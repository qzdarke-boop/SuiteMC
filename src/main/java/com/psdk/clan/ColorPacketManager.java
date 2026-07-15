package com.psdk.clan;

import com.psdk.PSDK;
import com.psdk.util.NexoUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

/**
 * Gerencia pacotinhos de cor customizáveis criados pelo admin.
 *
 * Cada pacotinho tem:
 *  - name: identificador único
 *  - display_name: MiniMessage
 *  - material: fallback Minecraft
 *  - nexo_id: item Nexo como visual (opcional)
 *  - color_names: lista de nomes de cores separados por vírgula
 *
 * PDC key: psdk_custom_packet → name do pacotinho
 */
public class ColorPacketManager {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final String PDC_KEY = "psdk_custom_packet";
    private static final Random RNG = new Random();

    private final PSDK plugin;

    public record ColorPacket(int id, String name, String displayName, String material,
                               String nexoId, List<String> colorNames) {}

    public ColorPacketManager(PSDK plugin) {
        this.plugin = plugin;
    }

    // ════════════════════════════════════════════════════════
    //  CRUD
    // ════════════════════════════════════════════════════════

    public boolean createPacket(String name, String displayName) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "INSERT INTO clan_color_packets (name, display_name) VALUES (?, ?)")) {
            ps.setString(1, name.toLowerCase());
            ps.setString(2, displayName);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao criar pacotinho: " + name, e);
            return false;
        }
    }

    public boolean deletePacket(String name) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "DELETE FROM clan_color_packets WHERE name = ?")) {
            ps.setString(1, name.toLowerCase());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao deletar pacotinho: " + name, e);
            return false;
        }
    }

    public ColorPacket getPacket(String name) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT * FROM clan_color_packets WHERE name = ?")) {
            ps.setString(1, name.toLowerCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return fromResultSet(rs);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao buscar pacotinho: " + name, e);
        }
        return null;
    }

    public List<ColorPacket> getAllPackets() {
        List<ColorPacket> list = new ArrayList<>();
        try (Statement stmt = plugin.getDatabaseManager().getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM clan_color_packets ORDER BY name")) {
            while (rs.next()) list.add(fromResultSet(rs));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao listar pacotinhos", e);
        }
        return list;
    }

    public boolean setNexoId(String name, String nexoId) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "UPDATE clan_color_packets SET nexo_id = ? WHERE name = ?")) {
            ps.setString(1, nexoId == null ? "" : nexoId);
            ps.setString(2, name.toLowerCase());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao atualizar nexo_id do pacotinho: " + name, e);
            return false;
        }
    }

    public boolean setMaterial(String name, String material) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "UPDATE clan_color_packets SET material = ? WHERE name = ?")) {
            ps.setString(1, material.toUpperCase());
            ps.setString(2, name.toLowerCase());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao atualizar material do pacotinho: " + name, e);
            return false;
        }
    }

    /** Adiciona uma cor ao pacotinho. */
    public boolean addColor(String packetName, String colorName) {
        ColorPacket packet = getPacket(packetName);
        if (packet == null) return false;

        List<String> colors = new ArrayList<>(packet.colorNames());
        if (colors.contains(colorName.toLowerCase())) return false; // já tem

        colors.add(colorName.toLowerCase());
        return updateColorNames(packetName, colors);
    }

    /** Remove uma cor do pacotinho. */
    public boolean removeColor(String packetName, String colorName) {
        ColorPacket packet = getPacket(packetName);
        if (packet == null) return false;

        List<String> colors = new ArrayList<>(packet.colorNames());
        boolean removed = colors.remove(colorName.toLowerCase());
        if (!removed) return false;
        return updateColorNames(packetName, colors);
    }

    private boolean updateColorNames(String packetName, List<String> colors) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "UPDATE clan_color_packets SET color_names = ? WHERE name = ?")) {
            ps.setString(1, String.join(",", colors));
            ps.setString(2, packetName.toLowerCase());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao atualizar cores do pacotinho: " + packetName, e);
            return false;
        }
    }

    // ════════════════════════════════════════════════════════
    //  ITEM
    // ════════════════════════════════════════════════════════

    /** Cria o ItemStack físico do pacotinho para dar ao player. */
    public ItemStack createItem(ColorPacket packet, int amount) {
        // Item personalizado do Nexo (modelo/textura) — fallback para material vanilla.
        ItemStack item = NexoUtil.buildItem(packet.nexoId());
        if (item == null) {
            Material mat = Material.matchMaterial(packet.material());
            if (mat == null) mat = Material.FIREWORK_ROCKET;
            item = new ItemStack(mat, Math.max(1, Math.min(64, amount)));
        } else {
            item.setAmount(Math.max(1, Math.min(64, amount)));
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(mm.deserialize("<!italic>" + packet.displayName()));

        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<!italic><gray>Pacotinho personalizado de cor."));
        lore.add(Component.empty());
        lore.add(mm.deserialize("<!italic><#848c94>Cores disponíveis: <white>" + packet.colorNames().size()));
        meta.lore(lore);

        meta.getPersistentDataContainer()
            .set(new NamespacedKey(plugin, PDC_KEY), PersistentDataType.STRING, packet.name());

        item.setItemMeta(meta);
        return item;
    }

    /** Lê o nome do pacotinho do item. Retorna null se não for pacotinho customizado. */
    public String getPacketNameFromItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer()
                .get(new NamespacedKey(plugin, PDC_KEY), PersistentDataType.STRING);
    }

    /**
     * Sorteia uma cor do pacotinho que o player ainda não possui.
     * Se já tem todas, retorna uma aleatória do pool assim mesmo.
     */
    public ClanCommand.ClanColor rollColor(ColorPacket packet, Player player) {
        List<String> colorNames = packet.colorNames();
        if (colorNames.isEmpty()) return null;

        List<ClanCommand.ClanColor> pool = new ArrayList<>();
        for (String name : colorNames) {
            ClanCommand.ClanColor color = ClanColorKeyManager.findCommandColor(name);
            if (color != null) pool.add(color);
        }
        if (pool.isEmpty()) return null;

        List<ClanCommand.ClanColor> notOwned = pool.stream()
                .filter(c -> !ClanCommand.hasColorPermission(player, c))
                .toList();

        List<ClanCommand.ClanColor> candidates = notOwned.isEmpty() ? pool : notOwned;
        return candidates.get(RNG.nextInt(candidates.size()));
    }

    // ════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════

    private ColorPacket fromResultSet(ResultSet rs) throws SQLException {
        String raw = rs.getString("color_names");
        List<String> colors = (raw == null || raw.isBlank())
                ? new ArrayList<>()
                : new ArrayList<>(List.of(raw.split(",")));
        return new ColorPacket(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("display_name"),
                rs.getString("material"),
                rs.getString("nexo_id"),
                colors
        );
    }

}
