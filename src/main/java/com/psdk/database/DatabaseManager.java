package com.psdk.database;

import com.psdk.PSDK;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.logging.Level;

public class DatabaseManager {

    private final PSDK plugin;
    private Connection connection;       // conexão real (thread principal)
    private Connection sharedView;       // wrapper não-fechável devolvido por getConnection()
    private File databaseFile;

    public DatabaseManager(PSDK plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        try {
            databaseFile = new File(plugin.getDataFolder(), "psdk.db");
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA cache_size=2000");
                stmt.execute("PRAGMA foreign_keys=ON");
                // Espera (em vez de falhar com "database is locked") quando uma conexão
                // assíncrona dedicada está com o write-lock do WAL.
                stmt.execute("PRAGMA busy_timeout=10000");
            }

            runSchemaMigration();
            plugin.getLogger().info("Banco de dados SQLite inicializado.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao inicializar banco de dados!", e);
            return false;
        }
    }

    private void runSchemaMigration() throws SQLException {
        InputStream schemaStream = plugin.getResource("schema.sql");
        if (schemaStream == null) {
            plugin.getLogger().severe("schema.sql não encontrado nos resources do plugin!");
            return;
        }

        String version = plugin.getDescription().getVersion();
        SchemaManager schemaManager = new SchemaManager(plugin.getLogger());
        schemaManager.migrate(connection, schemaStream, version);
    }

    /**
     * Conexão compartilhada — destinada EXCLUSIVAMENTE à thread principal.
     * O SQLite/JDBC não é thread-safe ao compartilhar uma mesma Connection entre
     * threads; trabalho assíncrono deve usar {@link #newConnection()}.
     */
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys=ON");
                stmt.execute("PRAGMA busy_timeout=10000");
            }
            sharedView = null; // conexão recriada -> reconstruir o wrapper
        }
        if (sharedView == null) sharedView = uncloseable(connection);
        return sharedView;
    }

    /**
     * Envolve a conexão compartilhada num proxy cujo {@code close()} é NO-OP. Vários
     * managers usam {@code try (Connection c = getConnection())}; sem isto, o
     * try-with-resources FECHARIA a conexão compartilhada — e se outro código estivesse
     * iterando um ResultSet nela, dava "stmt pointer is closed". A conexão real só é
     * fechada em {@link #close()} (onDisable).
     */
    private static Connection uncloseable(Connection real) {
        return (Connection) Proxy.newProxyInstance(
                DatabaseManager.class.getClassLoader(),
                new Class[]{Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) return null; // no-op
                    if ("isClosed".equals(method.getName())) return real.isClosed();
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    /**
     * Abre uma nova conexão dedicada para uso fora da thread principal.
     * O chamador é responsável por fechá-la (use try-with-resources).
     * Cada thread assíncrona deve ter a sua própria conexão — compartilhar a
     * conexão principal causava corrupção de transações (ex.: um rollback de um
     * save assíncrono revertia escritas de economia da thread principal).
     */
    public Connection newConnection() throws SQLException {
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        try (Statement stmt = c.createStatement()) {
            stmt.execute("PRAGMA foreign_keys=ON");
            stmt.execute("PRAGMA busy_timeout=10000");
            stmt.execute("PRAGMA synchronous=NORMAL");
        }
        return c;
    }

    /**
     * Cria um backup consistente do banco em {@code <pasta>/backups/}, mantendo
     * apenas os mais recentes. Usa {@code VACUUM INTO} (seguro com WAL e com o
     * servidor rodando) e uma conexão dedicada, então pode rodar fora da thread
     * principal. Qualquer falha é apenas logada — nunca interrompe o servidor.
     */
    public void backupDatabase(int keep) {
        if (databaseFile == null || !databaseFile.exists()) return;
        try {
            File backupDir = new File(plugin.getDataFolder(), "backups");
            if (!backupDir.exists()) backupDir.mkdirs();

            String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
                    .format(new java.util.Date());
            File target = new File(backupDir, "psdk-" + stamp + ".db");
            if (target.exists()) return; // já existe um backup neste segundo

            String safePath = target.getAbsolutePath().replace("'", "''");
            try (Connection c = newConnection();
                 Statement stmt = c.createStatement()) {
                stmt.execute("VACUUM INTO '" + safePath + "'");
            }
            plugin.getLogger().info("Backup do banco criado: backups/" + target.getName());

            rotateBackups(backupDir, keep);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Falha ao criar backup do banco (ignorado).", e);
        }
    }

    /** Mantém apenas os {@code keep} backups mais recentes, apagando os antigos. */
    private void rotateBackups(File dir, int keep) {
        File[] files = dir.listFiles((d, n) -> n.startsWith("psdk-") && n.endsWith(".db"));
        if (files == null || files.length <= keep) return;
        java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < files.length - keep; i++) {
            if (!files[i].delete()) {
                plugin.getLogger().warning("Não foi possível apagar backup antigo: " + files[i].getName());
            }
        }
    }

    public void close() {
        sharedView = null;
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao fechar banco de dados", e);
        }
    }
}
