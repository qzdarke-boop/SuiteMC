package com.psdk.kits;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class KitsBasicosGUI {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public static final String TITLE     = "<#a4a4a4>Kits Básicos";
    public static final int    SLOT_BACK = 22;

    // FERRAMENTAS=11, COMIDA=13, PVP=15
    public static int kitSlot(Kit kit) {
        return switch (kit) {
            case FERRAMENTAS -> 11;
            case COMIDA      -> 13;
            case PVP         -> 15;
        };
    }

    public static Kit kitAtSlot(int slot) {
        for (Kit kit : Kit.values()) {
            if (kitSlot(kit) == slot) return kit;
        }
        return null;
    }

    public static Inventory build(UUID playerId, KitCooldownManager cooldowns) {
        KitsInventoryHolder holder = KitsInventoryHolder.basicos();
        Inventory inv = Bukkit.createInventory(holder, 27, MM.deserialize(TITLE));
        holder.bind(inv);

        for (Kit kit : Kit.values()) {
            inv.setItem(kitSlot(kit), buildKitItem(kit, playerId, cooldowns));
        }

        inv.setItem(SLOT_BACK, backButton());
        return inv;
    }

    private static ItemStack buildKitItem(Kit kit, UUID playerId, KitCooldownManager cooldowns) {
        ItemStack stack = new ItemStack(kit.getIcon());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(MM.deserialize("<!italic>" + kit.getDisplayName()));

        long mins = TimeUnit.MILLISECONDS.toMinutes(kit.getCooldownMs());
        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<!italic><#848c94>Você pode coletar este"));
        lore.add(MM.deserialize("<!italic><#848c94>kit a cada <#cbd1d7>" + mins + " minutos<#848c94>."));

        if (cooldowns.isOnCooldown(playerId, kit)) {
            String rem = cooldowns.formatRemaining(playerId, kit);
            lore.add(Component.empty());
            lore.add(MM.deserialize("<!italic><#10fc46>ᴘʀᴏxɪᴍᴀ ᴄᴏʟᴇᴛᴀ ᴇᴍ:"));
            lore.add(MM.deserialize("<!italic><#cbd1d7>  " + (rem == null ? "" : rem)));
            lore.add(Component.empty());
            lore.add(MM.deserialize("<!italic><#10fc46>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴠɪsᴜᴀʟɪᴢᴀʀ"));
        } else {
            lore.add(Component.empty());
            lore.add(MM.deserialize("<!italic><#10fc46>ᴄʟɪǫᴜᴇ"));
            lore.add(MM.deserialize("<!italic><#cbd1d7>Esquerdo<#848c94> para coletar."));
            lore.add(MM.deserialize("<!italic><#cbd1d7>Direito<#848c94> para visualizar."));
        }

        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack backButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<!italic><#cbd1d7>Voltar"));
        item.setItemMeta(meta);
        return item;
    }
}
