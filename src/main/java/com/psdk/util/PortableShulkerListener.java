package com.psdk.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

public class PortableShulkerListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == null) return;

        ItemStack item = event.getItem();
        if (!isShulker(item)) return;

        Player player = event.getPlayer();
        ItemStack opened = item.clone();
        opened.setAmount(1);

        event.setCancelled(true);
        removeOneFromHand(player, event.getHand(), item);

        PortableShulkerHolder holder = new PortableShulkerHolder(opened);
        Inventory inventory = Bukkit.createInventory(holder, 27, title(opened));
        holder.inventory = inventory;
        loadContents(opened, inventory);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PortableShulkerHolder)) return;

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        if (isShulker(cursor) || isShulker(current)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage(MM.deserialize("<#FF0000>Você não pode colocar shulkers dentro de shulkers."));
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PortableShulkerHolder)) return;
        if (isShulker(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof PortableShulkerHolder holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        returnShulker(player, holder, event.getInventory());
    }

    /**
     * Devolve o shulker (com o conteúdo atualizado) ao jogador. Idempotente: o flag
     * {@code closed} garante que isto rode UMA vez só — sem isso, o flush de desligamento
     * + o onClose poderiam devolver o item duas vezes (dupe).
     */
    private static void returnShulker(Player player, PortableShulkerHolder holder, Inventory inv) {
        if (holder.closed) return;
        holder.closed = true;
        ItemStack updated = holder.item.clone();
        saveContents(updated, inv);
        var overflow = player.getInventory().addItem(updated);
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    /**
     * Flush de desligamento: no shutdown os listeners são desregistrados ANTES de os
     * jogadores desconectarem, então o onClose não dispara e o shulker aberto (que está
     * "fora da mão") sumiria. Chamado no onDisable, devolve todos antes do save final.
     */
    public static void saveAllOpen() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Inventory top = p.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof PortableShulkerHolder holder) {
                returnShulker(p, holder, top);
            }
        }
    }

    private static void loadContents(ItemStack item, Inventory inventory) {
        if (!(item.getItemMeta() instanceof BlockStateMeta meta)) return;
        if (!(meta.getBlockState() instanceof ShulkerBox box)) return;
        inventory.setContents(box.getInventory().getContents());
    }

    private static void saveContents(ItemStack item, Inventory inventory) {
        if (!(item.getItemMeta() instanceof BlockStateMeta meta)) return;
        if (!(meta.getBlockState() instanceof ShulkerBox box)) return;
        box.getInventory().setContents(inventory.getContents());
        meta.setBlockState(box);
        item.setItemMeta(meta);
    }

    private static void removeOneFromHand(Player player, EquipmentSlot hand, ItemStack item) {
        ItemStack remaining = item.clone();
        remaining.setAmount(item.getAmount() - 1);
        if (remaining.getAmount() <= 0) remaining = null;

        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(remaining);
        } else {
            player.getInventory().setItemInMainHand(remaining);
        }
    }

    private static Component title(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().displayName();
        }
        return MM.deserialize("<#a4a4a4>Shulker");
    }

    private static boolean isShulker(ItemStack item) {
        return item != null && item.getType() != Material.AIR && item.getType().name().endsWith("SHULKER_BOX");
    }

    private static final class PortableShulkerHolder implements InventoryHolder {
        private final ItemStack item;
        private Inventory inventory;
        private boolean closed; // anti-dupe: garante devolução única

        private PortableShulkerHolder(ItemStack item) {
            this.item = item;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
