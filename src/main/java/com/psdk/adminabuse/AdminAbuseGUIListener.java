package com.psdk.adminabuse;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/** Cliques no painel do Admin Abuse. */
public class AdminAbuseGUIListener implements Listener {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public AdminAbuseGUIListener(PSDK plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AdminAbuseGUI)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals(event.getInventory())) return;
        if (!player.hasPermission("psdk.adminabuse")) { player.closeInventory(); return; }

        AdminAbuseManager am = plugin.getAdminAbuseManager();
        int slot = event.getSlot();

        switch (slot) {
            case AdminAbuseGUI.SLOT_START -> {
                player.closeInventory();
                am.start(player);
            }
            case AdminAbuseGUI.SLOT_MINING -> {
                am.toggleMining2x(player);
                player.openInventory(new AdminAbuseGUI(am).getInventory());   // re-renderiza o status
            }
            case AdminAbuseGUI.SLOT_CHESTALL -> {
                player.closeInventory();
                am.chestAll(player);
            }
            case AdminAbuseGUI.SLOT_COINS -> {
                player.closeInventory();
                am.coinRain(player);
            }
            case AdminAbuseGUI.SLOT_FIREWORK -> {
                player.closeInventory();
                am.fireworksParty(player);
            }
            case AdminAbuseGUI.SLOT_ARSENAL -> {
                player.closeInventory();
                if (!am.isActive()) {
                    player.sendMessage(mm.deserialize("<#FF0000>O show não está ativo. Use Iniciar primeiro."));
                } else {
                    am.giveArsenal(player);
                }
            }
            case AdminAbuseGUI.SLOT_BRAZIL -> {
                player.closeInventory();
                am.brazilFade(player);
            }
            case AdminAbuseGUI.SLOT_SHUFFLE -> {
                player.closeInventory();
                am.shuffleAll(player);
            }
            case AdminAbuseGUI.SLOT_ROCKET -> {
                player.closeInventory();
                am.rocketAll(player);
            }
            case AdminAbuseGUI.SLOT_TURBO -> {
                player.closeInventory();
                am.turboAll(player);
            }
            case AdminAbuseGUI.SLOT_ZOMBIE -> {
                player.closeInventory();
                am.zombieInvasion(player);
            }
            case AdminAbuseGUI.SLOT_SIZE -> {
                player.closeInventory();
                am.sizeChaos(player);
            }
            case AdminAbuseGUI.SLOT_LOTTERY -> {
                player.closeInventory();
                am.lotteryDraw(player);
            }
            case AdminAbuseGUI.SLOT_POTATO -> {
                player.closeInventory();
                am.hotPotato(player);
            }
            case AdminAbuseGUI.SLOT_RAINBOW -> {
                player.closeInventory();
                am.rainbowTrails(player);
            }
            case AdminAbuseGUI.SLOT_STORM -> {
                player.closeInventory();
                am.thunderstorm(player);
            }
            case AdminAbuseGUI.SLOT_STOP -> {
                player.closeInventory();
                am.stop();
                player.sendMessage(mm.deserialize("<#10fc46>Tudo encerrado."));
            }
            default -> { return; }
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof AdminAbuseGUI) event.setCancelled(true);
    }
}
