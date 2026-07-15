package com.psdk.database;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SchemaManager {

    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile(
            "CREATE\\s+TABLE\\s+IF\\s+NOT\\s+EXISTS\\s+(\\w+)\\s*\\(",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern COLUMN_DEF_PATTERN = Pattern.compile(
            "^\\s*(\\w+)\\s+(\\w+)(.*?)$"
    );

    private final Logger logger;

    public SchemaManager(Logger logger) {
        this.logger = logger;
    }

    public void migrate(Connection conn, InputStream schemaSql, String pluginVersion) throws SQLException {
        ensureMetaTable(conn);

        String currentVersion = getMetaValue(conn, "schema_version");
        if (pluginVersion.equals(currentVersion)) {
            logger.info("Schema já está na versão " + pluginVersion + ", nenhuma migração necessária.");
            return;
        }

        logger.info("Migrando schema: " + (currentVersion == null ? "novo" : currentVersion) + " -> " + pluginVersion);

        String schemaSqlContent = readStream(schemaSql);
        Map<String, String> desiredTables = parseCreateStatements(schemaSqlContent);
        Map<String, List<ColumnDef>> desiredColumns = parseColumns(desiredTables);

        Set<String> existingTables = getExistingTables(conn);

        for (Map.Entry<String, String> entry : desiredTables.entrySet()) {
            String tableName = entry.getKey();
            String createStmt = entry.getValue();

            if (!existingTables.contains(tableName)) {
                logger.info("Criando tabela: " + tableName);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(createStmt);
                }
            } else {
                migrateTable(conn, tableName, desiredColumns.get(tableName));
            }
        }

        setMetaValue(conn, "schema_version", pluginVersion);
        logger.info("Schema migrado para versão " + pluginVersion + " com sucesso.");
    }

    private void migrateTable(Connection conn, String tableName, List<ColumnDef> desiredCols) throws SQLException {
        if (desiredCols == null || desiredCols.isEmpty()) return;

        Map<String, ColumnInfo> existingCols = getTableColumns(conn, tableName);

        for (ColumnDef desired : desiredCols) {
            if (!existingCols.containsKey(desired.name.toLowerCase())) {
                String alterSql = buildAlterAddColumn(tableName, desired);
                logger.info("Adicionando coluna: " + tableName + "." + desired.name);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(alterSql);
                }
            } else {
                ColumnInfo existing = existingCols.get(desired.name.toLowerCase());
                if (!existing.type.equalsIgnoreCase(desired.type)) {
                    logger.warning("Coluna " + tableName + "." + desired.name +
                            " tem tipo diferente (banco: " + existing.type + ", schema: " + desired.type +
                            "). Migração de tipo não suportada pelo SQLite.");
                }
            }
        }

        for (String existingCol : existingCols.keySet()) {
            boolean found = desiredCols.stream()
                    .anyMatch(d -> d.name.equalsIgnoreCase(existingCol));
            if (!found && !existingCol.equalsIgnoreCase("rowid")) {
                logger.warning("Coluna " + tableName + "." + existingCol +
                        " existe no banco mas não no schema. Dados preservados.");
            }
        }
    }

    private String buildAlterAddColumn(String tableName, ColumnDef col) {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ").append(tableName).append(" ADD COLUMN ").append(col.name).append(" ").append(col.type);
        if (col.notNull) {
            sb.append(" NOT NULL");
        }
        if (col.defaultValue != null) {
            sb.append(" DEFAULT ").append(col.defaultValue);
        }
        return sb.toString();
    }

    private void ensureMetaTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS _meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        }
    }

    private String getMetaValue(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT value FROM _meta WHERE key = ?")) {
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("value");
            }
        }
        return null;
    }

    private void setMetaValue(Connection conn, String key, String value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO _meta (key, value) VALUES (?, ?)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    private Set<String> getExistingTables(Connection conn) throws SQLException {
        Set<String> tables = new HashSet<>();
        try (ResultSet rs = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME").toLowerCase());
            }
        }
        return tables;
    }

    private Map<String, ColumnInfo> getTableColumns(Connection conn, String tableName) throws SQLException {
        Map<String, ColumnInfo> columns = new LinkedHashMap<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                String name = rs.getString("name");
                String type = rs.getString("type");
                boolean notNull = rs.getInt("notnull") == 1;
                String dfltValue = rs.getString("dflt_value");
                columns.put(name.toLowerCase(), new ColumnInfo(name, type, notNull, dfltValue));
            }
        }
        return columns;
    }

    private Map<String, String> parseCreateStatements(String sql) {
        Map<String, String> tables = new LinkedHashMap<>();
        String[] statements = sql.split(";");

        for (String raw : statements) {
            String stmt = raw.trim();
            if (stmt.isEmpty()) continue;

            Matcher m = CREATE_TABLE_PATTERN.matcher(stmt);
            if (m.find()) {
                String tableName = m.group(1).toLowerCase();
                tables.put(tableName, stmt);
            }
        }
        return tables;
    }

    private Map<String, List<ColumnDef>> parseColumns(Map<String, String> createStatements) {
        Map<String, List<ColumnDef>> result = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : createStatements.entrySet()) {
            String tableName = entry.getKey();
            String createStmt = entry.getValue();
            result.put(tableName, extractColumns(createStmt));
        }
        return result;
    }

    private List<ColumnDef> extractColumns(String createStmt) {
        List<ColumnDef> columns = new ArrayList<>();

        int openParen = createStmt.indexOf('(');
        int closeParen = createStmt.lastIndexOf(')');
        if (openParen < 0 || closeParen < 0) return columns;

        String body = createStmt.substring(openParen + 1, closeParen).trim();
        List<String> parts = splitColumnDefs(body);

        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;

            String upper = trimmed.toUpperCase();
            if (upper.startsWith("PRIMARY KEY") || upper.startsWith("FOREIGN KEY") ||
                    upper.startsWith("UNIQUE") || upper.startsWith("CHECK") ||
                    upper.startsWith("CONSTRAINT")) {
                continue;
            }

            Matcher m = COLUMN_DEF_PATTERN.matcher(trimmed);
            if (m.matches()) {
                String colName = m.group(1);
                String colType = m.group(2);
                String rest = m.group(3).trim();

                boolean notNull = rest.toUpperCase().contains("NOT NULL");
                String defaultValue = extractDefault(rest);

                columns.add(new ColumnDef(colName, colType, notNull, defaultValue));
            }
        }
        return columns;
    }

    private List<String> splitColumnDefs(String body) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();

        for (char c : body.toCharArray()) {
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private String extractDefault(String rest) {
        Pattern defaultPattern = Pattern.compile("DEFAULT\\s+(.+?)(?:\\s+NOT\\s+NULL|\\s+PRIMARY|\\s+UNIQUE|\\s+CHECK|\\s+REFERENCES|$)",
                Pattern.CASE_INSENSITIVE);
        Matcher m = defaultPattern.matcher(rest);
        if (m.find()) {
            return m.group(1).trim().replaceAll(",\\s*$", "");
        }
        return null;
    }

    private String readStream(InputStream is) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("--")) continue;
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Erro ao ler schema.sql", e);
            return "";
        }
    }

    private record ColumnDef(String name, String type, boolean notNull, String defaultValue) {}

    private record ColumnInfo(String name, String type, boolean notNull, String defaultValue) {}
}
