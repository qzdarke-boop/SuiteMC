package com.psdk.clan;

import com.psdk.PSDK;
import org.bukkit.Bukkit;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ColorManager {

    private final PSDK plugin;
    private final Map<String, ClanColor> colors = new ConcurrentHashMap<>();

    public ColorManager(PSDK plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        colors.clear();
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM clan_colors")) {
                while (rs.next()) {
                    try {
                        ClanColor color = fromResultSet(rs);
                        colors.put(color.getName(), color);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Erro ao carregar cor de clan: " + rs.getString("name"), e);
                    }
                }
            }

            plugin.getLogger().info("[ClanColors] Carregadas " + colors.size() + " cor(es).");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao carregar cores de clan!", e);
        }
    }

    private ClanColor fromResultSet(ResultSet rs) throws SQLException {
        ClanColor color = new ClanColor();
        color.setName(rs.getString("name"));
        color.setColorHex(rs.getString("color_hex"));
        color.setDisplayName(rs.getString("display_name"));

        String loreStr = rs.getString("lore");
        if (loreStr != null && !loreStr.isEmpty()) {
            color.setLore(new ArrayList<>(List.of(loreStr.split("\n"))));
        }

        String matStr = rs.getString("key_material");
        color.setKeyMaterial(matStr != null ? org.bukkit.Material.matchMaterial(matStr) : org.bukkit.Material.TRIPWIRE_HOOK);
        
        color.setKeyDisplayName(rs.getString("key_display_name"));
        
        String keyLoreStr = rs.getString("key_lore");
        if (keyLoreStr != null && !keyLoreStr.isEmpty()) {
            color.setKeyLore(new ArrayList<>(List.of(keyLoreStr.split("\n"))));
        }

        color.setAnimationEnabled(rs.getInt("animation_enabled") == 1);
        color.setAnimationStyle(rs.getString("animation_style"));

        return color;
    }

    public void saveColor(ClanColor color) {
        Connection conn = null;
        try {
            conn = plugin.getDatabaseManager().getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT OR REPLACE INTO clan_colors
                    (name, color_hex, display_name, lore, key_material, key_display_name, key_lore, animation_enabled, animation_style)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""")) {
                ps.setString(1, color.getName());
                ps.setString(2, color.getColorHex());
                ps.setString(3, color.getDisplayName());
                ps.setString(4, color.getLore() != null ? String.join("\n", color.getLore()) : "");
                ps.setString(5, color.getKeyMaterial().name());
                ps.setString(6, color.getKeyDisplayName());
                ps.setString(7, color.getKeyLore() != null ? String.join("\n", color.getKeyLore()) : "");
                ps.setInt(8, color.isAnimationEnabled() ? 1 : 0);
                ps.setString(9, color.getAnimationStyle());

                ps.executeUpdate();
            }

            conn.commit();
            conn.setAutoCommit(true);
            colors.put(color.getName(), color);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao salvar cor de clan: " + color.getName(), e);
            if (conn != null) {
                try { conn.rollback(); conn.setAutoCommit(true); } catch (SQLException ignored) {}
            }
        }
    }

    public void deleteColor(String colorName) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM clan_player_colors WHERE color_name = ?")) {
                ps.setString(1, colorName);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM clan_color_items WHERE color_name = ?")) {
                ps.setString(1, colorName);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM clan_activated_colors WHERE color_name = ?")) {
                ps.setString(1, colorName);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM clan_colors WHERE name = ?")) {
                ps.setString(1, colorName);
                ps.executeUpdate();
            }
            colors.remove(colorName);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao deletar cor: " + colorName, e);
        }
    }

    public ClanColor getColor(String name) {
        return colors.get(name);
    }

    public Collection<ClanColor> getAllColors() {
        return colors.values();
    }

    /**
     * Remove uma cor da lista de cores ativadas de um clan.
     * Chamado quando o player que desbloqueou a cor sai do clan.
     */
    public void deactivateColorForClan(int clanId, String colorName) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "DELETE FROM clan_activated_colors WHERE clan_id = ? AND color_name = ?")) {
            ps.setInt(1, clanId);
            ps.setString(2, colorName);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao desativar cor do clan: " + colorName, e);
        }
    }

    public boolean activateColorForClan(int clanId, String colorName) {
        if (!colors.containsKey(colorName)) return false;

        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "INSERT OR REPLACE INTO clan_activated_colors (clan_id, color_name, activated_at) VALUES (?, ?, ?)")) {
            ps.setInt(1, clanId);
            ps.setString(2, colorName);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao ativar cor para clan", e);
            return false;
        }
    }

    public boolean hasColorActivated(int clanId, String colorName) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT 1 FROM clan_activated_colors WHERE clan_id = ? AND color_name = ?")) {
            ps.setInt(1, clanId);
            ps.setString(2, colorName);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao verificar cor ativada", e);
            return false;
        }
    }

    public List<String> getActivatedColors(int clanId) {
        List<String> activated = new ArrayList<>();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT color_name FROM clan_activated_colors WHERE clan_id = ?")) {
            ps.setInt(1, clanId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                activated.add(rs.getString("color_name"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao buscar cores ativadas", e);
        }
        return activated;
    }

    // ===== SISTEMA DE CORES DESBLOQUEADAS =====

    public void unlockColorForPlayer(java.util.UUID playerUUID, String colorName) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "INSERT OR REPLACE INTO clan_player_colors (player_uuid, color_name, unlocked) VALUES (?, ?, 1)")) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, colorName);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao desbloquear cor para jogador", e);
        }
    }

    public boolean hasColorUnlocked(java.util.UUID playerUUID, String colorName) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT 1 FROM clan_player_colors WHERE player_uuid = ? AND color_name = ? AND unlocked = 1")) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, colorName);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao verificar cor desbloqueada", e);
            return false;
        }
    }

    public List<String> getUnlockedColors(java.util.UUID playerUUID) {
        List<String> unlocked = new ArrayList<>();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT color_name FROM clan_player_colors WHERE player_uuid = ? AND unlocked = 1")) {
            ps.setString(1, playerUUID.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                unlocked.add(rs.getString("color_name"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao buscar cores desbloqueadas", e);
        }
        return unlocked;
    }

    public void lockColorForPlayer(java.util.UUID playerUUID, String colorName) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "UPDATE clan_player_colors SET unlocked = 0 WHERE player_uuid = ? AND color_name = ?")) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, colorName);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao bloquear cor para jogador", e);
        }
    }
}