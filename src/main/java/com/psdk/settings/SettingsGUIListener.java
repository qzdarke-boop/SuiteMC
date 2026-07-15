package com.psdk.settings;

import com.psdk.PSDK;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class SettingsGUIListener implements Listener {

    private final PSDK plugin;

    public SettingsGUIListener(PSDK plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SettingsGUI)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getInventory()) return;

        int slot = event.getSlot();
        SettingsManager sm = plugin.getSettingsManager();

        String setting = switch (slot) {
            case 11 -> "tell";
            case 12 -> "mentions";
            case 13 -> "mention_sound";
            case 14 -> "chat_visible";
            case 31 -> { player.closeInventory(); yield null; }
            default -> null;
        };

        if (setting != null) {
            sm.toggle(player.getUniqueId(), setting);
            player.openInventory(SettingsGUI.build(plugin, player));
        }
    }
}
