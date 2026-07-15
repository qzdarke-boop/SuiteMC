package com.psdk.kits;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class KitPreviewGUI {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public static final String TITLE_PREFIX = "Visualizando ";
    public static final int    SLOT_BACK    = 22;
    public static final int    START_SLOT   = 12;

    public static Inventory build(Kit kit) {
        Component title = MM.deserialize("<#a4a4a4>" + TITLE_PREFIX + kit.getName());
        KitsInventoryHolder holder = KitsInventoryHolder.basicPreview(kit);
        Inventory inv = Bukkit.createInventory(holder, 27, title);
        holder.bind(inv);

        List<ItemStack> items = kit.getItems();
        for (int i = 0; i < items.size(); i++) {
            int slot = START_SLOT + i;
            if (slot >= inv.getSize()) break;
            inv.setItem(slot, items.get(i).clone());
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        meta.displayName(MM.deserialize("<!italic><#e22c27>Voltar"));
        back.setItemMeta(meta);
        inv.setItem(SLOT_BACK, back);
        return inv;
    }

    public static boolean isPreviewTitle(Component title) {
        return PlainTextComponentSerializer.plainText().serialize(title).contains(TITLE_PREFIX);
    }
}
