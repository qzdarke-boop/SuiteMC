package com.psdk.staff;

import com.psdk.PSDK;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class StaffCommandLogListener implements Listener {

    private final PSDK plugin;

    public StaffCommandLogListener(PSDK plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        plugin.getStaffManager().logCommand(event.getPlayer(), event.getMessage());
    }
}
