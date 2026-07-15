package com.psdk.social;

import com.psdk.PSDK;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Clique (esquerdo ou direito) no NPC do Wumpus -> mostra o link do Discord.
 * Tem um cooldown curto pra não spammar a mensagem.
 */
public class DiscordNpcListener implements Listener {

    private static final long COOLDOWN_MS = 1500;

    private final PSDK plugin;
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();

    public DiscordNpcListener(PSDK plugin) { this.plugin = plugin; }

    @EventHandler
    public void onRightClick(PlayerInteractEntityEvent event) {
        if (!plugin.getDiscordNpcManager().isNpc(event.getRightClicked())) return;
        event.setCancelled(true);
        trigger(event.getPlayer());
    }

    @EventHandler
    public void onLeftClick(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p)) return;
        if (!plugin.getDiscordNpcManager().isNpc(event.getEntity())) return;
        event.setCancelled(true);
        trigger(p);
    }

    private void trigger(Player p) {
        long now = System.currentTimeMillis();
        Long last = cooldown.get(p.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) return;
        cooldown.put(p.getUniqueId(), now);
        SocialLinks.sendDiscord(p);   // mesma mensagem do /discord
    }
}
