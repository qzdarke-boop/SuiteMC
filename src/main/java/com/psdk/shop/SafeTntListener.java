package com.psdk.shop;

import com.psdk.PSDK;
import com.psdk.region.RegionFlag;
import com.psdk.thepit.ArenaManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * TNT especial da loja. Ao usar (clique direito num bloco) ela é "arremessada"
 * já ativada (vira uma {@link TNTPrimed} marcada) e explode causando dano e
 * destruindo os blocos ao redor — EXCETO a estrutura protegida da arena.
 *
 * Características:
 *  • Pode ser usada mesmo que haja uma entidade/jogador ocupando o espaço (ela é
 *    spawnada como entidade, não colocada como bloco) — inclusive no próprio pé.
 *  • Dano REAL escalado pela distância, ignorando armadura/proteção (ver onTntDamage).
 *  • Cooldown estilo escudo: o item mostra a animação de recarga no inventário
 *    ({@link Player#setCooldown}) e não dá pra spammar bloco fantasma no chão.
 */
public class SafeTntListener implements Listener {

    private static final String PDC_KEY = "safe_tnt";
    private static final int FUSE_TICKS = 45;            // ~2.25s -> um pouquinho mais de tempo pra explodir
    // Cooldown (4s) centralizado em AbilityCooldownManager.Ability.SAFE_TNT.

    private final PSDK plugin;
    private final NamespacedKey key;

    public SafeTntListener(PSDK plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, PDC_KEY);
    }

    /** Marca um ItemStack de TNT como "TNT da loja" (chamado na compra da loja). */
    public static void tagItem(PSDK plugin, ItemStack item) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, PDC_KEY), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }

    // Uso da TNT (clique direito num bloco). Spawna a TNT ativada como entidade,
    // então funciona mesmo se houver entidade/jogador ocupando o lugar.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return; // só a mão principal
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;

        ItemStack hand = event.getItem();
        if (hand == null || hand.getType() != Material.TNT) return;
        ItemMeta meta = hand.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) return;

        final Player player = event.getPlayer();

        // Impede a colocação vanilla do bloco de TNT (nada de bloco fantasma).
        event.setUseItemInHand(Event.Result.DENY);

        // Cooldown estilo escudo (centralizado): enquanto recarrega, não faz nada
        // (o item já mostra a animação de recarga na hotbar).
        if (!plugin.getAbilityCooldownManager().isReady(
                player, com.psdk.pitems.AbilityCooldownManager.Ability.SAFE_TNT)) return;

        // Precisa de um bloco de referência pra saber onde "arremessar".
        if (action != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        // Mesma lógica de colocação vanilla (face clicada -> bloco vizinho), mas como
        // é uma entidade ela nasce mesmo com jogador/entidade ocupando o espaço.
        Block target = clicked.getRelative(event.getBlockFace());
        Location c = target.getLocation().add(0.5, 0, 0.5);

        // BLOQUEIO EM ÁREA SEGURA/SPAWN: não deixa arremessar a TNT se o jogador OU o
        // ponto onde ela nasceria estiver numa região protegida (PvP/explosões off).
        if (isSafeArea(c) || isSafeArea(player.getLocation())) {
            player.sendActionBar(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize("<#e22c27>Você não pode usar TNT na área segura!"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
            return; // não gasta a TNT nem aplica cooldown
        }

        TNTPrimed tnt = target.getWorld().spawn(c, TNTPrimed.class, t -> {
            t.setFuseTicks(FUSE_TICKS);
            t.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        });
        tnt.setSource(player); // crédito de dano/kill no PvP
        target.getWorld().playSound(c, Sound.ENTITY_TNT_PRIMED, 1f, 1f);

        // Consome 1 TNT da mão (criativo não gasta).
        if (player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }

        // Recarga visual no inventário, igual ao escudo quando quebra e volta (centralizado).
        plugin.getAbilityCooldownManager().start(
                player, com.psdk.pitems.AbilityCooldownManager.Ability.SAFE_TNT);
    }

    /** Força da explosão da TNT da loja (padrão da TNT é 4.0). */
    private static final float EXPLOSION_POWER = 7.0f;   // explosão GIGANTE

    /** Dano máximo (no epicentro) que a TNT aplica IGNORANDO armadura/proteção. */
    private static final double MAX_DAMAGE = 14.0;
    /** Alcance (blocos) onde ainda há dano; além disso o dano é zero. */
    private static final double DAMAGE_RANGE = EXPLOSION_POWER * 2.0; // ~14 blocos

    // Define o raio (potência) da explosão da TNT da loja.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPrime(org.bukkit.event.entity.ExplosionPrimeEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) return;
        if (!tnt.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) return;
        event.setRadius(EXPLOSION_POWER);
    }

    // Dano da TNT da loja: substitui o dano vanilla (que a armadura/Blast Protection
    // quase zera no full netherite) por dano REAL que ignora armadura e proteção,
    // escalando com a distância — quanto mais perto, mais dano; longe, quase nada.
    // Obs.: se um bloco bloqueia totalmente a visão, o evento nem dispara (raytrace
    // vanilla), então quem se protege com um bloco na frente não toma dano.
    // HIGHEST: roda depois do redutor de dano da arena (TntListener, HIGH) para
    // que o dano real da TNT da loja prevaleça mesmo dentro da arena.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTntDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof TNTPrimed tnt)) return;
        if (!tnt.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        // Nunca dá dano dentro da área segura/spawn (a proteção de PvP das regiões ignora
        // TNT, então precisamos cancelar aqui explicitamente).
        if (isSafeArea(victim.getLocation())) { event.setCancelled(true); return; }

        double dist = victim.getLocation().distance(tnt.getLocation());
        double factor = 1.0 - (dist / DAMAGE_RANGE);
        if (factor <= 0) { event.setCancelled(true); return; }
        if (factor > 1) factor = 1;

        double dmg = MAX_DAMAGE * factor;

        // Zera a redução por armadura e por encantamentos de proteção -> dano "puro".
        // Absorção e Resistência continuam valendo (balanceamento justo).
        for (EntityDamageEvent.DamageModifier mod : EntityDamageEvent.DamageModifier.values()) {
            if (mod == EntityDamageEvent.DamageModifier.ARMOR
                    || mod == EntityDamageEvent.DamageModifier.MAGIC) {
                if (event.isApplicable(mod)) {
                    try { event.setDamage(mod, 0.0); } catch (UnsupportedOperationException ignored) {}
                }
            }
        }
        event.setDamage(EntityDamageEvent.DamageModifier.BASE, dmg);
    }

    /**
     * Área segura = região onde PvP ou explosões estão desativados (spawn/lobby etc.).
     * A TNT da loja não pode ser usada nem causar dano nesses lugares.
     */
    private boolean isSafeArea(Location loc) {
        if (loc == null) return false;
        var rm = plugin.getRegionManager();
        if (rm == null) return false;
        return !rm.isAllowed(loc, RegionFlag.PVP) || !rm.isAllowed(loc, RegionFlag.EXPLOSIONS);
    }

    // Explosão da TNT da loja -> destrói os blocos ao redor, EXCETO a estrutura
    // protegida da arena (blocos do snapshot dentro da arena são preservados).
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) return;
        if (!tnt.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) return;

        ArenaManager arena = plugin.getArenaManager();
        // Mantém na lista (vai destruir) tudo, menos blocos estruturais da arena.
        event.blockList().removeIf(b ->
                arena.isInsideArena(b.getLocation())
                        && !arena.isPlayerPlaced(b.getX(), b.getY(), b.getZ()));
    }
}
