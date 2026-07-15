package com.psdk.staff;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

/**
 * Inventário espelhado de outro jogador — armadura, offhand, mochila e hotbar
 * separados por vidros decorativos.
 */
public final class InvSeeGUI implements org.bukkit.inventory.InventoryHolder {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Mochila (player 9–35). */
    public static final int MAIN_START = 0;
    public static final int MAIN_END = 26;
    /** Hotbar (player 0–8). */
    public static final int HOTBAR_START = 27;
    public static final int HOTBAR_END = 35;
    /** Linha separadora. */
    public static final int SEPARATOR_START = 36;
    public static final int SEPARATOR_END = 44;
    public static final int SLOT_BOOTS = 46;
    public static final int SLOT_LEGGINGS = 48;
    public static final int SLOT_CHEST = 50;
    public static final int SLOT_HELMET = 52;
    public static final int SLOT_OFFHAND = 53;

    private static final int[] ARMOR_GUI_SLOTS = {
            SLOT_BOOTS, SLOT_LEGGINGS, SLOT_CHEST, SLOT_HELMET, SLOT_OFFHAND
    };

    private final Inventory inventory;
    private final UUID targetId;
    private final String targetName;
    private final boolean offline;

    private InvSeeGUI(UUID targetId, String targetName, boolean offline, Component title) {
        this.targetId = targetId;
        this.targetName = targetName;
        this.offline = offline;
        this.inventory = Bukkit.createInventory(this, 54, title);
    }

    public UUID getTargetId() { return targetId; }
    public String getTargetName() { return targetName; }
    public boolean isOffline() { return offline; }

    @Override
    public Inventory getInventory() { return inventory; }

    public static InvSeeGUI create(Player target) {
        Component title = MM.deserialize("<dark_gray>InvSee <gray>— <#fcc850>" + target.getName());
        InvSeeGUI holder = new InvSeeGUI(target.getUniqueId(), target.getName(), false, title);
        holder.fillGlass();
        copyFromPlayer(target, holder.inventory);
        return holder;
    }

    public static InvSeeGUI createOffline(UUID targetId, String targetName, ItemStack[] playerContents) {
        Component title = MM.deserialize("<dark_gray>InvSee <gray>— <#fcc850>" + targetName
                + " <gray>(offline)");
        InvSeeGUI holder = new InvSeeGUI(targetId, targetName, true, title);
        holder.fillGlass();
        if (playerContents != null) copyFromArray(playerContents, holder.inventory);
        return holder;
    }

    public static boolean isGlassSlot(int slot) {
        if (slot >= SEPARATOR_START && slot <= SEPARATOR_END) return true;
        return slot == 45 || slot == 47 || slot == 49 || slot == 51;
    }

    public static Integer guiSlotToPlayerSlot(int guiSlot) {
        if (guiSlot >= MAIN_START && guiSlot <= MAIN_END) return guiSlot + 9;
        if (guiSlot >= HOTBAR_START && guiSlot <= HOTBAR_END) return guiSlot - HOTBAR_START;
        return switch (guiSlot) {
            case SLOT_BOOTS -> 36;
            case SLOT_LEGGINGS -> 37;
            case SLOT_CHEST -> 38;
            case SLOT_HELMET -> 39;
            case SLOT_OFFHAND -> 40;
            default -> null;
        };
    }

    public static void copyFromPlayer(Player target, Inventory gui) {
        PlayerInventory inv = target.getInventory();
        for (int guiSlot = 0; guiSlot < 54; guiSlot++) {
            Integer playerSlot = guiSlotToPlayerSlot(guiSlot);
            if (playerSlot == null) continue;
            ItemStack item = inv.getItem(playerSlot);
            gui.setItem(guiSlot, item == null ? null : item.clone());
        }
    }

    public static void syncToPlayer(Player target, Inventory gui) {
        PlayerInventory inv = target.getInventory();
        for (int guiSlot = 0; guiSlot < 54; guiSlot++) {
            Integer playerSlot = guiSlotToPlayerSlot(guiSlot);
            if (playerSlot == null) continue;
            ItemStack item = gui.getItem(guiSlot);
            inv.setItem(playerSlot, item == null || item.getType().isAir() ? null : item.clone());
        }
        target.updateInventory();
    }

    /**
     * Extrai o conteúdo do GUI para um array de 41 posições no layout do jogador
     * (0–35 mochila/hotbar, 36–39 armadura, 40 offhand) — usado para salvar offline.
     */
    public static ItemStack[] extractContents(Inventory gui) {
        ItemStack[] contents = new ItemStack[41];
        for (int guiSlot = 0; guiSlot < 54; guiSlot++) {
            Integer playerSlot = guiSlotToPlayerSlot(guiSlot);
            if (playerSlot == null || playerSlot >= contents.length) continue;
            ItemStack item = gui.getItem(guiSlot);
            contents[playerSlot] = (item == null || item.getType().isAir()) ? null : item.clone();
        }
        return contents;
    }

    private static void copyFromArray(ItemStack[] contents, Inventory gui) {
        for (int guiSlot = 0; guiSlot < 54; guiSlot++) {
            Integer playerSlot = guiSlotToPlayerSlot(guiSlot);
            if (playerSlot == null || playerSlot >= contents.length) continue;
            ItemStack item = contents[playerSlot];
            gui.setItem(guiSlot, item == null ? null : item.clone());
        }
    }

    private void fillGlass() {
        ItemStack sep = pane(Material.BLACK_STAINED_GLASS_PANE, "<!italic><gray> ");
        for (int i = SEPARATOR_START; i <= SEPARATOR_END; i++) inventory.setItem(i, sep);

        inventory.setItem(45, pane(Material.GRAY_STAINED_GLASS_PANE, "<!italic><#a4a4a4>Botas"));
        inventory.setItem(47, pane(Material.GRAY_STAINED_GLASS_PANE, "<!italic><#a4a4a4>Calças"));
        inventory.setItem(49, pane(Material.GRAY_STAINED_GLASS_PANE, "<!italic><#a4a4a4>Peitoral"));
        inventory.setItem(51, pane(Material.GRAY_STAINED_GLASS_PANE, "<!italic><#a4a4a4>Capacete"));
    }

    public void refreshGlass() {
        fillGlass();
    }

    public static int[] armorGuiSlots() {
        return ARMOR_GUI_SLOTS.clone();
    }

    private static ItemStack pane(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MM.deserialize(name));
            item.setItemMeta(meta);
        }
        return item;
    }
}
