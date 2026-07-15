package com.psdk.thepit;

import com.psdk.PSDK;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class TutorialGUIListener implements Listener {

    private final PSDK plugin;

    public TutorialGUIListener(PSDK plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TutorialGUI)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getInventory()) return;

        int slot = event.getSlot();
        if (!TutorialGUI.isClickable(slot)) return;

        String cmd = TutorialGUI.getCommand(slot);
        if (cmd == null) return;

        player.closeInventory();
        player.performCommand(cmd);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof TutorialGUI) {
            event.setCancelled(true);
        }
    }
}
