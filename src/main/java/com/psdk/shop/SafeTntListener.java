package com.psdk.shop;

import com.psdk.PSDK;
import com.psdk.region.RegionFlag;
import com.psdk.thepit.ArenaManager;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.minimessage.MiniMessage;

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

    /**
     * Factory oficial: cria uma nova instância da TNT do /shop já identificada pelo PDC.
     * A compra deve usar isto (ou {@link #tagItem}) — nunca um {@code new ItemStack(TNT)} cru.
     */
    public static ItemStack create(PSDK plugin, int amount) {
        ItemStack item = new ItemStack(Material.TNT, Math.max(1, amount));
        tagItem(plugin, item);
        return item;
    }

    // ─────────────────────────── Identificação central ─────────────────────────
    // ÚNICO ponto de verdade. Valida SEMPRE o identificador interno (PDC), nunca o
    // material/nome/lore/brilho — uma TNT vanilla renomeada ou com a lore copiada NÃO passa.

    /** True se o item é a TNT oficial do /shop (PDC interno). */
    public boolean isShopTnt(ItemStack item) {
        if (item == null || item.getType() != Material.TNT) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** True se a entidade é uma TNT primada originada da TNT do /shop (marcada no spawn). */
    public boolean isShopTntEntity(Entity entity) {
        return entity instanceof TNTPrimed tnt
                && tnt.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /**
     * Mundo do Skill Pit onde a regra "só a TNT do /shop funciona" vale (a arena de PvP).
     * Se a arena ainda não foi definida, não aplica restrições (evita afetar setup/outros mundos).
     */
    private boolean isSkillPitWorld(World world) {
        if (world == null) return false;
        World arena = plugin.getArenaManager().getCachedWorld();
        return arena != null && world.equals(arena);
    }

    // Uso da TNT (clique direito num bloco). Spawna a TNT ativada como entidade,
    // então funciona mesmo se houver entidade/jogador ocupando o lugar.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return; // só a mão principal
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;

        ItemStack hand = event.getItem();
        // Identificação SEMPRE pelo PDC (factory oficial) — TNT vanilla nunca passa daqui.
        if (!isShopTnt(hand)) return;

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

    // ───────────── Bloqueio TOTAL de TNT vanilla no Skill Pit ──────────────────
    // A TNT do /shop NUNCA vira um bloco: ela nasce direto como entidade primada
    // identificada (ver onUse). Logo, qualquer TNT como BLOCO ou como entidade SEM o
    // PDC oficial é vanilla (Criativo, /give, dispenser, redstone, cadeia, /summon,
    // mapa, plugin…) e não pode ser colocada, primada nem explodir. Regras baseadas
    // SOMENTE no identificador interno, nunca na origem presumida. Valem inclusive p/ OP.

    /** Ninguém coloca bloco de TNT na arena (a do /shop é entidade, não bloco). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.TNT) return;
        if (!isSkillPitWorld(event.getBlock().getWorld())) return;
        event.setCancelled(true); // não coloca, não consome
        event.getPlayer().sendActionBar(MiniMessage.miniMessage()
                .deserialize("<#e22c27>Só a TNT da loja pode ser usada aqui!"));
    }

    /** Bloco de TNT vanilla nunca prima (pederneira, fogo, lava, redstone, botão, dispenser,
     *  observador, flecha flamejante, outra explosão…). Bloco preservado, sem drop/duplicação. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTntPrime(TNTPrimeEvent event) {
        if (!isSkillPitWorld(event.getBlock().getWorld())) return;
        event.setCancelled(true);
    }

    /** Entidade TNTPrimed sem identidade do /shop nunca chega a existir na arena
     *  (dispenser, /summon, cadeia, plugins…). A do /shop já tem o PDC setado no spawn. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTntSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) return;
        if (isShopTntEntity(tnt)) return;
        if (!isSkillPitWorld(tnt.getWorld())) return;
        event.setCancelled(true);
    }

    /** Dispenser não dispensa/prima TNT na arena. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (event.getItem().getType() != Material.TNT) return;
        if (!isSkillPitWorld(event.getBlock().getWorld())) return;
        event.setCancelled(true);
    }

    // Potência da explosão da TNT do /shop + trava final: TNTPrimed sem identidade oficial
    // não explode (rede de segurança caso escape do onTntSpawn).
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPrime(ExplosionPrimeEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) return;
        if (isShopTntEntity(tnt)) {
            event.setRadius(EXPLOSION_POWER);
            return;
        }
        // TNT primada vanilla: cancela a explosão, sem fogo, e remove a entidade.
        if (isSkillPitWorld(tnt.getWorld())) {
            event.setCancelled(true);
            event.setFire(false);
            tnt.remove();
        }
    }

    // Dano da TNT da loja: substitui o dano vanilla (que a armadura/Blast Protection
    // quase zera no full netherite) por dano REAL que ignora armadura e proteção,
    // escalando com a distância — quanto mais perto, mais dano; longe, quase nada.
    // O dano é escalado pela EXPOSIÇÃO real à explosão (linha de visão): blocos sólidos
    // entre a TNT e o jogador reduzem o dano e, se cobrem totalmente, o ANULAM. Sem isto,
    // o dano por distância atravessaria paredes/vidros (era o bug de "TNT atravessando").
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

        final Location boom = tnt.getLocation();

        // (1) JAULA: se a explosão e o jogador estão em lados opostos de uma Jaula ativa,
        //     a estrutura contém a explosão por completo — bloqueio total, ANTES do Escudo
        //     (a Jaula protege mesmo sem escudo). Verificação geométrica pelo gerenciador
        //     real de Jaulas (não depende do raytrace, que vazaria pelas quinas). Feita AQUI,
        //     na própria rota que aplica o dano, para nunca escapar por ordem de eventos.
        if (victim instanceof Player caged
                && plugin.getCageManager() != null
                && plugin.getCageManager().isSeparatedByActiveCage(boom, caged)) {
            event.setCancelled(true);
            return;
        }

        // (2) LINHA DE VISÃO: bloco sólido entre a explosão e o jogador (parede, teto, piso,
        //     vidro, qualquer bloco na frente) reduz o dano e, se cobre totalmente, o ANULA.
        double exposure = explosionExposure(boom, victim);
        if (exposure <= 0.0) { event.setCancelled(true); return; }

        // (3) ESCUDO: defendendo (escudo levantado, não quebrado/em recarga) e virado para a
        //     explosão → bloqueio TOTAL. Mesma regra dentro e fora da Jaula. Escudo quebrado
        //     (isBlocking() == false) NÃO bloqueia. Escudo virado para o lado errado idem.
        if (victim instanceof Player shielder && isBlockingExplosion(shielder, boom)) {
            event.setCancelled(true);
            return;
        }

        double dist = victim.getLocation().distance(boom);
        double factor = 1.0 - (dist / DAMAGE_RANGE);
        if (factor <= 0) { event.setCancelled(true); return; }
        if (factor > 1) factor = 1;

        double dmg = MAX_DAMAGE * factor * exposure;

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

    /**
     * Fração [0..1] do corpo do alvo que a explosão "enxerga" sem obstrução por blocos
     * sólidos — mesma ideia do cálculo de exposição da explosão vanilla, mas próprio,
     * porque o dano da TNT da loja é por distância e, sozinho, ignoraria paredes.
     *
     * <p>Traça raios do centro da explosão até uma grade de pontos amostrais na bounding
     * box do alvo. Blocos passáveis (ar, grama, tochas…) são ignorados; qualquer bloco
     * sólido (parede, teto, piso, VIDRO da Jaula, bloco colocado na frente) obstrui.
     *
     * @return 1.0 = totalmente exposto; 0.0 = totalmente coberto (nenhum dano).
     */
    private double explosionExposure(Location source, LivingEntity victim) {
        World world = victim.getWorld();
        if (source == null || source.getWorld() == null || !source.getWorld().equals(world)) {
            return 1.0;
        }
        BoundingBox box = victim.getBoundingBox();
        final double[] frac = {0.05, 0.5, 0.95};
        int visible = 0, total = 0;
        for (double fx : frac) {
            for (double fy : frac) {
                for (double fz : frac) {
                    total++;
                    double tx = box.getMinX() + (box.getMaxX() - box.getMinX()) * fx;
                    double ty = box.getMinY() + (box.getMaxY() - box.getMinY()) * fy;
                    double tz = box.getMinZ() + (box.getMaxZ() - box.getMinZ()) * fz;
                    Vector dir = new Vector(tx - source.getX(), ty - source.getY(), tz - source.getZ());
                    double d = dir.length();
                    if (d < 1.0E-4) { visible++; continue; }
                    // ignorePassableBlocks=true → só blocos sólidos (inclui vidro) obstruem.
                    RayTraceResult res = world.rayTraceBlocks(
                            source, dir.normalize(), d, FluidCollisionMode.NEVER, true);
                    if (res == null || res.getHitBlock() == null) visible++;
                }
            }
        }
        return total == 0 ? 1.0 : (double) visible / total;
    }

    /**
     * True se o jogador está DEFENDENDO com escudo (levantado, não quebrado/em recarga)
     * e virado para a explosão — caso em que o Escudo bloqueia o dano por completo.
     *
     * <p>Usa {@link Player#isBlocking()} (false quando o escudo está quebrado/desabilitado
     * por machado ou ainda não levantado) + a MESMA checagem direcional do escudo vanilla:
     * a fonte precisa estar no arco frontal (produto escalar no plano horizontal). Assim a
     * regra é idêntica dentro e fora da Jaula: de frente bloqueia, de costas/lado não.
     */
    private boolean isBlockingExplosion(Player player, Location source) {
        if (!player.isBlocking()) return false;
        Vector view = player.getEyeLocation().getDirection();
        Vector toSource = player.getLocation().toVector().subtract(source.toVector());
        toSource.setY(0);
        if (toSource.lengthSquared() < 1.0E-6) return true; // explosão na mesma coluna
        toSource.normalize();
        return toSource.dot(view) < 0.0;
    }

    // Explosão da TNT da loja -> destrói os blocos ao redor, EXCETO a estrutura
    // protegida da arena (blocos do snapshot dentro da arena são preservados).
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) return;

        // Trava final: TNT primada sem identidade do /shop não destrói nada na arena
        // (não quebra blocos, sem dano/knockback/cadeia) e é removida.
        if (!isShopTntEntity(tnt)) {
            if (isSkillPitWorld(tnt.getWorld())) {
                event.setCancelled(true);
                event.blockList().clear();
                tnt.remove();
            }
            return;
        }

        ArenaManager arena = plugin.getArenaManager();
        // Mantém na lista (vai destruir) tudo, menos blocos estruturais da arena.
        event.blockList().removeIf(b ->
                arena.isInsideArena(b.getLocation())
                        && !arena.isPlayerPlaced(b.getX(), b.getY(), b.getZ()));
    }
}
