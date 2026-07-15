package com.psdk.lootchest;

import com.psdk.PSDK;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Abertura do baú (clique direito na entidade de interação) e gestão da contagem
 * de 5s ao fechar o menu (cancelada ao reabrir).
 */
public class LootChestListener implements Listener {

    private final PSDK plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public LootChestListener(PSDK plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        tryOpen(event.getPlayer(), event.getRightClicked().getUniqueId(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        tryOpen(event.getPlayer(), event.getRightClicked().getUniqueId(), event);
    }

    private void tryOpen(Player player, UUID entityUuid, org.bukkit.event.Cancellable event) {
        // Resolve por interação OU pelo shulker de colisão — assim o clique no
        // hitbox invisível também abre o baú (e é cancelado).
        LootChest chest = plugin.getLootChestManager().getChestByEntity(entityUuid);
        if (chest == null) return;
        event.setCancelled(true);
        if (chest.isLeaving()) return;

        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < 500) return;
        cooldowns.put(player.getUniqueId(), now);

        plugin.getLootChestManager().cancelCountdown(chest);
        if (chest.getLoc().getWorld() != null) {
            chest.getLoc().getWorld().playSound(
                    chest.getLoc().clone().add(0.5, 0, 0.5), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.0f);
        }
        player.openInventory(chest.getInventory());
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder() instanceof LootChest chest) {
            plugin.getLootChestManager().cancelCountdown(chest);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof LootChest chest)) return;
        // Verifica no próximo tick: se ninguém mais está olhando, inicia a contagem.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (chest.isLeaving()) return;
            if (chest.getInventory().getViewers().isEmpty()) {
                plugin.getLootChestManager().startCountdown(chest);
            }
        });
    }
}
