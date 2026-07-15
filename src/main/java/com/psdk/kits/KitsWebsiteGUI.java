package com.psdk.kits;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Menu de Kits Website (27 slots). Slot central com teia informativa. */
public class KitsWebsiteGUI {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public static final String TITLE      = "<#a4a4a4>Kits Website";
    private static final int   SLOT_COBWEB = 13;
    public static final int    SLOT_BACK   = 22;

    public static Inventory build() {
        KitsInventoryHolder holder = KitsInventoryHolder.website();
        Inventory inv = Bukkit.createInventory(holder, 27, MM.deserialize(TITLE));
        holder.bind(inv);
        inv.setItem(SLOT_COBWEB, buildCobweb());
        inv.setItem(SLOT_BACK, backButton());
        return inv;
    }

    private static ItemStack buildCobweb() {
        ItemStack item = new ItemStack(Material.COBWEB);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<!italic><white>Ops! Parece que não tem nada aqui..."));
        meta.lore(List.of(MM.deserialize("<!italic><#848c94>Que tal voltar mais tarde?")));
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
}
