package com.psdk.kits;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Menu de Kits VIP (54 slots) — 4 ranks. */
public class KitsVipGUI {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public static final String TITLE     = "<#a4a4a4>Kits VIP";
    public static final int    SLOT_BACK = 49;

    // Coluna Premium (rosa) — botão 1D, vidro 7D, concreto 30D
    public static final int SLOT_PREMIUM_1D  = 10;
    public static final int SLOT_PREMIUM_7D  = 19;
    public static final int SLOT_PREMIUM_30D = 28;

    // Coluna Elite (verde)
    public static final int SLOT_ELITE_1D  = 12;
    public static final int SLOT_ELITE_7D  = 21;
    public static final int SLOT_ELITE_30D = 30;

    // Coluna Eternal (azul)
    public static final int SLOT_ETERNAL_1D  = 14;
    public static final int SLOT_ETERNAL_7D  = 23;
    public static final int SLOT_ETERNAL_30D = 32;

    // Coluna Suite (vermelha)
    public static final int SLOT_SUITE_1D  = 16;
    public static final int SLOT_SUITE_7D  = 25;
    public static final int SLOT_SUITE_30D = 34;

    // ─────────────────────────────────────────────────────────────────────────
    //  Layout: colunas 1, 3, 5, 7 (equidistantes em uma row de 9)
    //  Kit 1 rosa  → col 1 (10 / 19 / 28)
    //  Kit 2 verde → col 3 (12 / 21 / 30)
    //  Kit 3 azul  → col 5 (14 / 23 / 32)
    //  Kit 4 suite → col 7 (16 / 25 / 34)
    // ─────────────────────────────────────────────────────────────────────────

    private static final int[] SLOTS_BTN   = { 10, 12, 14, 16 };
    private static final int[] SLOTS_GLASS = { 19, 21, 23, 25 };
    private static final int[] SLOTS_CONC  = { 28, 30, 32, 34 };

    private static final String[] ICONS = {
        "<font:nexo:default>ꐓ</font>",
        "<font:nexo:default>ꐢ</font>",
        "<font:nexo:default>ꐡ</font>",
        "<font:nexo:default>♦</font>"
    };

    private static final Material[] MAT_BTN = {
        Material.PINK_CONCRETE, Material.LIME_CONCRETE,
        Material.BLUE_CONCRETE, Material.RED_CONCRETE
    };
    private static final Material[] MAT_GLASS = {
        Material.PINK_STAINED_GLASS, Material.LIME_STAINED_GLASS,
        Material.BLUE_STAINED_GLASS, Material.RED_STAINED_GLASS
    };
    private static final Material[] MAT_CONC = {
        Material.PINK_CONCRETE, Material.LIME_CONCRETE,
        Material.BLUE_CONCRETE, Material.RED_CONCRETE
    };

    private static final VipKit[] PREMIUM_KITS = {
        VipKit.PREMIUM_1D, VipKit.PREMIUM_7D, VipKit.PREMIUM_30D
    };
    private static final VipKit[] ELITE_KITS = {
        VipKit.ELITE_1D, VipKit.ELITE_7D, VipKit.ELITE_30D
    };
    private static final VipKit[] ETERNAL_KITS = {
        VipKit.ETERNAL_1D, VipKit.ETERNAL_7D, VipKit.ETERNAL_30D
    };
    private static final VipKit[] SUITE_KITS = {
        VipKit.SUITE_1D, VipKit.SUITE_7D, VipKit.SUITE_30D
    };

    public static Inventory build(UUID playerId, KitCooldownManager cooldowns) {
        KitsInventoryHolder holder = KitsInventoryHolder.vip();
        Inventory inv = Bukkit.createInventory(holder, 54, MM.deserialize(TITLE));
        holder.bind(inv);

        setColumn(inv, 0, PREMIUM_KITS, playerId, cooldowns);
        setColumn(inv, 1, ELITE_KITS, playerId, cooldowns);
        setColumn(inv, 2, ETERNAL_KITS, playerId, cooldowns);
        setColumn(inv, 3, SUITE_KITS, playerId, cooldowns);

        inv.setItem(SLOT_BACK, backButton());
        return inv;
    }

    private static void setColumn(Inventory inv, int col, VipKit[] kits,
                                  UUID playerId, KitCooldownManager cooldowns) {
        Player player = Bukkit.getPlayer(playerId);
        inv.setItem(SLOTS_BTN[col],   vipKitItem(MAT_BTN[col],   ICONS[col], kits[0], 1,  player, cooldowns));
        inv.setItem(SLOTS_GLASS[col], vipKitItem(MAT_GLASS[col], ICONS[col], kits[1], 7,  player, cooldowns));
        inv.setItem(SLOTS_CONC[col],  vipKitItem(MAT_CONC[col],  ICONS[col], kits[2], 30, player, cooldowns));
    }

    private static ItemStack vipKitItem(Material mat, String icon, VipKit kit, int amount,
                                        Player player, KitCooldownManager cooldowns) {
        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(icon));

        boolean hasPermission = player != null && player.hasPermission(kit.getPermission());

        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<!italic><reset><#848c94>" + kit.getDisplayName()));
        lore.add(MM.deserialize("<!italic><reset><#848c94>Cooldown: <#cbd1d7>" + kit.getCooldownLabel()));

        if (!hasPermission) {
            lore.add(Component.empty());
            lore.add(MM.deserialize("<!italic><#e22c27>Necessário: <reset><white>")
                    .append(MM.deserialize(icon)));
            lore.add(Component.empty());
            lore.add(MM.deserialize("<!italic><reset><#10fc46>ᴄʟɪǫᴜᴇ"));
            lore.add(MM.deserialize("<!italic><reset><#cbd1d7>Direito<#848c94> para visualizar."));
        } else if (player != null && cooldowns.isOnCooldown(player.getUniqueId(), kit)) {
            String rem = cooldowns.formatRemaining(player.getUniqueId(), kit);
            lore.add(Component.empty());
            lore.add(MM.deserialize("<!italic><reset><#10fc46>ᴘʀᴏxɪᴍᴀ ᴄᴏʟᴇᴛᴀ ᴇᴍ:"));
            lore.add(MM.deserialize("<!italic><reset><#cbd1d7>  " + (rem == null ? "" : rem)));
            lore.add(Component.empty());
            lore.add(MM.deserialize("<!italic><reset><#10fc46>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴠɪsᴜᴀʟɪᴢᴀʀ"));
        } else {
            lore.add(Component.empty());
            lore.add(MM.deserialize("<!italic><reset><#10fc46>ᴄʟɪǫᴜᴇ"));
            lore.add(MM.deserialize("<!italic><reset><#cbd1d7>Esquerdo<#848c94> para coletar."));
            lore.add(MM.deserialize("<!italic><reset><#cbd1d7>Direito<#848c94> para visualizar."));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack backButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<!italic><#cbd1d7>Voltar"));
        item.setItemMeta(meta);
        return item;
    }

    public static String iconFor(VipKit kit) {
        String key = kit.getConfigKey();
        if (key.startsWith("premium")) return "<font:nexo:default>ꐓ</font>";
        if (key.startsWith("elite"))   return "<font:nexo:default>ꐢ</font>";
        if (key.startsWith("eternal")) return "<font:nexo:default>ꐡ</font>";
        return "<font:nexo:default>♦</font>";
    }
}
