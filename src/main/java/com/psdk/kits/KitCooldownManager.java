package com.psdk.kits;

import com.psdk.PSDK;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class KitCooldownManager {

    private static final String PREFIX_BASIC = "basic:";
    private static final String PREFIX_VIP   = "vip:";

    private final PSDK plugin;
    private final Map<UUID, Map<Kit, Long>> basicCooldowns = new HashMap<>();
    private final Map<UUID, Map<VipKit, Long>> vipCooldowns = new HashMap<>();

    public KitCooldownManager(PSDK plugin) {
        this.plugin = plugin;
        loadFromDatabase();
    }

    public boolean isOnCooldown(UUID playerId, Kit kit) {
        return isOnCooldown(basicCooldowns.get(playerId), kit, playerId, kitKey(kit));
    }

    public boolean isOnCooldown(UUID playerId, VipKit kit) {
        return isOnCooldown(vipCooldowns.get(playerId), kit, playerId, kitKey(kit));
    }

    private <K> boolean isOnCooldown(Map<K, Long> playerCooldowns, K kit, UUID playerId, String dbKey) {
        if (playerCooldowns == null) return false;
        Long expireAt = playerCooldowns.get(kit);
        if (expireAt == null) return false;
        if (System.currentTimeMillis() >= expireAt) {
            playerCooldowns.remove(kit);
            deleteCooldown(playerId, dbKey);
            return false;
        }
        return true;
    }

    public long getRemainingMs(UUID playerId, Kit kit) {
        return getRemainingMs(basicCooldowns.get(playerId), kit);
    }

    public long getRemainingMs(UUID playerId, VipKit kit) {
        return getRemainingMs(vipCooldowns.get(playerId), kit);
    }

    private <K> long getRemainingMs(Map<K, Long> playerCooldowns, K kit) {
        if (playerCooldowns == null) return 0;
        Long expireAt = playerCooldowns.get(kit);
        if (expireAt == null) return 0;
        return Math.max(0, expireAt - System.currentTimeMillis());
    }

    /**
     * Reset TOTAL dos cooldowns de kit (básicos + VIP) para TODOS os jogadores:
     * limpa o cache em memória E a tabela no banco. Usado pelo reset de lançamento
     * para que ninguém — nem VIP/premium — fique com cooldown após o reset, sem
     * depender de reiniciar o servidor.
     */
    public void clearAll() {
        basicCooldowns.clear();
        vipCooldowns.clear();
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM kit_cooldowns");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Falha ao limpar todos os cooldowns de kit.", e);
        }
    }

    public void setCooldown(UUID playerId, Kit kit) {
        long expireAt = System.currentTimeMillis() + kit.getCooldownMs();
        basicCooldowns.computeIfAbsent(playerId, k -> new HashMap<>()).put(kit, expireAt);
        saveCooldown(playerId, kitKey(kit), expireAt);
    }

    public void setCooldown(UUID playerId, VipKit kit) {
        long expireAt = System.currentTimeMillis() + kit.getCooldownMs();
        vipCooldowns.computeIfAbsent(playerId, k -> new HashMap<>()).put(kit, expireAt);
        saveCooldown(playerId, kitKey(kit), expireAt);
    }

    public String formatRemaining(UUID playerId, Kit kit) {
        return formatRemaining(getRemainingMs(playerId, kit));
    }

    public String formatRemaining(UUID playerId, VipKit kit) {
        return formatRemaining(getRemainingMs(playerId, kit));
    }

    private String formatRemaining(long millis) {
        if (millis <= 0) return null;

        long days = millis / 86_400_000;
        long hours = (millis % 86_400_000) / 3_600_000;
        long minutes = (millis % 3_600_000) / 60_000;
        long seconds = (millis % 60_000) / 1_000;

        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    private static String kitKey(Kit kit) {
        return PREFIX_BASIC + kit.configKey();
    }

    private static String kitKey(VipKit kit) {
        return PREFIX_VIP + kit.getConfigKey();
    }

    private void loadFromDatabase() {
        long now = System.currentTimeMillis();
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT player_uuid, kit_key, expire_at FROM kit_cooldowns WHERE expire_at > ?")) {
            ps.setLong(1, now);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID playerId = UUID.fromString(rs.getString("player_uuid"));
                    String key = rs.getString("kit_key");
                    long expireAt = rs.getLong("expire_at");
                    applyLoaded(playerId, key, expireAt);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Falha ao carregar cooldowns de kits.", e);
        }
        purgeExpired(now);
    }

    private void applyLoaded(UUID playerId, String key, long expireAt) {
        if (key.startsWith(PREFIX_BASIC)) {
            Kit kit = basicKitFromKey(key.substring(PREFIX_BASIC.length()));
            if (kit != null) {
                basicCooldowns.computeIfAbsent(playerId, k -> new HashMap<>()).put(kit, expireAt);
            }
        } else if (key.startsWith(PREFIX_VIP)) {
            VipKit kit = vipKitFromKey(key.substring(PREFIX_VIP.length()));
            if (kit != null) {
                vipCooldowns.computeIfAbsent(playerId, k -> new HashMap<>()).put(kit, expireAt);
            }
        }
    }

    private static Kit basicKitFromKey(String configKey) {
        for (Kit kit : Kit.values()) {
            if (kit.configKey().equals(configKey)) return kit;
        }
        return null;
    }

    private static VipKit vipKitFromKey(String configKey) {
        for (VipKit kit : VipKit.values()) {
            if (kit.getConfigKey().equals(configKey)) return kit;
        }
        return null;
    }

    private void saveCooldown(UUID playerId, String kitKey, long expireAt) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO kit_cooldowns (player_uuid, kit_key, expire_at) VALUES (?, ?, ?) "
                             + "ON CONFLICT(player_uuid, kit_key) DO UPDATE SET expire_at = excluded.expire_at")) {
            ps.setString(1, playerId.toString());
            ps.setString(2, kitKey);
            ps.setLong(3, expireAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Falha ao salvar cooldown de kit.", e);
        }
    }

    private void deleteCooldown(UUID playerId, String kitKey) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM kit_cooldowns WHERE player_uuid = ? AND kit_key = ?")) {
            ps.setString(1, playerId.toString());
            ps.setString(2, kitKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Falha ao remover cooldown de kit.", e);
        }
    }

    private void purgeExpired(long now) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM kit_cooldowns WHERE expire_at <= ?")) {
            ps.setLong(1, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Falha ao limpar cooldowns expirados.", e);
        }
    }
}
