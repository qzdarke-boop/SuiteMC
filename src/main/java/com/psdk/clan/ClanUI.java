package com.psdk.clan;

import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashSet;
import java.util.Set;

/**
 * Estilo visual compartilhado das GUIs de clan (cores, gradientes, sons, preenchimento).
 */
public final class ClanUI {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private ClanUI() {}

    public static void clickSound(Player player) {
        if (player != null) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.65f, 1.25f);
        }
    }

    public static void openSound(Player player) {
        if (player != null) {
            player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.45f, 1.35f);
        }
    }

    public static void successSound(Player player) {
        if (player != null) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
        }
    }

    /** Título com gradiente MiniMessage. */
    public static String gradientTitle(String fromHex, String toHex, String text) {
        return "<gradient:" + fromHex + ":" + toHex + "><bold>" + text + "</bold></gradient>";
    }

    /** Linha de ação no lore (small caps). */
    public static String click(String action) {
        return "<#6CB4FF>▸ <#B8D9FF>" + action;
    }

    public static String stat(String label, String value) {
        return "<#8B949E>" + label + ": <white>" + value;
    }

    public static String divider() {
        return "<dark_gray><st>                              </st>";
    }

    public static ItemStack fillerPane() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MM.deserialize(" ").decoration(TextDecoration.ITALIC, false));
            meta.lore(java.util.List.of());
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Preenche slots vazios com vidro decorativo (não sobrescreve itens já colocados). */
    public static void fillBackground(Inventory inv, int size, int... reserved) {
        Set<Integer> keep = new HashSet<>();
        for (int slot : reserved) keep.add(slot);
        ItemStack pane = fillerPane();
        for (int i = 0; i < size; i++) {
            if (keep.contains(i)) continue;
            if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                inv.setItem(i, pane);
            }
        }
    }
}
