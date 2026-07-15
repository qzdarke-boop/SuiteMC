package com.psdk.thepit.topboard;

import com.psdk.PSDK;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Rastreia estatísticas semanais/mensais e tempo online total para os tops.
 */
public class TopStatsTracker {

    private static final String SETTING_WEEK_KEY = "top_stats_week_key";
    private static final String SETTING_MONTH_KEY = "top_stats_month_key";

    private final PSDK plugin;
    private final Map<UUID, PlayerStats> cache = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final Object flushLock = new Object();

    public TopStatsTracker(PSDK plugin) {
        this.plugin = plugin;
        startFlushTask();
        startResetTask();
    }

    public void addKill(UUID uuid, String name) {
        if (uuid == null) return;
        PlayerStats stats = ensure(uuid, name);
        stats.weeklyKills++;
        stats.monthlyKills++;
        markDirty(uuid);
    }

    public void addCoins(UUID uuid, String name, double amount) {
        if (uuid == null || !Double.isFinite(amount) || amount <= 0) return;
        PlayerStats stats = ensure(uuid, name);
        stats.weeklyCoins += amount;
        stats.monthlyCoins += amount;
        markDirty(uuid);
    }

    public void addPlaytime(UUID uuid, String name, long ms) {
        if (uuid == null || ms <= 0) return;
        PlayerStats stats = ensure(uuid, name);
        stats.totalPlaytimeMs += ms;
        stats.weeklyPlaytimeMs += ms;
        stats.monthlyPlaytimeMs += ms;
        markDirty(uuid);
    }

    public void flushAll() {
        flushDirty();
    }

    private PlayerStats ensure(UUID uuid, String name) {
        return cache.computeIfAbsent(uuid, id -> {
            PlayerStats loaded = loadFromDb(id);
            if (name != null && !name.isBlank()) loaded.name = name;
            return loaded;
        });
    }

    private void markDirty(UUID uuid) {
        dirty.add(uuid);
    }

    private void startFlushTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                flushDirty();
            }
        }.runTaskTimerAsynchronously(plugin, 60L, 60L);
    }

    private void startResetTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                checkResets();
            }
        }.runTaskTimerAsynchronously(plugin, 1200L, 1200L);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::checkResets);
    }

    private void checkResets() {
        String weekKey = currentWeekKey();
        String monthKey = currentMonthKey();
        String storedWeek = getSetting(SETTING_WEEK_KEY);
        String storedMonth = getSetting(SETTING_MONTH_KEY);

        if (storedWeek == null || storedWeek.isBlank()) {
            setSetting(SETTING_WEEK_KEY, weekKey);
        } else if (!weekKey.equals(storedWeek)) {
            resetWeekly();
            setSetting(SETTING_WEEK_KEY, weekKey);
        }

        if (storedMonth == null || storedMonth.isBlank()) {
            setSetting(SETTING_MONTH_KEY, monthKey);
        } else if (!monthKey.equals(storedMonth)) {
            resetMonthly();
            setSetting(SETTING_MONTH_KEY, monthKey);
        }
    }

    private String currentWeekKey() {
        LocalDate now = LocalDate.now();
        int week = now.get(WeekFields.ISO.weekOfWeekBasedYear());
        return now.getYear() + "-W" + week;
    }

    private String currentMonthKey() {
        LocalDate now = LocalDate.now();
        return now.getYear() + "-" + String.format("%02d", now.getMonthValue());
    }

    private String getSetting(String key) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection()
                .prepareStatement("SELECT value FROM settings WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("value");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao ler setting " + key, e);
        }
        return null;
    }

    private void setSetting(String key, String value) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao salvar setting " + key, e);
        }
    }

    private void resetWeekly() {
        cache.values().forEach(s -> {
            s.weeklyKills = 0;
            s.weeklyCoins = 0;
            s.weeklyPlaytimeMs = 0;
        });
        try (Connection conn = plugin.getDatabaseManager().newConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE player_period_stats SET weekly_kills = 0, weekly_coins = 0, weekly_playtime_ms = 0")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao resetar stats semanais", e);
        }
        plugin.getLogger().info("Top stats semanais resetados.");
    }

    private void resetMonthly() {
        cache.values().forEach(s -> {
            s.monthlyKills = 0;
            s.monthlyCoins = 0;
            s.monthlyPlaytimeMs = 0;
        });
        try (Connection conn = plugin.getDatabaseManager().newConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE player_period_stats SET monthly_kills = 0, monthly_coins = 0, monthly_playtime_ms = 0")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao resetar stats mensais", e);
        }
        plugin.getLogger().info("Top stats mensais resetados.");
    }

    private PlayerStats loadFromDb(UUID uuid) {
        PlayerStats stats = new PlayerStats();
        try (Connection conn = plugin.getDatabaseManager().newConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT name, weekly_kills, monthly_kills, weekly_coins, monthly_coins, weekly_playtime_ms, monthly_playtime_ms "
                            + "FROM player_period_stats WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        stats.name = rs.getString("name");
                        stats.weeklyKills = rs.getInt("weekly_kills");
                        stats.monthlyKills = rs.getInt("monthly_kills");
                        stats.weeklyCoins = rs.getDouble("weekly_coins");
                        stats.monthlyCoins = rs.getDouble("monthly_coins");
                        stats.weeklyPlaytimeMs = rs.getLong("weekly_playtime_ms");
                        stats.monthlyPlaytimeMs = rs.getLong("monthly_playtime_ms");
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT name, total_playtime_ms FROM player_data WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        if (stats.name == null || stats.name.isBlank()) stats.name = rs.getString("name");
                        stats.totalPlaytimeMs = rs.getLong("total_playtime_ms");
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao carregar period stats", e);
        }
        return stats;
    }

    private void flushDirty() {
        if (dirty.isEmpty()) return;
        synchronized (flushLock) {
            List<UUID> batch = new ArrayList<>(dirty);
            dirty.removeAll(batch);
            try (Connection conn = plugin.getDatabaseManager().newConnection()) {
                conn.setAutoCommit(false);
                String periodSql = """
                        INSERT INTO player_period_stats (uuid, name, weekly_kills, monthly_kills, weekly_coins, monthly_coins, weekly_playtime_ms, monthly_playtime_ms)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(uuid) DO UPDATE SET
                            name = excluded.name,
                            weekly_kills = excluded.weekly_kills,
                            monthly_kills = excluded.monthly_kills,
                            weekly_coins = excluded.weekly_coins,
                            monthly_coins = excluded.monthly_coins,
                            weekly_playtime_ms = excluded.weekly_playtime_ms,
                            monthly_playtime_ms = excluded.monthly_playtime_ms""";
                String playtimeSql = """
                        INSERT INTO player_data (uuid, name, total_playtime_ms, kills, deaths, level, xp, blocks_placed, blocks_broken, last_updated)
                        VALUES (?, ?, ?, 0, 0, 1, 0, 0, 0, 0)
                        ON CONFLICT(uuid) DO UPDATE SET
                            total_playtime_ms = excluded.total_playtime_ms,
                            name = COALESCE(excluded.name, player_data.name)""";
                try (PreparedStatement periodPs = conn.prepareStatement(periodSql);
                     PreparedStatement playtimePs = conn.prepareStatement(playtimeSql)) {
                    for (UUID id : batch) {
                        PlayerStats s = cache.get(id);
                        if (s == null) continue;
                        periodPs.setString(1, id.toString());
                        periodPs.setString(2, s.name != null ? s.name : "Desconhecido");
                        periodPs.setInt(3, s.weeklyKills);
                        periodPs.setInt(4, s.monthlyKills);
                        periodPs.setDouble(5, s.weeklyCoins);
                        periodPs.setDouble(6, s.monthlyCoins);
                        periodPs.setLong(7, s.weeklyPlaytimeMs);
                        periodPs.setLong(8, s.monthlyPlaytimeMs);
                        periodPs.addBatch();

                        playtimePs.setString(1, id.toString());
                        playtimePs.setString(2, s.name != null ? s.name : "Desconhecido");
                        playtimePs.setLong(3, s.totalPlaytimeMs);
                        playtimePs.addBatch();
                    }
                    periodPs.executeBatch();
                    playtimePs.executeBatch();
                }
                conn.commit();
            } catch (SQLException e) {
                dirty.addAll(batch);
                plugin.getLogger().log(Level.WARNING, "Erro ao gravar period stats", e);
            }
        }
    }

    /**
     * Zera apenas os stats semanais/mensais do top. Kills, coins e horas globais não são alterados.
     */
    public void resetAllTopStats(Runnable onComplete) {
        synchronized (flushLock) {
            dirty.clear();
            for (PlayerStats stats : cache.values()) {
                stats.weeklyKills = 0;
                stats.monthlyKills = 0;
                stats.weeklyCoins = 0;
                stats.monthlyCoins = 0;
                stats.weeklyPlaytimeMs = 0;
                stats.monthlyPlaytimeMs = 0;
            }
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().newConnection();
                 var stmt = conn.createStatement()) {
                stmt.executeUpdate("""
                        UPDATE player_period_stats SET
                            weekly_kills = 0, monthly_kills = 0,
                            weekly_coins = 0, monthly_coins = 0,
                            weekly_playtime_ms = 0, monthly_playtime_ms = 0""");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Erro ao resetar stats do top", e);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getTopQueryService().refreshAll();
                plugin.getTopBoardManager().refreshAllPlayerHolograms();
                if (onComplete != null) onComplete.run();
            });
        });
    }

    private static final class PlayerStats {
        String name = "Desconhecido";
        int weeklyKills;
        int monthlyKills;
        double weeklyCoins;
        double monthlyCoins;
        long weeklyPlaytimeMs;
        long monthlyPlaytimeMs;
        long totalPlaytimeMs;
    }
}
