package com.psdk.crates;

import com.psdk.PSDK;
import org.bukkit.Sound;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HologramInteractListener implements Listener {

    private final PSDK plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public HologramInteractListener(PSDK plugin) {
        this.plugin = plugin;
    }

    // Clique direito na entidade Interaction
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Interaction interaction)) return;
        event.setCancelled(true);
        tryOpen(event.getPlayer(), interaction);
    }

    // Clique direito "at entity" (variante do Bukkit)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Interaction interaction)) return;
        event.setCancelled(true);
        tryOpen(event.getPlayer(), interaction);
    }

    private void tryOpen(Player player, Interaction interaction) {
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < 500) return;
        cooldowns.put(player.getUniqueId(), now);

        String uuid = interaction.getUniqueId().toString();
        Crate crate = plugin.getCrateManager().getCrateByInteractionUUID(uuid);

        // Fallback: a hitbox pode ser uma Interaction órfã (UUID dessincronizado após
        // restart). Casa pela localização e re-vincula o UUID atual (auto-cura), pra
        // não falhar silenciosamente como antes.
        if (crate == null) {
            crate = plugin.getCrateManager().getCrateByLocation(interaction.getLocation());
            if (crate == null) return;
            crate.setInteractionUUID(uuid);
            plugin.getCrateManager().saveCrate(crate);
        }

        int saldo = plugin.getCrateManager().getSaldo(player.getUniqueId(), crate.getNome());
        double tokens = plugin.getEconomyManager().getTokens(player.getUniqueId());

        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        new CrateGUI(crate, player, saldo, tokens).open(player);
    }
}