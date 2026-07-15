package com.psdk.clan;

import com.psdk.PSDK;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ClanColorKeyManager {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final Random RNG = new Random();

    private final PSDK plugin;

    private static final String PDC_COLOR_NAME  = "psdk_color_name";
    private static final String PDC_COLOR_PERM  = "psdk_color_perm";
    private static final String PDC_PACKET_TYPE = "psdk_color_packet";

    // ════════════════════════════════════════════════════════
    //  ✏️  EDITE AQUI — nomes, cores, hover e nexoId de cada pacotinho
    // ════════════════════════════════════════════════════════
    public enum PacketType {

        SOLIDA(
            // — Cor do título (MiniMessage)
            "<#10fc46>",
            // — Nome exibido no item
            "Pacotinho Colorido",
            // — Linhas do hover/lore (use MiniMessage; cada String = uma linha)
            new String[]{
                "<!italic><#848c94>Ultilize esse pacote para",
                "<!italic><#848c94>obter cores de clan<#10fc46>",
            },
            // — Material vanilla de fallback
            "PAPER",
            // — nexoId do Nexo (null = usa só o material vanilla)
            "pacote_de_cores"
        ),

        GRADIENTE(
            "<#10fc46>",
            "Pacotinho Colorido",
            new String[]{
                "<!italic><#848c94>Ultilize esse pacote para",
                "<!italic><#848c94>obter cores de clan<#10fc46>",
            },
            "PAPER",
            "pacote_de_cores"
        ),

        ANIMADA(
            "<#10fc46>",
            "Pacotinho Colorido",
            new String[]{
                "<!italic><#848c94>Ultilize esse pacote para",
                "<!italic><#848c94>obter cores de clan<#10fc46>",
            },
            "PAPER",
            "pacote_de_cores"
        ),

        QUALQUER(
            "<#e7332d>",
            "Pacotinho Aleatório",
            new String[]{
                "<!italic><#848c94>Ultilize esse pacote para",
                "<!italic><#848c94>obter uma cor de clan aleatória!<#e7332d>",
            },
            "PAPER",
            "pacote_aleatorio"
        );

        // ════════════════════════════════════════════════════════
        //  Não mexa abaixo desta linha
        // ════════════════════════════════════════════════════════

        public final String colorTag;
        public final String displayName;
        public final String[] hoverLines;
        public final String material;
        public final String nexoId;

        /** Label legível usado em mensagens de sistema (ex.: "Sólida"). */
        public String label() {
            return switch (this) {
                case SOLIDA    -> "Sólida";
                case GRADIENTE -> "Gradiente";
                case ANIMADA   -> "Animada";
                case QUALQUER  -> "Aleatório";
            };
        }

        PacketType(String colorTag, String displayName, String[] hoverLines, String material, String nexoId) {
            this.colorTag     = colorTag;
            this.displayName  = displayName;
            this.hoverLines   = hoverLines;
            this.material     = material;
            this.nexoId       = nexoId;
        }
    }

    public ClanColorKeyManager(PSDK plugin) {
        this.plugin = plugin;
    }

    // ════════════════════════════════════════════════════════
    //  PACOTINHO ALEATÓRIO
    // ════════════════════════════════════════════════════════

    public ItemStack createPacketItem(PacketType type, int amount) {
        int qty = Math.max(1, Math.min(amount, 64));

        // Tenta criar via Nexo (item personalizado); se falhar usa material vanilla
        ItemStack nexoItem = com.psdk.util.NexoUtil.buildItem(type.nexoId);
        ItemStack item;
        if (nexoItem != null) {
            // Clona o item do Nexo para não modificar o original em cache
            item = nexoItem.clone();
        } else {
            Material mat = Material.matchMaterial(type.material);
            if (mat == null) mat = Material.PAPER;
            item = new ItemStack(mat);
        }
        item.setAmount(qty);

        // Pega o meta que JÁ veio do Nexo (contém custom_model_data e textura)
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        boolean isGradient = type == PacketType.QUALQUER;
        String titleClose  = isGradient ? "" : "";

        // Sobrescreve só display e lore — custom_model_data permanece intacto no meta
        meta.displayName(mm.deserialize("<!italic>" + type.colorTag + type.displayName + titleClose));

        List<Component> lore = new ArrayList<>();
        for (String line : type.hoverLines) {
            lore.add(mm.deserialize(line));
        }
        meta.lore(lore);

        meta.getPersistentDataContainer()
            .set(new NamespacedKey(plugin, PDC_PACKET_TYPE), PersistentDataType.STRING, type.name());

        item.setItemMeta(meta);
        return item;
    }

    public PacketType getPacketTypeFromItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String raw = meta.getPersistentDataContainer()
                .get(new NamespacedKey(plugin, PDC_PACKET_TYPE), PersistentDataType.STRING);
        if (raw == null) return null;
        try { return PacketType.valueOf(raw); } catch (IllegalArgumentException e) { return null; }
    }

    public ClanCommand.ClanColor rollColor(PacketType type, org.bukkit.entity.Player player) {
        List<ClanCommand.ClanColor> pool = buildPool(type);
        if (pool.isEmpty()) return null;

        List<ClanCommand.ClanColor> notOwned = new ArrayList<>();
        for (ClanCommand.ClanColor c : pool) {
            if (!ClanCommand.hasColorPermission(player, c)) notOwned.add(c);
        }

        List<ClanCommand.ClanColor> candidates = notOwned.isEmpty() ? pool : notOwned;
        return candidates.get(RNG.nextInt(candidates.size()));
    }

    private List<ClanCommand.ClanColor> buildPool(PacketType type) {
        return switch (type) {
            case SOLIDA    -> ClanCommand.CLAN_COLORS;
            case GRADIENTE -> ClanCommand.CLAN_GRADIENTS;
            case ANIMADA   -> ClanCommand.CLAN_ANIMATED;
            case QUALQUER  -> ClanCommand.getAllColors();
        };
    }

    // ════════════════════════════════════════════════════════
    //  CHAVE ESPECÍFICA — ClanCommand.ClanColor (lista estática)
    // ════════════════════════════════════════════════════════

    public ItemStack createKeyItem(ClanCommand.ClanColor color, int amount) {
        Material mat = Material.matchMaterial(color.material());
        if (mat == null) mat = Material.TRIPWIRE_HOOK;

        ItemStack item = new ItemStack(mat, Math.max(1, Math.min(amount, 64)));
        ItemMeta meta  = item.getItemMeta();
        if (meta == null) return item;

        String titleTag = color.isGradient()
                ? "<!italic>" + color.hex() + color.name().toUpperCase() + "</gradient>"
                : "<!italic><" + color.hex() + ">" + color.name().toUpperCase();
        meta.displayName(mm.deserialize(titleTag));

        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<!italic><#848c94>Ultilize esse pacote para \n<!italic><#848c94>obter uma cor de clan aleatória! <!italic><" +
                (color.isGradient()
                        ? color.hex() + color.name() + ""
                        : color.hex() + ">" + color.name())));
        meta.lore(lore);

        meta.getPersistentDataContainer()
            .set(new NamespacedKey(plugin, PDC_COLOR_NAME), PersistentDataType.STRING, color.name());
        meta.getPersistentDataContainer()
            .set(new NamespacedKey(plugin, PDC_COLOR_PERM), PersistentDataType.STRING, color.permission());

        item.setItemMeta(meta);
        return item;
    }

    // ════════════════════════════════════════════════════════
    //  CHAVE ESPECÍFICA — ClanColor (banco de dados)
    // ════════════════════════════════════════════════════════

    public ItemStack createKeyItem(ClanColor color, int amount) {
        ClanCommand.ClanColor found = findCommandColor(color.getName());
        if (found != null) return createKeyItem(found, amount);

        Material mat = color.getKeyMaterial() != null ? color.getKeyMaterial() : Material.TRIPWIRE_HOOK;
        ItemStack item = new ItemStack(mat, Math.max(1, Math.min(amount, 64)));
        ItemMeta meta  = item.getItemMeta();
        if (meta == null) return item;

        String displayName = color.getDisplayName() != null ? color.getDisplayName() : color.getName();
        meta.displayName(mm.deserialize("<!italic><#FF8C00>" + displayName.toUpperCase()));

        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<!italic><#848c94>Ultilize esse pacote para \n<!italic><#848c94>obter uma cor de clan aleatória! <!italic><#FF8C00>" + displayName.toUpperCase()));
        meta.lore(lore);

        String perm = derivePermission(color.getName());
        meta.getPersistentDataContainer()
            .set(new NamespacedKey(plugin, PDC_COLOR_NAME), PersistentDataType.STRING, color.getName());
        meta.getPersistentDataContainer()
            .set(new NamespacedKey(plugin, PDC_COLOR_PERM), PersistentDataType.STRING, perm);

        item.setItemMeta(meta);
        return item;
    }

    private static String derivePermission(String colorName) {
        if (colorName == null) return "psdk.clan.cor.desconhecida";
        return "psdk.clan.cor." + colorName.toLowerCase().replace(" ", "_").replace("-", "_");
    }

    // ════════════════════════════════════════════════════════
    //  LEITURA DE PDC
    // ════════════════════════════════════════════════════════

    public String getColorNameFromKey(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer()
                .get(new NamespacedKey(plugin, PDC_COLOR_NAME), PersistentDataType.STRING);
    }

    public String getPermissionFromKey(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer()
                .get(new NamespacedKey(plugin, PDC_COLOR_PERM), PersistentDataType.STRING);
    }

    // ════════════════════════════════════════════════════════
    //  UTILITÁRIO ESTÁTICO
    // ════════════════════════════════════════════════════════

    public static ClanCommand.ClanColor findCommandColor(String name) {
        if (name == null) return null;
        for (ClanCommand.ClanColor c : ClanCommand.CLAN_COLORS)    if (c.name().equalsIgnoreCase(name)) return c;
        for (ClanCommand.ClanColor c : ClanCommand.CLAN_GRADIENTS) if (c.name().equalsIgnoreCase(name)) return c;
        for (ClanCommand.ClanColor c : ClanCommand.CLAN_ANIMATED)  if (c.name().equalsIgnoreCase(name)) return c;
        return null;
    }
}