package com.psdk.thepit;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Protege a estrutura da arena (the pit).
 *
 * Dentro da arena os jogadores só podem quebrar:
 *  • Minérios (dão coins — ver {@link BlockMineListener#MINEABLE});
 *  • Blocos que eles mesmos colocaram (limpos no próximo regen).
 *
 * Qualquer outro bloco (parede, chão, estrutura original do snapshot) é
 * protegido. OPs e quem tiver {@code psdk.arena.build} ignoram a proteção.
 * Colocar blocos continua liberado — eles são marcados e o regen os remove.
 */
public class ArenaProtectionListener implements Listener {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public ArenaProtectionListener(PSDK plugin) {
        this.plugin = plugin;
    }

    // LOW: roda depois da proteção de região (LOWEST) e ANTES do BlockMineListener
    // (HIGH), pra cancelar blocos estruturais antes de qualquer pagamento de coins.
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.isOp() || player.hasPermission("psdk.arena.build")) return;

        Block block = event.getBlock();
        Location loc = block.getLocation();
        ArenaManager arena = plugin.getArenaManager();
        if (!arena.isInsideArena(loc)) return;

        // Minérios liberados (BlockMineListener paga os coins em HIGH).
        if (BlockMineListener.MINEABLE.containsKey(block.getType())) return;

        // Blocos colocados pelos próprios jogadores podem ser quebrados.
        if (arena.isPlayerPlaced(block.getX(), block.getY(), block.getZ())) {
            arena.unmarkPlaced(block.getX(), block.getY(), block.getZ());
            return;
        }

        // Estrutura original da arena: protegida.
        event.setCancelled(true);
        player.sendActionBar(mm.deserialize("<#e22c27>Você só pode quebrar os minérios da arena!"));
    }

    // MONITOR + ignoreCancelled: só marca o bloco se a colocação realmente ocorreu
    // (ex.: não foi cancelada pela proteção de região).
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        ArenaManager arena = plugin.getArenaManager();
        if (arena.isInsideArena(block.getLocation())) {
            arena.markPlaced(block.getX(), block.getY(), block.getZ());
        }
    }
}
