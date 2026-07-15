package com.psdk.kits;

import com.psdk.PSDK;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class VipKitPreviewGUI {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    public static final int SLOT_BACK = 49;

    public static Inventory build(PSDK plugin, VipKit kit) {
        Component title = MM.deserialize("<#a4a4a4>" + kit.getDisplayName());
        KitsInventoryHolder holder = KitsInventoryHolder.vipPreview(kit);
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.bind(inv);

        for (var entry : kit.resolveSlotItems(plugin).entrySet()) {
            inv.setItem(entry.getKey(), entry.getValue().clone());
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        meta.displayName(MM.deserialize("<!italic><#cbd1d7>Voltar"));
        back.setItemMeta(meta);
        inv.setItem(SLOT_BACK, back);
        return inv;
    }

    public static boolean isPreviewTitle(Component title) {
        String plain = PLAIN.serialize(title);
        for (VipKit kit : VipKit.values()) {
            if (plain.equals(kit.getDisplayName())) return true;
        }
        return false;
    }

    public static VipKit kitFromTitle(Component title) {
        String plain = PLAIN.serialize(title);
        for (VipKit kit : VipKit.values()) {
            if (plain.equals(kit.getDisplayName())) return kit;
        }
        return null;
    }
}
