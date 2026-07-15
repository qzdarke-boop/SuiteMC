package com.psdk.thepit.topboard;

import com.psdk.PSDK;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class TopBoardPlayerListener implements Listener {

    private final PSDK plugin;

    public TopBoardPlayerListener(PSDK plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                plugin.getTopBoardManager().updatePlayerHolograms(event.getPlayer());
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getTopBoardManager().removeAllPlayerHolograms(event.getPlayer());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getTopBoardManager().updatePlayerHolograms(event.getPlayer()));
    }
}
