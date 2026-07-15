package com.psdk.cage;

import com.psdk.PSDK;
import com.psdk.pitems.AbilityCooldownManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lógica de eventos da Jaula:
 * <ul>
 *   <li>Ativação por clique direito no chão (mão principal, clique único);</li>
 *   <li>Proteção total da estrutura (quebra, explosão, pistão, líquido, física,
 *       fogo, entidades, world events);</li>
 *   <li>Contador compartilhado de tentativas de quebra pelos presos;</li>
 *   <li>Interceptação de teleporte/movimento que atravesse a fronteira;</li>
 *   <li>Limpeza automática quando a Jaula fica sem presos.</li>
 * </ul>
 * Todas as regras de fronteira/estado ficam centralizadas no {@link CageManager}.
 */
public class CageListener implements Listener {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final long ACTIVATE_DEBOUNCE_MS = 300L;
    private static final long TP_MSG_COOLDOWN_MS = 1200L;

    private final PSDK plugin;
    private final Map<UUID, Long> activateDebounce = new HashMap<>();
    private final Map<UUID, Long> tpMsgCooldown = new HashMap<>();

    public CageListener(PSDK plugin) {
        this.plugin = plugin;
    }

    private CageManager mgr() {
        return plugin.getCageManager();
    }

    // ─────────────────────────────── Ativação ─────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onActivate(PlayerInteractEvent event) {
        // Só mão principal → evita o segundo evento da mão secundária.
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!CageItem.isCageItem(plugin, hand)) return;

        // Nunca colocar como bloco comum / não interagir com o bloco clicado.
        event.setCancelled(true);

        // Item temporário expirado: não cria a Jaula (o PSDKItemExpireTask remove em seguida).
        if (com.psdk.pitems.PSDKItems.isExpired(hand)) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        // Anti clique-duplicado do mesmo uso.
        long now = System.currentTimeMillis();
        Long last = activateDebounce.get(player.getUniqueId());
        if (last != null && now - last < ACTIVATE_DEBOUNCE_MS) return;
        activateDebounce.put(player.getUniqueId(), now);

        // Cooldown de 5 min (padrão visual do escudo). Enquanto recarrega, não faz nada
        // (a recarga já aparece no item) — não consome nem tenta criar.
        AbilityCooldownManager cd = plugin.getAbilityCooldownManager();
        if (!cd.isReady(player, AbilityCooldownManager.Ability.JAULA)) return;

        String error = mgr().tryCreateCage(player, clicked);
        if (error != null) {
            // Tentativa inválida: não consome o item nem inicia o cooldown.
            player.sendActionBar(mm.deserialize(error));
            return;
        }

        // Sucesso: consome exatamente uma unidade (criativo não consome) e inicia o cooldown.
        if (player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }
        cd.start(player, AbilityCooldownManager.Ability.JAULA);
    }

    // ───────────────────────── Tentativas de quebra ───────────────────────────

    // Uma tentativa só conta quando o jogador CONCLUI a quebra do vidro (BlockBreakEvent),
    // não ao dar um tapa/iniciar a mineração (BlockDamageEvent). O evento é cancelado para
    // o vidro permanecer intacto (inquebrável, sem drop). Prioridade LOWEST + ignoreCancelled
    // = false para rodar antes das proteções de região/arena e ainda assim registrar a
    // tentativa legítima. Só conta quem está fisicamente dentro (validado no CageManager).
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Cage cage = mgr().getCageByBlock(event.getBlock());
        if (cage == null) return;

        // Vidro individualmente inquebrável: nunca some, nunca dropa, nunca vira ar.
        event.setCancelled(true);
        event.setDropItems(false);

        Player p = event.getPlayer();
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return; // não conta

        mgr().tryRegisterAttempt(cage, p);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> mgr().getCageByBlock(b) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> mgr().getCageByBlock(b) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (mgr().getCageByBlock(b) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (mgr().getCageByBlock(b) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        if (mgr().getCageByBlock(event.getToBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (mgr().getCageByBlock(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (mgr().getCageByBlock(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (mgr().getCageByBlock(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (mgr().getCageByBlock(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    // Endermen / outras entidades mexendo em blocos.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (mgr().getCageByBlock(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    // ───────────────────── Fronteira: teleporte e movimento ────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (!mgr().wouldCrossCageBoundary(from, to)) return;

        event.setCancelled(true);
        Player p = event.getPlayer();
        long now = System.currentTimeMillis();
        Long last = tpMsgCooldown.get(p.getUniqueId());
        if (last == null || now - last > TP_MSG_COOLDOWN_MS) {
            tpMsgCooldown.put(p.getUniqueId(), now);
            p.sendActionBar(mm.deserialize("<#e22c27>Você não pode atravessar a Jaula!"));
        }
    }

    // Rede de segurança contra glitches: só processa jogadores rastreados como presos.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;
        Player p = event.getPlayer();
        UUID cageId = mgr().getTrackedCage(p);
        if (cageId == null) return; // caminho rápido para a esmagadora maioria

        Cage cage = mgr().getCage(cageId);
        if (cage == null || !cage.isActive()) {
            mgr().untrack(p);
            return;
        }
        // Se um preso saiu do interior (glitch/knockback), puxa de volta.
        if (!cage.isInsideInterior(to)) {
            event.setTo(event.getFrom());
        }
    }

    // ───────────────────── Saída de jogadores / limpeza ───────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        mgr().handlePlayerRemoved(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        mgr().handlePlayerRemoved(event.getEntity());
    }
}
