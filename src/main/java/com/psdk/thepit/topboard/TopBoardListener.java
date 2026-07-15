package com.psdk.thepit.topboard;

import com.psdk.PSDK;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TopBoardListener implements Listener {

    private static final long COOLDOWN_MS = 400;

    private final PSDK plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public TopBoardListener(PSDK plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRightClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (handleRight(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRightClickAt(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (handleRight(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLeftClickPre(PrePlayerAttackEntityEvent event) {
        if (handleLeft(event.getPlayer(), event.getAttacked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLeftClickDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (handleLeft(player, event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private boolean handleLeft(Player player, Entity clicked) {
        TopBoard board = resolveBoard(player, clicked);
        if (board == null) return false;
        if (!tryCooldown(player)) return true;
        plugin.getTopBoardManager().cyclePeriod(player, board);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
        return true;
    }

    private boolean handleRight(Player player, Entity clicked) {
        TopBoard board = resolveBoard(player, clicked);
        if (board == null) return false;
        if (!tryCooldown(player)) return true;
        plugin.getTopBoardManager().nextPage(player, board);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 0.9f);
        return true;
    }

    private TopBoard resolveBoard(Player player, Entity entity) {
        TopBoardManager manager = plugin.getTopBoardManager();
        if (!manager.isTopBoardEntity(entity)) return null;

        if (entity instanceof Interaction interaction) {
            TopBoard board = manager.getByInteraction(interaction.getUniqueId());
            if (board != null) return board;
            if (interaction.getLocation() != null) {
                return manager.findNearest(interaction.getLocation(), 16.0);
            }
            return null;
        }
        if (entity instanceof TextDisplay) {
            TopBoard board = manager.getByPlayerDisplay(player.getUniqueId(), entity.getUniqueId());
            if (board != null) return board;
            if (entity.getLocation() != null) {
                return manager.findNearest(entity.getLocation(), 16.0);
            }
            return null;
        }
        return null;
    }

    private boolean tryCooldown(Player player) {
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) return false;
        cooldowns.put(player.getUniqueId(), now);
        return true;
    }
}
