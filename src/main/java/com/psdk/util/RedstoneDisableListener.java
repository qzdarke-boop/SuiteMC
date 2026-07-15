package com.psdk.util;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

/**
 * Desativa toda a redstone do servidor.
 *
 * <p>Zera qualquer corrente de redstone ({@link BlockRedstoneEvent}), o que neutraliza
 * fios, tochas, repetidores, comparadores e qualquer mecanismo alimentado por sinal.
 * Pistões são cancelados explicitamente como segurança adicional, já que sem corrente
 * eles não receberiam sinal de qualquer forma.</p>
 */
public class RedstoneDisableListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRedstone(BlockRedstoneEvent event) {
        // Força a corrente para 0 — nenhum sinal de redstone se propaga.
        event.setNewCurrent(0);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        event.setCancelled(true);
    }
}
