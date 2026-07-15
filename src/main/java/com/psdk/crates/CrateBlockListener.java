package com.psdk.crates;

import com.psdk.PSDK;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class CrateBlockListener implements Listener {

    private final PSDK plugin;

    public CrateBlockListener(PSDK plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Crate crate = plugin.getCrateManager().getCrateByLocation(block.getLocation());
        if (crate == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        int saldo = plugin.getCrateManager().getSaldo(player.getUniqueId(), crate.getNome());
        double tokens = plugin.getEconomyManager().getTokens(player.getUniqueId());

        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        new CrateGUI(crate, player, saldo, tokens).open(player);
    }
}