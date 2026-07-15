package com.psdk.thepit;

import com.psdk.PSDK;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PlayerDataManager {

    private final PSDK plugin;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(PSDK plugin) {
        this.plugin = plugin;
        startAutoSaveTask();
    }

    private void startAutoSaveTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                List<PlayerData> dirty = new ArrayList<>();
                for (PlayerData data : cache.values()) {
                    if (data.isDirty()) dirty.add(data);
                }
                if (!dirty.isEmpty()) saveBatchSync(dirty);
            }
        }.runTaskTimerAsynchronously(plugin, 6000L, 6000L);
    }

    public void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        loadPlayerAsync(uuid, name).thenAccept(data -> {
            data.setName(name);
            // grava no cache na MAIN thread, e só se o jogador ainda estiver online
            // (evita ghost-entry/race se ele saiu durante o carregamento async).
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) cache.put(uuid, data);
            });
        });
    }

    public void unloadPlayer(Player player) {
        PlayerData data = cache.remove(player.getUniqueId());
        if (data != null && data.isDirty()) {
            savePlayerAsync(data);
        }
    }

    public void saveAllPlayers() {
        List<PlayerData> all = new ArrayList<>(cache.values());
        if (!all.isEmpty()) saveBatchSync(all);
    }

    public PlayerData getPlayerData(Player player) {
        return cache.get(player.getUniqueId());
    }

    public PlayerData getPlayerData(UUID uuid) {
        return cache.get(uuid);
    }

    public boolean isLoaded(Player player) {
        return cache.containsKey(player.getUniqueId());
    }

    /** Zera kills do jogador (online ou offline). */
    public void resetKills(UUID uuid) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) cached.setKills(0);
        new BukkitRunnable() {
            @Override public void run() {
                try (Connection conn = plugin.getDatabaseManager().newConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "UPDATE player_data SET kills=0, last_updated=? WHERE uuid=?")) {
                    ps.setLong(1, System.currentTimeMillis());
                    ps.setString(2, uuid.toString());
                    ps.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Erro ao resetar kills de " + uuid, e);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /** Zera todos os dados do jogador (online ou offline). */
    public void resetPlayerData(UUID uuid) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) {
            cached.setLevel(1);
            cached.setXp(0);
            cached.setKills(0);
            cached.setDeaths(0);
            cached.setBlocksPlaced(0);
            cached.setBlocksBroken(0);
        }
        new BukkitRunnable() {
            @Override public void run() {
                try (Connection conn = plugin.getDatabaseManager().newConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "UPDATE player_data SET level=1, xp=0, kills=0, deaths=0, " +
                             "blocks_placed=0, blocks_broken=0, last_updated=? WHERE uuid=?")) {
                    ps.setLong(1, System.currentTimeMillis());
                    ps.setString(2, uuid.toString());
                    ps.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Erro ao resetar dados de " + uuid, e);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    // --- Async/sync DB operations ---

    private CompletableFuture<PlayerData> loadPlayerAsync(UUID uuid, String name) {
        CompletableFuture<PlayerData> future = new CompletableFuture<>();
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    future.complete(loadPlayerSync(uuid, name));
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Erro ao carregar jogador: " + uuid, e);
                    future.complete(new PlayerData(uuid, name));
                }
            }
        }.runTaskAsynchronously(plugin);
        return future;
    }

    private PlayerData loadPlayerSync(UUID uuid, String name) {
        // Executado em thread assíncrona: conexão dedicada (não compartilha a principal).
        try (Connection conn = plugin.getDatabaseManager().newConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM player_data WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlayerData(uuid, rs.getString("name"),
                            rs.getInt("level"), rs.getLong("xp"),
                            rs.getInt("kills"), rs.getInt("deaths"),
                            rs.getLong("blocks_placed"), rs.getLong("blocks_broken"));
                }
            }
            PlayerData newData = new PlayerData(uuid, name);
            savePlayerWith(conn, newData);
            return newData;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao carregar jogador " + uuid, e);
            return new PlayerData(uuid, name);
        }
    }

    private void savePlayerAsync(PlayerData data) {
        new BukkitRunnable() {
            @Override
            public void run() { savePlayerSync(data); }
        }.runTaskAsynchronously(plugin);
    }

    private static final String UPSERT_SQL = """
            INSERT OR REPLACE INTO player_data (uuid, name, level, xp, kills, deaths, blocks_placed, blocks_broken, last_updated)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    private void savePlayerSync(PlayerData data) {
        // Conexão dedicada — pode rodar em thread assíncrona.
        try (Connection conn = plugin.getDatabaseManager().newConnection()) {
            savePlayerWith(conn, data);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao salvar jogador " + data.getUuid(), e);
        }
    }

    private void savePlayerWith(Connection conn, PlayerData data) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
            ps.setString(1, data.getUuid().toString());
            ps.setString(2, data.getName());
            ps.setInt(3, data.getLevel());
            ps.setLong(4, data.getXp());
            ps.setInt(5, data.getKills());
            ps.setInt(6, data.getDeaths());
            ps.setLong(7, data.getBlocksPlaced());
            ps.setLong(8, data.getBlocksBroken());
            ps.setLong(9, System.currentTimeMillis());
            ps.executeUpdate();
            data.markClean();
        }
    }

    private void saveBatchSync(List<PlayerData> players) {
        // Conexão dedicada: a transação (autoCommit=false) fica isolada da conexão
        // principal, evitando que um rollback aqui reverta escritas de outros sistemas.
        try (Connection conn = plugin.getDatabaseManager().newConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
                for (PlayerData data : players) {
                    ps.setString(1, data.getUuid().toString());
                    ps.setString(2, data.getName());
                    ps.setInt(3, data.getLevel());
                    ps.setLong(4, data.getXp());
                    ps.setInt(5, data.getKills());
                    ps.setInt(6, data.getDeaths());
                    ps.setLong(7, data.getBlocksPlaced());
                    ps.setLong(8, data.getBlocksBroken());
                    ps.setLong(9, System.currentTimeMillis());
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
                for (PlayerData data : players) data.markClean();
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                throw e;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao salvar batch de jogadores", e);
        }
    }
}
