package com.psdk.util;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

public final class WandUtils {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final String WAND_KEY = "psdk_arena_wand";
    private static NamespacedKey wandKey;

    private WandUtils() {}

    public static void init(Plugin plugin) {
        wandKey = new NamespacedKey(plugin, WAND_KEY);
    }

    public static ItemStack createWand() {
        ItemStack item = new ItemStack(Material.GOLDEN_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(mm.deserialize("<!italic><#fcc850><bold>Arena Wand"));
            meta.lore(List.of(
                    mm.deserialize("<!italic><#fcc850>Clique Esquerdo: <#a4a4a4>Definir Pos1"),
                    mm.deserialize("<!italic><#fcc850>Clique Direito: <#a4a4a4>Definir Pos2")
            ));
            if (wandKey != null) {
                meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.GOLDEN_PICKAXE) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && wandKey != null
                && meta.getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }
}
