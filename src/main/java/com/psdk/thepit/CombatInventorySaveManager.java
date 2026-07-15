package com.psdk.thepit;

import com.psdk.PSDK;
import com.psdk.util.ItemSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Salva inventário no SQLite por sessão (uma por boot/crash).
 * Várias sessões podem coexistir; no login restaura o backup mais recente do jogador
 * e remove a linha. Sessões vazias são apagadas automaticamente.
 */
public class CombatInventorySaveManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String SETTING_CLEAN_SHUTDOWN = "combat_inv_clean_shutdown";
    private static final int STORAGE_SLOTS = 36;
    private static final int ARMOR_SLOTS = 4;
    private static final int TOTAL_SLOTS = STORAGE_SLOTS + ARMOR_SLOTS + 1;
    private static final char SLOT_SEP = '\u001E';
    /** Backups mais antigos que isso são ignorados no restore e apagados no boot. */
    private static final long BACKUP_TTL_MS = 48L * 60 * 60 * 1000;
    private static final long PERIODIC_INTERVAL_TICKS = 300L;
    private static final int PERIODIC_BATCH_SIZE = 5;

    private final PSDK plugin;
    private volatile boolean restoreMode;
    private volatile String activeSessionId;
    /** Incrementado no quit para cancelar saves async pendentes (evita race). */
    private final ConcurrentHashMap<UUID, Long> saveGeneration = new ConcurrentHashMap<>();

    public CombatInventorySaveManager(PSDK plugin) {
        this.plugin = plugin;
        migrateLegacyTableIfNeeded();
        purgeExpiredBackups();
        initShutdownFlag();
        startPeriodicSaveTask();
    }

    private void migrateLegacyTableIfNeeded() {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (!tableExists(conn, "player_combat_inventory_backups")) return;
            if (isSessionBasedSchema(conn)) return;

            plugin.getLogger().info("[CombatSave] Migrando tabela para o formato por sessão...");

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS combat_restore_sessions (
                            session_id   TEXT PRIMARY KEY NOT NULL,
                            created_at   INTEGER NOT NULL DEFAULT 0,
                            reason       TEXT NOT NULL DEFAULT 'legacy'
                        )""");
            }

            String legacySession = "legacy-" + UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO combat_restore_sessions (session_id, created_at, reason) VALUES (?, ?, 'legacy')")) {
                ps.setString(1, legacySession);
                ps.setLong(2, System.currentTimeMillis());
                ps.executeUpdate();
            }

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("""
                        CREATE TABLE player_combat_inventory_backups_new (
                            session_id      TEXT NOT NULL,
                            player_uuid     TEXT NOT NULL,
                            name            TEXT NOT NULL,
                            inventory_data  TEXT NOT NULL,
                            saved_at        INTEGER NOT NULL DEFAULT 0,
                            in_combat       INTEGER NOT NULL DEFAULT 1,
                            PRIMARY KEY (session_id, player_uuid)
                        )""");
            }

            // Suporta formato legado (uuid), híbrido (uuid + session_id) ou parcialmente migrado.
            boolean hasUuid = columnExists(conn, "player_combat_inventory_backups", "uuid");
            boolean hasPlayerUuid = columnExists(conn, "player_combat_inventory_backups", "player_uuid");
            boolean hasSessionId = columnExists(conn, "player_combat_inventory_backups", "session_id");

            String playerExpr = hasPlayerUuid && hasUuid
                    ? "COALESCE(NULLIF(player_uuid, ''), uuid)"
                    : hasPlayerUuid ? "player_uuid" : "uuid";
            String sessionExpr = hasSessionId ? "COALESCE(NULLIF(session_id, ''), ?)" : "?";

            String insertSql = """
                    INSERT INTO player_combat_inventory_backups_new
                        (session_id, player_uuid, name, inventory_data, saved_at, in_combat)
                    SELECT %s, %s, name, inventory_data, saved_at, in_combat
                    FROM player_combat_inventory_backups
                    WHERE %s IS NOT NULL AND %s != ''
                    """.formatted(sessionExpr, playerExpr, playerExpr, playerExpr);

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, legacySession);
                ps.executeUpdate();
            }

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE player_combat_inventory_backups");
                stmt.execute("ALTER TABLE player_combat_inventory_backups_new RENAME TO player_combat_inventory_backups");
            }

            plugin.getLogger().info("[CombatSave] Migração concluída — sessão legada: " + legacySession);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[CombatSave] Falha na migração da tabela legada", e);
        }
    }

    /** Formato correto: session_id + player_uuid, sem coluna legada uuid. */
    private static boolean isSessionBasedSchema(Connection conn) throws SQLException {
        return columnExists(conn, "player_combat_inventory_backups", "session_id")
                && columnExists(conn, "player_combat_inventory_backups", "player_uuid")
                && !columnExists(conn, "player_combat_inventory_backups", "uuid");
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean columnExists(Connection conn, String table, String column) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("PRAGMA table_info(" + table + ")")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (column.equalsIgnoreCase(rs.getString("name"))) return true;
                }
            }
        }
        return false;
    }

    private void initShutdownFlag() {
        boolean lastShutdownClean = readCleanShutdownFlag();
        restoreMode = !lastShutdownClean;
        writeCleanShutdownFlag(false);

        String reason = lastShutdownClean ? "uptime" : "crash";
        activeSessionId = createSession(reason);

        if (!lastShutdownClean) {
            int pending = countPendingBackupsExcludingSession(activeSessionId);
            int players = countDistinctPlayersWithPendingBackups(activeSessionId);
            plugin.getLogger().warning(
                    "[CombatSave] Reinício abrupto detectado. Nova sessão: " + activeSessionId
                            + (pending > 0
                            ? " — " + pending + " backup(s) de " + players + " jogador(es) pendente(s) para restore."
                            : " — nenhum backup pendente encontrado."));
        } else {
            plugin.getLogger().info("[CombatSave] Sessão ativa: " + activeSessionId);
        }
    }

    public static boolean hasItems(Player player) {
        if (player == null) return false;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && !item.getType().isAir()) return true;
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && !item.getType().isAir()) return true;
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        return offhand != null && !offhand.getType().isAir();
    }

    public boolean hasPendingBackup(UUID uuid) {
        return findPendingBackup(uuid).isPresent();
    }

    public Optional<BackupInfo> findPendingBackup(UUID uuid) {
        if (uuid == null || activeSessionId == null) return Optional.empty();
        long minSavedAt = System.currentTimeMillis() - BACKUP_TTL_MS;
        try (Connection conn = plugin.getDatabaseManager().newConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT b.session_id, b.name, b.saved_at, b.in_combat FROM player_combat_inventory_backups b "
                             + "INNER JOIN combat_restore_sessions s ON s.session_id = b.session_id "
                             + "WHERE b.player_uuid = ? AND b.session_id != ? AND b.saved_at >= ? "
                             + "ORDER BY b.saved_at DESC LIMIT 1")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, activeSessionId);
            ps.setLong(3, minSavedAt);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new BackupInfo(
                            rs.getString("session_id"),
                            rs.getString("name"),
                            rs.getLong("saved_at"),
                            rs.getInt("in_combat") == 1));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[CombatSave] Erro ao consultar backup de " + uuid, e);
        }
        return Optional.empty();
    }

    public record BackupInfo(String sessionId, String playerName, long savedAt, boolean inCombat) {}

    public boolean isRestoreMode() {
        return restoreMode;
    }

    public String getActiveSessionId() {
        return activeSessionId;
    }

    private void startPeriodicSaveTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
                if (!online.isEmpty()) {
                    saveOnlineBatchSync(online, 0);
                }
            }
        }.runTaskTimer(plugin, PERIODIC_INTERVAL_TICKS, PERIODIC_INTERVAL_TICKS);
    }

    private void saveOnlineBatchSync(List<Player> players, int startIndex) {
        int end = Math.min(startIndex + PERIODIC_BATCH_SIZE, players.size());
        for (int i = startIndex; i < end; i++) {
            Player player = players.get(i);
            if (player.isOnline()) {
                saveSync(player);
            }
        }
        if (end < players.size()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    saveOnlineBatchSync(players, end);
                }
            }.runTaskLater(plugin, 1L);
        }
    }

    public boolean needsEmergencySave(Player player) {
        if (player == null) return false;
        if (plugin.getCombatManager().isInCombat(player)) return true;
        ArenaManager arena = plugin.getArenaManager();
        return arena != null && arena.isInsideArena(player.getLocation());
    }

    public void scheduleSave(Player player) {
        if (player == null || !player.isOnline() || activeSessionId == null) return;
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        String sessionId = activeSessionId;
        long generation = saveGeneration.getOrDefault(uuid, 0L);
        String data = captureInventoryData(player);
        boolean inCombat = plugin.getCombatManager().isInCombat(player);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (saveGeneration.getOrDefault(uuid, 0L) != generation) return;
                saveSnapshot(sessionId, uuid, name, data, inCombat);
            }
        }.runTaskAsynchronously(plugin);
    }

    public void saveSync(Player player) {
        if (player == null || activeSessionId == null) return;
        saveSnapshot(
                activeSessionId,
                player.getUniqueId(),
                player.getName(),
                captureInventoryData(player),
                plugin.getCombatManager().isInCombat(player));
    }

    public void saveAllEmergency() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            saveSync(player);
        }
    }

    public void markCleanShutdown() {
        writeCleanShutdownFlag(true);
    }

    /**
     * Restaura backup de sessão anterior (crash/boot passado), nunca da sessão ativa deste boot.
     * Deve rodar na main thread, antes do kit padrão.
     */
    public boolean tryRestore(Player player) {
        if (player == null || activeSessionId == null) return false;

        PendingBackup pending = loadLatestRestorableSnapshot(player.getUniqueId());
        if (pending == null) return false;

        applyInventoryData(player, pending.inventoryData());
        deleteSnapshotRow(pending.sessionId(), player.getUniqueId());
        cleanupEmptySession(pending.sessionId());

        player.sendMessage(MM.deserialize(
                "<#10fc46><bold>Inventário recuperado</bold> <gray>— seus itens foram restaurados após o reinício do servidor."));
        plugin.getLogger().info("[CombatSave] Inventário restaurado para " + player.getName()
                + " (sessão " + pending.sessionId() + ")");
        return true;
    }

    /** Invalida saves async pendentes e remove todos os backups do jogador (síncrono — uso no quit). */
    public void clearAllSnapshotsForPlayerSync(UUID uuid) {
        invalidatePendingSaves(uuid);
        Set<String> sessions = findSessionsForPlayer(uuid);
        deleteAllSnapshotsForPlayer(uuid);
        for (String sessionId : sessions) {
            cleanupEmptySession(sessionId);
        }
        saveGeneration.remove(uuid);
    }

    private void invalidatePendingSaves(UUID uuid) {
        saveGeneration.merge(uuid, 1L, Long::sum);
    }

    /** Remove todos os backups do jogador em todas as sessões (morte — async ok). */
    public void clearAllSnapshotsForPlayer(UUID uuid) {
        invalidatePendingSaves(uuid);
        runAsync(() -> {
            Set<String> sessions = findSessionsForPlayer(uuid);
            deleteAllSnapshotsForPlayer(uuid);
            for (String sessionId : sessions) {
                cleanupEmptySession(sessionId);
            }
            saveGeneration.remove(uuid);
        });
    }

    private void runAsync(Runnable task) {
        new BukkitRunnable() {
            @Override
            public void run() {
                task.run();
            }
        }.runTaskAsynchronously(plugin);
    }

    private String createSession(String reason) {
        String sessionId = UUID.randomUUID().toString();
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO combat_restore_sessions (session_id, created_at, reason) VALUES (?, ?, ?)")) {
            ps.setString(1, sessionId);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, reason);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[CombatSave] Erro ao criar sessão de restore", e);
        }
        return sessionId;
    }

    private void saveSnapshot(String sessionId, UUID uuid, String name, String data, boolean inCombat) {
        try (Connection conn = plugin.getDatabaseManager().newConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO player_combat_inventory_backups "
                             + "(session_id, player_uuid, name, inventory_data, saved_at, in_combat) "
                             + "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, sessionId);
            ps.setString(2, uuid.toString());
            ps.setString(3, name);
            ps.setString(4, data);
            ps.setLong(5, System.currentTimeMillis());
            ps.setInt(6, inCombat ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[CombatSave] Erro ao salvar backup de " + name, e);
        }
    }

    private PendingBackup loadLatestRestorableSnapshot(UUID uuid) {
        long minSavedAt = System.currentTimeMillis() - BACKUP_TTL_MS;
        try (Connection conn = plugin.getDatabaseManager().newConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT b.session_id, b.inventory_data FROM player_combat_inventory_backups b "
                             + "INNER JOIN combat_restore_sessions s ON s.session_id = b.session_id "
                             + "WHERE b.player_uuid = ? AND b.session_id != ? AND b.saved_at >= ? "
                             + "ORDER BY b.saved_at DESC LIMIT 1")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, activeSessionId);
            ps.setLong(3, minSavedAt);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PendingBackup(rs.getString("session_id"), rs.getString("inventory_data"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[CombatSave] Erro ao carregar backup de " + uuid, e);
        }
        return null;
    }

    private void purgeExpiredBackups() {
        long cutoff = System.currentTimeMillis() - BACKUP_TTL_MS;
        try (Connection conn = plugin.getDatabaseManager().newConnection()) {
            Set<String> affected = new HashSet<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT DISTINCT session_id FROM player_combat_inventory_backups WHERE saved_at < ?")) {
                ps.setLong(1, cutoff);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) affected.add(rs.getString("session_id"));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM player_combat_inventory_backups WHERE saved_at < ?")) {
                ps.setLong(1, cutoff);
                int removed = ps.executeUpdate();
                if (removed > 0) {
                    plugin.getLogger().info("[CombatSave] " + removed + " backup(s) expirado(s) removido(s).");
                }
            }
            for (String sessionId : affected) {
                cleanupEmptySession(sessionId);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[CombatSave] Erro ao purgar backups expirados", e);
        }
    }

    private void deleteSnapshotRow(String sessionId, UUID uuid) {
        try (Connection conn = plugin.getDatabaseManager().newConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM player_combat_inventory_backups WHERE session_id = ? AND player_uuid = ?")) {
            ps.setString(1, sessionId);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[CombatSave] Erro ao limpar backup de " + uuid, e);
        }
    }

    private void deleteAllSnapshotsForPlayer(UUID uuid) {
        try (Connection conn = plugin.getDatabaseManager().newConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM player_combat_inventory_backups WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[CombatSave] Erro ao limpar backups de " + uuid, e);
        }
    }

    private Set<String> findSessionsForPlayer(UUID uuid) {
        Set<String> sessions = new HashSet<>();
        try (Connection conn = plugin.getDatabaseManager().newConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT DISTINCT session_id FROM player_combat_inventory_backups WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sessions.add(rs.getString("session_id"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[CombatSave] Erro ao listar sessões de " + uuid, e);
        }
        return sessions;
    }

    private void cleanupEmptySession(String sessionId) {
        if (sessionId == null) return;
        try (Connection conn = plugin.getDatabaseManager().newConnection()) {
            int count;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM player_combat_inventory_backups WHERE session_id = ?")) {
                ps.setString(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    count = rs.next() ? rs.getInt(1) : 0;
                }
            }
            if (count == 0) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM combat_restore_sessions WHERE session_id = ?")) {
                    ps.setString(1, sessionId);
                    ps.executeUpdate();
                }
                plugin.getLogger().info("[CombatSave] Sessão esvaziada e removida: " + sessionId);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[CombatSave] Erro ao limpar sessão " + sessionId, e);
        }
    }

    private int countPendingBackupsExcludingSession(String excludeSessionId) {
        try (Connection conn = plugin.getDatabaseManager().newConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM player_combat_inventory_backups WHERE session_id != ?")) {
            ps.setString(1, excludeSessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    private int countDistinctPlayersWithPendingBackups(String excludeSessionId) {
        try (Connection conn = plugin.getDatabaseManager().newConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(DISTINCT player_uuid) FROM player_combat_inventory_backups WHERE session_id != ?")) {
            ps.setString(1, excludeSessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    private String captureInventoryData(Player player) {
        ItemStack[] storage = player.getInventory().getStorageContents();
        ItemStack[] armor = player.getInventory().getArmorContents();
        ItemStack offhand = player.getInventory().getItemInOffHand();

        StringBuilder sb = new StringBuilder(TOTAL_SLOTS * 32);
        appendSlots(sb, storage, STORAGE_SLOTS);
        appendSlots(sb, armor, ARMOR_SLOTS);
        if (sb.length() > 0) sb.append(SLOT_SEP);
        appendSlot(sb, offhand);
        return sb.toString();
    }

    private void appendSlots(StringBuilder sb, ItemStack[] items, int count) {
        for (int i = 0; i < count; i++) {
            if (i > 0 || sb.length() > 0) sb.append(SLOT_SEP);
            appendSlot(sb, items != null && i < items.length ? items[i] : null);
        }
    }

    private void appendSlot(StringBuilder sb, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        String encoded = ItemSerializer.toBase64(item);
        if (encoded != null) sb.append(encoded);
    }

    private void applyInventoryData(Player player, String data) {
        String[] parts = data.split(String.valueOf(SLOT_SEP), -1);
        ItemStack[] storage = new ItemStack[STORAGE_SLOTS];
        ItemStack[] armor = new ItemStack[ARMOR_SLOTS];
        ItemStack offhand = null;

        for (int i = 0; i < TOTAL_SLOTS; i++) {
            ItemStack item = (i < parts.length && !parts[i].isEmpty())
                    ? ItemSerializer.fromBase64(parts[i])
                    : null;
            if (i < STORAGE_SLOTS) {
                storage[i] = item;
            } else if (i < STORAGE_SLOTS + ARMOR_SLOTS) {
                armor[i - STORAGE_SLOTS] = item;
            } else {
                offhand = item;
            }
        }

        player.getInventory().clear();
        player.getInventory().setStorageContents(storage);
        player.getInventory().setArmorContents(armor);
        if (offhand != null) {
            player.getInventory().setItemInOffHand(offhand);
        }
    }

    private boolean readCleanShutdownFlag() {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT value FROM settings WHERE key = ?")) {
            ps.setString(1, SETTING_CLEAN_SHUTDOWN);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return "1".equals(rs.getString("value"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[CombatSave] Erro ao ler flag de shutdown", e);
        }
        return true;
    }

    private void writeCleanShutdownFlag(boolean clean) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)")) {
            ps.setString(1, SETTING_CLEAN_SHUTDOWN);
            ps.setString(2, clean ? "1" : "0");
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[CombatSave] Erro ao gravar flag de shutdown", e);
        }
    }

    private record PendingBackup(String sessionId, String inventoryData) {}
}
