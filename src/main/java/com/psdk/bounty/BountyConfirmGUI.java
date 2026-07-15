package com.psdk.bounty;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** GUI de confirmação ao adicionar recompensa (cabeça do alvo + confirmar/cancelar). */
public class BountyConfirmGUI implements InventoryHolder {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    public static final Component TITLE = mm.deserialize("<#a4a4a4>Confirmar recompensa?");

    static final int CANCEL_SLOT = 11;
    static final int HEAD_SLOT = 13;
    static final int CONFIRM_SLOT = 15;

    private final Inventory inventory;
    private final UUID target;
    private final String targetName;
    private final double amountToAdd;
    private final double currentBounty;

    public BountyConfirmGUI(UUID target, String targetName, double amountToAdd, double currentBounty) {
        this.target = target;
        this.targetName = targetName;
        this.amountToAdd = amountToAdd;
        this.currentBounty = currentBounty;
        this.inventory = Bukkit.createInventory(this, 27, TITLE);
        build();
    }

    private void build() {
        inventory.setItem(CANCEL_SLOT, pane(Material.RED_STAINED_GLASS_PANE, "<#FF5555>Cancelar"));
        inventory.setItem(HEAD_SLOT, buildHead());
        inventory.setItem(CONFIRM_SLOT, pane(Material.GREEN_STAINED_GLASS_PANE, "<#10fc46>Confirmar"));
    }

    private ItemStack buildHead() {
        ItemStack head = BountyHeadFactory.head(target, targetName);
        ItemMeta meta = head.getItemMeta();
        if (meta == null) return head;

        double novo = currentBounty + amountToAdd;
        List<Component> lore = new ArrayList<>();
        lore.add(line("<#a4a4a4>Dynamic"));
        lore.add(Component.empty());
        lore.add(line("<#efa600>Recompensa atual:"));
        lore.add(line("<#10fc46>" + BountyManager.fmt(currentBounty)));
        lore.add(Component.empty());
        lore.add(line("<#efa600>Você vai adicionar:"));
        lore.add(line("<#10fc46>" + BountyManager.fmt(amountToAdd)));
        lore.add(Component.empty());
        lore.add(line("<#efa600>Nova recompensa:"));
        lore.add(line("<#10fc46>" + BountyManager.fmt(novo)));
        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack pane(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(line(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static Component line(String mini) {
        return mm.deserialize(mini).decoration(TextDecoration.ITALIC, false);
    }

    public UUID getTarget() { return target; }
    public String getTargetName() { return targetName; }
    public double getAmountToAdd() { return amountToAdd; }
    public boolean isCancelSlot(int slot) { return slot == CANCEL_SLOT; }
    public boolean isConfirmSlot(int slot) { return slot == CONFIRM_SLOT; }

    @Override public Inventory getInventory() { return inventory; }
}
