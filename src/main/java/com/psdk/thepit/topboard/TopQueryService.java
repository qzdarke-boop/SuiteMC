package com.psdk.thepit.topboard;

import com.psdk.PSDK;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;

/**
 * Consultas unificadas de ranking com cache de 15 segundos (até 100 entradas por tipo/período).
 */
public class TopQueryService {

    public static final int MAX_RANK = 100;
    public static final int PAGE_SIZE = 10;

    private final PSDK plugin;
    /** Swap atômico: leituras nunca veem estado parcial e não precisam de lock. */
    private volatile Map<String, List<TopEntry>> cache = Map.of();
    private final java.util.concurrent.atomic.AtomicBoolean refreshing =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private volatile long lastRefreshMs;

    public TopQueryService(PSDK plugin) {
        this.plugin = plugin;
        startRefreshTask();
    }

    public void startRefreshTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                refreshAll();
            }
        }.runTaskTimerAsynchronously(plugin, 20L, 300L);
        new BukkitRunnable() {
            @Override
            public void run() {
                refreshAll();
            }
        }.runTaskAsynchronously(plugin);
    }

    public List<TopEntry> getPage(TopBoardType type, TopPeriod period, int page) {
        ensureFresh();
        List<TopEntry> all = cache.getOrDefault(cacheKey(type, period), List.of());
        int start = Math.max(0, page) * PAGE_SIZE;
        if (start >= all.size()) return List.of();
        int end = Math.min(all.size(), start + PAGE_SIZE);
        return all.subList(start, end);
    }

    public TopEntry getEntry(TopBoardType type, TopPeriod period, int rank) {
        if (rank < 1 || rank > MAX_RANK) return null;
        ensureFresh();
        List<TopEntry> all = cache.getOrDefault(cacheKey(type, period), List.of());
        int index = rank - 1;
        if (index >= all.size()) return null;
        return all.get(index);
    }

    private void ensureFresh() {
        if (System.currentTimeMillis() - lastRefreshMs > 20_000L && plugin.isEnabled()) {
            // Nunca roda queries na main thread — GUI lê o cache atual e agenda refresh.
            if (org.bukkit.Bukkit.isPrimaryThread()) {
                org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, this::refreshAll);
            } else {
                refreshAll();
            }
        }
    }

    public void refreshAll() {
        if (!refreshing.compareAndSet(false, true)) return;
        try {
            Map<String, List<TopEntry>> next = new HashMap<>();
            for (TopBoardType type : TopBoardType.values()) {
                for (TopPeriod period : TopPeriod.values()) {
                    if (period != TopPeriod.GLOBAL && !type.supportsPeriods()) continue;
                    next.put(cacheKey(type, period), queryTop(type, period));
                }
            }
            cache = next;
            lastRefreshMs = System.currentTimeMillis();
        } finally {
            refreshing.set(false);
        }
    }

    private String cacheKey(TopBoardType type, TopPeriod period) {
        return period.getId() + "_" + type.getId();
    }

    private List<TopEntry> queryTop(TopBoardType type, TopPeriod period) {
        String sql = buildSql(type, period);
        if (sql == null) return List.of();

        List<TopEntry> list = new ArrayList<>();
        try (Connection conn = plugin.getDatabaseManager().newConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, MAX_RANK);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    list.add(new TopEntry(parseUuid(rs.getString("uuid")),
                            name != null ? name : "Desconhecido", rs.getDouble("v"),
                            blankIfNull(rs.getString("clan_tag")),
                            blankIfNull(rs.getString("clan_color"))));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao consultar top " + type + "/" + period, e);
        }
        return list;
    }

    private String buildSql(TopBoardType type, TopPeriod period) {
        if (period == TopPeriod.GLOBAL) {
            return switch (type) {
                case KILLS -> buildTopSql("player_data", "kills");
                // Top Coins = coins CONQUISTADOS por gameplay (não o saldo movido por /pay, /eco).
                case COINS -> buildTopSql("player_economy", "coins_earned");
                case HOURS -> buildTopSql("player_data", "total_playtime_ms");
                case DEATHS -> buildTopSql("player_data", "deaths");
                case LEVEL -> buildTopSql("player_data", "level");
                case TOKENS -> buildTopSql("player_economy", "tokens");
                case BLOCKS_BROKEN -> buildTopSql("player_data", "blocks_broken");
                case BLOCKS_PLACED -> buildTopSql("player_data", "blocks_placed");
            };
        }

        // Só KILLS/COINS/HORAS têm rastreio semanal/mensal.
        if (!type.supportsPeriods()) return null;

        String column = switch (type) {
            case KILLS -> period == TopPeriod.WEEKLY ? "weekly_kills" : "monthly_kills";
            case COINS -> period == TopPeriod.WEEKLY ? "weekly_coins" : "monthly_coins";
            case HOURS -> period == TopPeriod.WEEKLY ? "weekly_playtime_ms" : "monthly_playtime_ms";
            default -> null;
        };
        if (column == null) return null;
        return buildTopSql("player_period_stats", column);
    }

    private String buildTopSql(String table, String column) {
        return "SELECT t.uuid, t.name, t." + column + " AS v, " +
                "(SELECT c.tag FROM clan_members cm JOIN clans c ON c.id = cm.clan_id " +
                "WHERE cm.player_uuid = t.uuid LIMIT 1) AS clan_tag, " +
                "(SELECT c.color FROM clan_members cm JOIN clans c ON c.id = cm.clan_id " +
                "WHERE cm.player_uuid = t.uuid LIMIT 1) AS clan_color " +
                "FROM " + table + " t ORDER BY t." + column + " DESC LIMIT ?";
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String blankIfNull(String value) {
        return value != null ? value : "";
    }
}
