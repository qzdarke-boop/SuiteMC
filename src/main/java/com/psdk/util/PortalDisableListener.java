package com.psdk.util;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.PortalCreateEvent;

/**
 * Desativa os portais do Nether e do End:
 *  - cancela a TELEPORTE por portal (jogador {@link PlayerPortalEvent} e entidades {@link EntityPortalEvent}),
 *  - cancela a CRIAÇÃO do portal do Nether ({@link PortalCreateEvent}, ao acender a obsidiana),
 *  - impede ATIVAR o portal do End (colocar o olho de ender no frame).
 */
public class PortalDisableListener implements Listener {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    // Jogador entrando em portal (Nether OU End) — não teleporta.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        event.setCancelled(true);
        event.getPlayer().sendActionBar(mm.deserialize("<#e22c27>Os portais estão desativados neste servidor!"));
    }

    // Mobs / itens também não passam por portal.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        event.setCancelled(true);
    }

    // Não forma o portal do Nether ao acender a moldura.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        event.setCancelled(true);
    }

    // Impede ativar o portal do End (olho de ender no frame).
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEnderEye(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null
                || event.getClickedBlock().getType() != Material.END_PORTAL_FRAME) return;
        if (event.getItem() != null && event.getItem().getType() == Material.ENDER_EYE) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(mm.deserialize("<#e22c27>O portal do End está desativado!"));
        }
    }
}
