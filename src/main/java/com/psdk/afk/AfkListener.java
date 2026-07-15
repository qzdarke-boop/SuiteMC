package com.psdk.afk;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class AfkListener implements Listener {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public AfkListener(PSDK plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getAfkManager().removeAfk(event.getPlayer());
    }

    /**
     * Se o jogador estava AFK e saiu do mundo de AFK por qualquer meio
     * (portal, /home, /tp, void, etc.), remove o status AFK automaticamente —
     * caso contrário ele ficaria "preso" como AFK e não conseguiria usar /afk de novo.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        AfkManager afk = plugin.getAfkManager();
        if (afk.isAfk(player) && !afk.isInAfkWorld(player)) {
            afk.removeAfk(player);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && plugin.getAfkManager().isInAfkWorld(victim)) {
            event.setCancelled(true);
            return;
        }
        if (event.getDamager() instanceof Player attacker && plugin.getAfkManager().isInAfkWorld(attacker)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getAfkManager().isInAfkWorld(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getAfkManager().isInAfkWorld(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.getAfkManager().isInAfkWorld(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (plugin.getAfkManager().isInAfkWorld(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
}
