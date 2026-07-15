package com.psdk.clan;

import com.psdk.PSDK;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Rankings de clans (mesmo padrão do TopQueryService): queries async em conexão
 * dedicada, cache volatile com swap atômico. TTL de 60s — clans mudam menos que
 * players.
 */
public class ClanTopQueryService {

    public static final int LIMIT = 10;

    public enum ClanTopType { MEMBERS, KILLS, TREASURY }

    public record ClanTopEntry(int clanId, String tag, String name, String color, double value) {}

    private final PSDK plugin;
    private volatile Map<ClanTopType, List<ClanTopEntry>> cache = Map.of();
    /** Kills agregadas por clan (todos os clans, não só o top 10) — usado por /clan info e PAPI. */
    private volatile Map<Integer, Long> killsByClan = Map.of();
    /** Tesouro por clan — leitura via placeholder sem tocar no banco. */
    private volatile Map<Integer, Double> treasuryByClan = Map.of();
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private volatile long lastRefreshMs;

    public ClanTopQueryService(PSDK plugin) {
        this.plugin = plugin;
        new BukkitRunnable() {
            @Override
            public void run() {
                refreshAll();
            }
        }.runTaskTimerAsynchronously(plugin, 40L, 1200L); // 60s
    }

    public List<ClanTopEntry> getTop(ClanTopType type) {
        if (System.currentTimeMillis() - lastRefreshMs > 90_000L && plugin.isEnabled()) {
            if (Bukkit.isPrimaryThread()) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, this::refreshAll);
            } else {
                refreshAll();
            }
        }
        return cache.getOrDefault(type, List.of());
    }

    /** Kills agregadas do clan (do cache — nunca toca no banco). */
    public long getKills(int clanId) {
        return killsByClan.getOrDefault(clanId, 0L);
    }

    /** Tesouro do clan (do cache — nunca toca no banco). */
    public double getTreasury(int clanId) {
        return treasuryByClan.getOrDefault(clanId, 0d);
    }

    public void refreshAll() {
        if (!refreshing.compareAndSet(false, true)) return;
        try {
            Map<ClanTopType, List<ClanTopEntry>> next = new EnumMap<>(ClanTopType.class);
            Map<Integer, Long> nextKills = new java.util.HashMap<>();
            Map<Integer, Double> nextTreasury = new java.util.HashMap<>();
            try (Connection conn = plugin.getDatabaseManager().newConnection()) {
                for (ClanTopType type : ClanTopType.values()) {
                    next.put(type, query(conn, type));
                }
                try (PreparedStatement ps = conn.prepareStatement("""
                        SELECT m.clan_id, COALESCE(SUM(pd.kills), 0) AS v
                        FROM clan_members m LEFT JOIN player_data pd ON pd.uuid = m.player_uuid
                        GROUP BY m.clan_id""");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) nextKills.put(rs.getInt("clan_id"), rs.getLong("v"));
                }
                try (PreparedStatement ps = conn.prepareStatement("SELECT clan_id, coins FROM clan_treasury");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) nextTreasury.put(rs.getInt("clan_id"), rs.getDouble("coins"));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Erro ao atualizar tops de clans", e);
                return;
            }
            cache = next;
            killsByClan = nextKills;
            treasuryByClan = nextTreasury;
            lastRefreshMs = System.currentTimeMillis();
        } finally {
            refreshing.set(false);
        }
    }

    private List<ClanTopEntry> query(Connection conn, ClanTopType type) throws SQLException {
        String sql = switch (type) {
            case MEMBERS -> """
                    SELECT c.id, c.tag, c.name, c.color, COUNT(m.player_uuid) AS v
                    FROM clans c LEFT JOIN clan_members m ON m.clan_id = c.id
                    GROUP BY c.id ORDER BY v DESC LIMIT ?""";
            case KILLS -> """
                    SELECT c.id, c.tag, c.name, c.color, COALESCE(SUM(pd.kills), 0) AS v
                    FROM clans c
                    JOIN clan_members m ON m.clan_id = c.id
                    LEFT JOIN player_data pd ON pd.uuid = m.player_uuid
                    GROUP BY c.id ORDER BY v DESC LIMIT ?""";
            case TREASURY -> """
                    SELECT c.id, c.tag, c.name, c.color, COALESCE(t.coins, 0) AS v
                    FROM clans c LEFT JOIN clan_treasury t ON t.clan_id = c.id
                    ORDER BY v DESC LIMIT ?""";
        };
        List<ClanTopEntry> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, LIMIT);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ClanTopEntry(
                            rs.getInt("id"),
                            rs.getString("tag"),
                            rs.getString("name"),
                            rs.getString("color"),
                            rs.getDouble("v")));
                }
            }
        }
        return list;
    }
}
