package com.psdk.bounty;

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

public class BountyGUIListener implements Listener {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public BountyGUIListener(PSDK plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Object holder = event.getInventory().getHolder();

        if (holder instanceof BountyGUI gui) {
            event.setCancelled(true);
            Inventory clicked = event.getClickedInventory();
            if (clicked == null || !clicked.equals(event.getInventory())) return;
            handleListClick(player, gui, event.getSlot());
        } else if (holder instanceof BountyConfirmGUI gui) {
            event.setCancelled(true);
            Inventory clicked = event.getClickedInventory();
            if (clicked == null || !clicked.equals(event.getInventory())) return;
            handleConfirmClick(player, gui, event.getSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        Object holder = event.getInventory().getHolder();
        if (holder instanceof BountyGUI || holder instanceof BountyConfirmGUI) {
            event.setCancelled(true);
        }
    }

    private void handleListClick(Player player, BountyGUI gui, int slot) {
        if (gui.isNextSlot(slot)) {
            open(player, gui.getPage() + 1);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        } else if (gui.isPrevSlot(slot)) {
            open(player, gui.getPage() - 1);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        }
    }

    private void open(Player player, int page) {
        BountyGUI gui = new BountyGUI(plugin.getBountyManager().getSortedDescending(), page);
        player.openInventory(gui.getInventory());
    }

    private void handleConfirmClick(Player player, BountyConfirmGUI gui, int slot) {
        if (gui.isCancelSlot(slot)) {
            player.closeInventory();
            player.sendMessage(mm.deserialize("<#FF5555>Recompensa cancelada."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }
        if (!gui.isConfirmSlot(slot)) return;

        double amount = gui.getAmountToAdd();
        // Débito atômico dos coins do jogador no momento da confirmação.
        if (!plugin.getEconomyManager().removeCoins(player.getUniqueId(), amount)) {
            player.closeInventory();
            player.sendMessage(mm.deserialize("<#FF0000>Você não tem coins suficientes para essa recompensa!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }

        boolean ok = plugin.getBountyManager().addBounty(gui.getTarget(), gui.getTargetName(), amount);
        if (!ok) {
            // Persistência falhou: devolve os coins debitados para não haver perda.
            // Devolução: não conta para o Top Coins.
            plugin.getEconomyManager().addCoinsNoStat(player.getUniqueId(), player.getName(), amount);
            player.closeInventory();
            player.sendMessage(mm.deserialize("<#FF0000>Erro ao registrar a recompensa. Seus coins foram devolvidos."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }
        player.closeInventory();
        player.sendMessage(mm.deserialize("<#10fc46>Você adicionou <#fcc850>" + BountyManager.fmt(amount)
                + " <#10fc46>de recompensa pelo abate de <#fcc850>" + gui.getTargetName() + "<#10fc46>."));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.2f);
    }
}
