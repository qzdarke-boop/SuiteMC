package com.psdk.region;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import com.psdk.PSDK;
import com.psdk.pitems.AbilityCooldownManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.*;

public class RegionProtectionListener implements Listener {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private static final Set<Material> INTERACTABLE = EnumSet.of(
            Material.OAK_DOOR, Material.SPRUCE_DOOR, Material.BIRCH_DOOR, Material.JUNGLE_DOOR,
            Material.ACACIA_DOOR, Material.DARK_OAK_DOOR, Material.MANGROVE_DOOR, Material.CHERRY_DOOR,
            Material.BAMBOO_DOOR, Material.CRIMSON_DOOR, Material.WARPED_DOOR, Material.COPPER_DOOR,
            Material.EXPOSED_COPPER_DOOR, Material.WEATHERED_COPPER_DOOR, Material.OXIDIZED_COPPER_DOOR,
            Material.WAXED_COPPER_DOOR, Material.WAXED_EXPOSED_COPPER_DOOR, Material.WAXED_WEATHERED_COPPER_DOOR,
            Material.WAXED_OXIDIZED_COPPER_DOOR, Material.IRON_DOOR,
            Material.OAK_TRAPDOOR, Material.SPRUCE_TRAPDOOR, Material.BIRCH_TRAPDOOR, Material.JUNGLE_TRAPDOOR,
            Material.ACACIA_TRAPDOOR, Material.DARK_OAK_TRAPDOOR, Material.MANGROVE_TRAPDOOR, Material.CHERRY_TRAPDOOR,
            Material.BAMBOO_TRAPDOOR, Material.CRIMSON_TRAPDOOR, Material.WARPED_TRAPDOOR, Material.COPPER_TRAPDOOR,
            Material.EXPOSED_COPPER_TRAPDOOR, Material.WEATHERED_COPPER_TRAPDOOR, Material.OXIDIZED_COPPER_TRAPDOOR,
            Material.WAXED_COPPER_TRAPDOOR, Material.WAXED_EXPOSED_COPPER_TRAPDOOR, Material.WAXED_WEATHERED_COPPER_TRAPDOOR,
            Material.WAXED_OXIDIZED_COPPER_TRAPDOOR, Material.IRON_TRAPDOOR,
            Material.OAK_FENCE_GATE, Material.SPRUCE_FENCE_GATE, Material.BIRCH_FENCE_GATE,
            Material.JUNGLE_FENCE_GATE, Material.ACACIA_FENCE_GATE, Material.DARK_OAK_FENCE_GATE,
            Material.MANGROVE_FENCE_GATE, Material.CHERRY_FENCE_GATE, Material.BAMBOO_FENCE_GATE,
            Material.CRIMSON_FENCE_GATE, Material.WARPED_FENCE_GATE,
            Material.STONE_BUTTON, Material.OAK_BUTTON, Material.SPRUCE_BUTTON, Material.BIRCH_BUTTON,
            Material.JUNGLE_BUTTON, Material.ACACIA_BUTTON, Material.DARK_OAK_BUTTON, Material.MANGROVE_BUTTON,
            Material.CHERRY_BUTTON, Material.BAMBOO_BUTTON, Material.CRIMSON_BUTTON, Material.WARPED_BUTTON,
            Material.POLISHED_BLACKSTONE_BUTTON,
            Material.LEVER,
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
            Material.HOPPER, Material.DISPENSER, Material.DROPPER,
            Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.BREWING_STAND, Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL,
            Material.ENCHANTING_TABLE, Material.CRAFTING_TABLE,
            Material.CARTOGRAPHY_TABLE, Material.STONECUTTER, Material.LOOM,
            Material.GRINDSTONE, Material.SMITHING_TABLE
    );

    // ── Bloqueio global de coleta de água/lava (item configurável) ──────────────
    /** Liga/desliga o bloqueio de coletar água/lava com balde nos mundos do Skill Pit. */
    private static final boolean BLOCK_LIQUID_PICKUP = true;
    /** Mensagem exibida ao tentar coletar (padrão visual do projeto). */
    private static final String LIQUID_PICKUP_MSG = "<#e22c27>Você não pode coletar água ou lava neste modo!";
    /** Anti-spam da mensagem enquanto o jogador segura o botão. */
    private static final long LIQUID_MSG_COOLDOWN_MS = 1500L;

    private final PSDK plugin;
    private final Map<UUID, Set<String>> playerRegions = new HashMap<>();
    private final Map<UUID, Long> liquidMsgCooldown = new HashMap<>();
    /** Marca projéteis disparados de dentro de uma área segura (não podem dar dano). */
    private final NamespacedKey safeShotKey;

    public RegionProtectionListener(PSDK plugin) {
        this.plugin = plugin;
        this.safeShotKey = new NamespacedKey(plugin, "shot_in_safezone");
        startRegionTracker();
    }

    // Evita vazamento: tira o jogador dos mapas quando ele sai.
    @EventHandler
    public void onQuitClearRegions(PlayerQuitEvent event) {
        playerRegions.remove(event.getPlayer().getUniqueId());
        liquidMsgCooldown.remove(event.getPlayer().getUniqueId());
    }

    private RegionManager rm() {
        return plugin.getRegionManager();
    }

    private boolean bypass(Player player) {
        return player.hasPermission("psdk.region.bypass");
    }

    // ---- Block Break ----
    // LOWEST: a proteção precisa cancelar ANTES dos listeners de gameplay (ex.:
    // BlockMineListener e a Picareta Infernal dão coins em HIGH com ignoreCancelled).
    // Se rodasse junto deles (mesma prioridade), a ordem de registro fazia os coins
    // serem dados antes do cancelamento -> dava pra ganhar dinheiro de graça no
    // mesmo minério dentro de uma região com BLOCK_BREAK negado.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (bypass(event.getPlayer())) return;
        if (!rm().isAllowed(event.getBlock().getLocation(), RegionFlag.BLOCK_BREAK)) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(mm.deserialize("<#e22c27>Você não pode quebrar blocos aqui!"));
        }
    }

    // ---- Block Place ----
    // LOWEST pelo mesmo motivo: cancelar antes de qualquer listener de gameplay.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (bypass(event.getPlayer())) return;
        if (!rm().isAllowed(event.getBlock().getLocation(), RegionFlag.BLOCK_PLACE)) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(mm.deserialize("<#e22c27>Você não pode colocar blocos aqui!"));
        }
    }

    // ---- PVP ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = resolvePlayerAttacker(event);
        if (attacker == null) return;
        if (bypass(attacker)) return;

        // Se AMBOS já estão em combate, a luta CONTINUA mesmo que um (ou os dois) tenha
        // entrado na área segura — não dá pra fugir do PvP fugindo pro spawn. Nesse caso o
        // golpe é permitido e o combate será RENOVADO pelo CombatListener (combat-log sobe).
        // Assim que o combate expira dentro da área segura, o PvP volta a ser bloqueado.
        boolean bothInCombat = plugin.getCombatManager().isInCombat(attacker)
                && plugin.getCombatManager().isInCombat(victim);

        // ANTI-EXPLOIT DE SPAWN: quem está (ou disparou) de DENTRO da área segura não pode
        // causar dano a ninguém, nem mesmo em quem está fora. Cobre soco no limite do spawn
        // e flechas/projéteis atiradas de dentro do spawn. EXCEÇÃO: dupla já em combate.
        boolean attackerSafe;
        if (event.getDamager() instanceof Projectile proj) {
            // Projétil: usa a marcação feita no disparo (posição de quem atirou).
            attackerSafe = proj.getPersistentDataContainer().has(safeShotKey, PersistentDataType.BYTE)
                    || !rm().isAllowed(attacker.getLocation(), RegionFlag.PVP);
        } else {
            attackerSafe = !rm().isAllowed(attacker.getLocation(), RegionFlag.PVP);
        }
        if (attackerSafe && !bothInCombat) {
            event.setCancelled(true);
            attacker.sendActionBar(mm.deserialize("<#e22c27>Você não pode causar dano estando na área segura!"));
            return;
        }

        // Flag de flecha/projétil: bloqueia dano de arco/tridente/etc. na região (independente do PvP).
        if (event.getDamager() instanceof Projectile
                && !rm().isAllowed(victim.getLocation(), RegionFlag.PROJECTILES)) {
            event.setCancelled(true);
            attacker.sendActionBar(mm.deserialize("<#e22c27>Flechas/projéteis estão desativados nesta região!"));
            return;
        }
        if (!rm().isAllowed(victim.getLocation(), RegionFlag.PVP)) {
            // Exceção: se AMBOS já estão em combate, o PvP CONTINUA mesmo na zona segura
            // (não dá pra fugir do combate entrando numa área sem PvP). Vale pra todos os envolvidos:
            // qualquer dupla que esteja em combate pode se atacar dentro da área.
            if (bothInCombat) return;
            event.setCancelled(true);
            attacker.sendActionBar(mm.deserialize("<#e22c27>PvP está desativado nesta região!"));
        }
    }

    // Marca todo projétil disparado por um jogador que está numa área segura (PvP off).
    // Assim, mesmo que a flecha acerte alguém fora do spawn, o dano é cancelado.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunchSafezone(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player shooter)) return;
        if (bypass(shooter)) return;
        if (!rm().isAllowed(shooter.getLocation(), RegionFlag.PVP)) {
            event.getEntity().getPersistentDataContainer().set(safeShotKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    private Player resolvePlayerAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) return p;
        if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    // ---- Vara de pesca (anti-puxão da/para a área segura) ----
    // Só é possível fisgar OUTRO jogador se o pescador E o alvo estiverem fora da área
    // segura (ou seja, na arena). Bloqueia: puxar alguém que está no spawn, e pescar
    // estando no spawn (puxando quem está fora pra dentro/fora).
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFishPlayer(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) return;
        if (!(event.getCaught() instanceof Player target)) return;
        Player fisher = event.getPlayer();
        if (bypass(fisher)) return;

        boolean fisherSafe = !rm().isAllowed(fisher.getLocation(), RegionFlag.PVP);
        boolean targetSafe = !rm().isAllowed(target.getLocation(), RegionFlag.PVP);
        if (fisherSafe || targetSafe) {
            event.setCancelled(true);
            fisher.sendActionBar(mm.deserialize("<#e22c27>Você só pode usar a vara de pesca em jogadores na arena!"));
        }
    }

    // ---- Fall Damage ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!rm().isAllowed(player.getLocation(), RegionFlag.FALL_DAMAGE)) {
            event.setCancelled(true);
        }
    }

    // ---- Mob Damage ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (event.getDamager() instanceof Player) return;
        if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player) return;

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
                && cause != EntityDamageEvent.DamageCause.PROJECTILE) return;

        if (!rm().isAllowed(victim.getLocation(), RegionFlag.MOB_DAMAGE)) {
            event.setCancelled(true);
        }
    }

    // ---- Explosions ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> !rm().isAllowed(block.getLocation(), RegionFlag.EXPLOSIONS));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> !rm().isAllowed(block.getLocation(), RegionFlag.EXPLOSIONS));
    }

    // ---- Mob Spawn ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason != CreatureSpawnEvent.SpawnReason.NATURAL && reason != CreatureSpawnEvent.SpawnReason.SPAWNER) return;
        if (!rm().isAllowed(event.getLocation(), RegionFlag.MOB_SPAWN)) {
            event.setCancelled(true);
        }
    }

    // ---- Interact ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!INTERACTABLE.contains(block.getType())) return;
        if (bypass(event.getPlayer())) return;
        if (!rm().isAllowed(block.getLocation(), RegionFlag.INTERACT)) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(mm.deserialize("<#e22c27>Você não pode interagir com isso aqui!"));
        }
    }

    // ---- Item Drop ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (bypass(event.getPlayer())) return;
        if (!rm().isAllowed(event.getPlayer().getLocation(), RegionFlag.ITEM_DROP)) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(mm.deserialize("<#e22c27>Você não pode dropar itens aqui!"));
        }
    }

    // ---- Commands ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (bypass(player)) return;
        String msg = event.getMessage().toLowerCase();
        if (msg.startsWith("/regiao") || msg.startsWith("/regiao ")) return;
        if (!rm().isAllowed(player.getLocation(), RegionFlag.COMMANDS)) {
            event.setCancelled(true);
            player.sendActionBar(mm.deserialize("<#e22c27>Você não pode usar comandos nesta região!"));
        }
    }

    // ---- Fire Spread ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (!rm().isAllowed(event.getBlock().getLocation(), RegionFlag.FIRE_SPREAD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (event.getSource().getType() != Material.FIRE) return;
        if (!rm().isAllowed(event.getBlock().getLocation(), RegionFlag.FIRE_SPREAD)) {
            event.setCancelled(true);
        }
    }

    // ---- Pistons ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (isPistonBlockedByRegion(event.getBlocks())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (isPistonBlockedByRegion(event.getBlocks())) event.setCancelled(true);
    }

    private boolean isPistonBlockedByRegion(List<Block> blocks) {
        for (Block block : blocks) {
            if (!rm().isAllowed(block.getLocation(), RegionFlag.PISTONS)) return true;
        }
        return false;
    }

    // ---- Liquid Flow ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        Material type = event.getBlock().getType();
        if (type != Material.WATER && type != Material.LAVA) return;
        if (!rm().isAllowed(event.getToBlock().getLocation(), RegionFlag.LIQUID_FLOW)) {
            event.setCancelled(true);
        }
    }

    // ---- Colocar líquido com balde (água/lava no chão) ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (bypass(event.getPlayer())) return;
        Location loc = event.getBlock().getLocation();
        // Bloqueia água/lava onde o flag LIQUID_PLACE estiver negado OU em zona
        // segura (PvP desligado): ninguém pode despejar líquido na área segura.
        if (!rm().isAllowed(loc, RegionFlag.LIQUID_PLACE) || !rm().isAllowed(loc, RegionFlag.PVP)) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(mm.deserialize("<#e22c27>Você não pode colocar água/lava aqui!"));
        }
    }

    // ---- Coletar líquido com balde (água/lava) — BLOQUEIO GLOBAL no Skill Pit ----
    // Impede a etapa "fabricar balde → pegar líquido do mapa → escapar da arena".
    // Vale em QUALQUER local dos mundos do Skill Pit (spawn, área segura, arena, fora dela,
    // cavernas, montanhas, etc.), independente de região/flag. NÃO usa bypass: nenhum jogador
    // pode obter WATER_BUCKET/LAVA_BUCKET coletando do mundo (staff usa /give se precisar).
    // Baldes de leite NÃO disparam este evento, então não são afetados. Cobre também
    // caldeirões de água/lava (mesmo evento). Só age em mundos do Skill Pit — não afeta
    // outros mundos que compartilhem a instância. LOWEST para bloquear antes de tudo.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!BLOCK_LIQUID_PICKUP) return;
        Player player = event.getPlayer();
        if (!plugin.isSkillPitWorld(player.getWorld())) return;

        // Só água e lava (não bloqueia powder snow nem usos legítimos).
        Material result  = event.getItemStack()   != null ? event.getItemStack().getType()   : null;
        Material clicked = event.getBlockClicked() != null ? event.getBlockClicked().getType() : null;
        boolean waterOrLava =
                result == Material.WATER_BUCKET || result == Material.LAVA_BUCKET
                || clicked == Material.WATER || clicked == Material.LAVA
                || clicked == Material.WATER_CAULDRON || clicked == Material.LAVA_CAULDRON
                || clicked == Material.BUBBLE_COLUMN;
        if (!waterOrLava) return;

        event.setCancelled(true);
        // Anti-desync: cliente volta a ver o balde vazio e o líquido no lugar; sem dupe.
        player.updateInventory();

        long now = System.currentTimeMillis();
        Long last = liquidMsgCooldown.get(player.getUniqueId());
        if (last == null || now - last > LIQUID_MSG_COOLDOWN_MS) {
            liquidMsgCooldown.put(player.getUniqueId(), now);
            player.sendActionBar(mm.deserialize(LIQUID_PICKUP_MSG));
        }
    }

    // ---- Tree Growth ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        if (!rm().isAllowed(event.getLocation(), RegionFlag.TREE_GROWTH)) {
            event.setCancelled(true);
        }
    }

    // ---- Ender Pearl ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnderPearlLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.EnderPearl pearl)) return;
        if (!(pearl.getShooter() instanceof Player player)) return;
        if (bypass(player)) return;
        if (!rm().isAllowed(player.getLocation(), RegionFlag.ENDERPEARL)) {
            event.setCancelled(true);
            player.sendActionBar(mm.deserialize("<#e22c27>Ender pearls estão desativadas nesta região!"));
        }
    }

    // Cooldown de 6s da Ender Pearl (padrão visual do escudo, via Player#setCooldown no
    // AbilityCooldownManager). Usa PlayerLaunchProjectileEvent (Paper) para poder NÃO
    // consumir a pearl quando o uso é bloqueado, e roda ANTES do ProjectileLaunchEvent —
    // então o cooldown só inicia num lançamento realmente aceito.
    //
    // IMPORTANTE: NÃO usar bypass(player) aqui. bypass() = psdk.region.bypass, que por
    // padrão é 'op' — se retornássemos cedo, TODO operador pularia o handler e ficava sem
    // cooldown (era o bug). O bypass de OP é APENAS de tempo e fica centralizado no
    // AbilityCooldownManager (isReady/start checam isOp). Assim o OP continua respeitando
    // a região/área segura das pearls e só ignora a espera.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnderPearlCooldown(PlayerLaunchProjectileEvent event) {
        if (!(event.getProjectile() instanceof org.bukkit.entity.EnderPearl)) return;
        Player player = event.getPlayer();

        // Região onde ender pearls estão desativadas: bloqueia SEM consumir (vale p/ OP também).
        if (!rm().isAllowed(player.getLocation(), RegionFlag.ENDERPEARL)) {
            event.setShouldConsume(false);
            event.setCancelled(true);
            player.sendActionBar(mm.deserialize("<#e22c27>Ender pearls estão desativadas nesta região!"));
            return;
        }

        // Cooldown ativo: bloqueia SEM consumir e SEM mensagem (a recarga já aparece no item).
        // Para OP, isReady() é sempre true → nunca cai aqui.
        AbilityCooldownManager cd = plugin.getAbilityCooldownManager();
        if (!cd.isReady(player, AbilityCooldownManager.Ability.ENDER_PEARL)) {
            event.setShouldConsume(false);
            event.setCancelled(true);
            return;
        }

        // Lançamento aceito → inicia o cooldown de 6s (start() é no-op para OP).
        cd.start(player, AbilityCooldownManager.Ability.ENDER_PEARL);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnderPearlTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;
        Player player = event.getPlayer();
        if (bypass(player)) return;
        if (!rm().isAllowed(player.getLocation(), RegionFlag.ENDERPEARL) ||
            !rm().isAllowed(event.getTo(), RegionFlag.ENDERPEARL)) {
            event.setCancelled(true);
            player.sendActionBar(mm.deserialize("<#e22c27>Ender pearls estão desativadas nesta região!"));
        }
    }

    // ---- Wind Charge ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWindChargeLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.WindCharge charge)) return;
        if (!(charge.getShooter() instanceof Player player)) return;
        if (bypass(player)) return;
        if (!rm().isAllowed(player.getLocation(), RegionFlag.WIND_CHARGE)) {
            event.setCancelled(true);
            player.sendActionBar(mm.deserialize("<#e22c27>Wind charges estão desativadas nesta região!"));
        }
    }

    // ---- Chorus Fruit ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChorusFruit(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.CHORUS_FRUIT) return;
        if (bypass(event.getPlayer())) return;
        if (!rm().isAllowed(event.getPlayer().getLocation(), RegionFlag.CHORUS_FRUIT)) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(mm.deserialize("<#e22c27>Chorus fruit está desativado nesta região!"));
        }
    }

    // ---- Elytra ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onElytraToggle(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!event.isGliding()) return;
        if (bypass(player)) return;
        if (!rm().isAllowed(player.getLocation(), RegionFlag.ELYTRA)) {
            event.setCancelled(true);
            player.sendActionBar(mm.deserialize("<#e22c27>Elytra está desativada nesta região!"));
        }
    }

    // ---- Hunger ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getFoodLevel() >= player.getFoodLevel()) return;
        if (!rm().isAllowed(player.getLocation(), RegionFlag.HUNGER)) {
            event.setCancelled(true);
        }
    }

    // ---- Item Pickup ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (bypass(player)) return;
        if (!rm().isAllowed(player.getLocation(), RegionFlag.ITEM_PICKUP)) {
            event.setCancelled(true);
        }
    }

    // ---- XP Gain ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onXpGain(PlayerExpChangeEvent event) {
        if (!rm().isAllowed(event.getPlayer().getLocation(), RegionFlag.XP_GAIN)) {
            event.setAmount(0);
        }
    }

    // ---- Potion Splash ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (!rm().isAllowed(event.getEntity().getLocation(), RegionFlag.POTION_SPLASH)) {
            event.setCancelled(true);
        }
    }

    // ---- Entity Interact (villagers, item frames) ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (bypass(event.getPlayer())) return;
        if (!rm().isAllowed(event.getRightClicked().getLocation(), RegionFlag.ENTITY_INTERACT)) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(mm.deserialize("<#e22c27>Você não pode interagir com entidades aqui!"));
        }
    }

    // ---- Vehicle Place ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehiclePlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getItem() == null) return;
        Material mat = event.getItem().getType();
        if (mat != Material.OAK_BOAT && mat != Material.SPRUCE_BOAT && mat != Material.BIRCH_BOAT &&
                mat != Material.JUNGLE_BOAT && mat != Material.ACACIA_BOAT && mat != Material.DARK_OAK_BOAT &&
                mat != Material.MANGROVE_BOAT && mat != Material.CHERRY_BOAT && mat != Material.BAMBOO_RAFT &&
                mat != Material.MINECART && mat != Material.CHEST_MINECART && mat != Material.HOPPER_MINECART &&
                mat != Material.TNT_MINECART && mat != Material.FURNACE_MINECART) return;
        if (bypass(event.getPlayer())) return;
        if (!rm().isAllowed(event.getPlayer().getLocation(), RegionFlag.VEHICLE_PLACE)) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(mm.deserialize("<#e22c27>Você não pode colocar veículos aqui!"));
        }
    }

    // ---- Bed Use ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBedUse(PlayerBedEnterEvent event) {
        if (bypass(event.getPlayer())) return;
        if (!rm().isAllowed(event.getBed().getLocation(), RegionFlag.BED_USE)) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(mm.deserialize("<#e22c27>Você não pode usar camas nesta região!"));
        }
    }

    // ---- Ender Chest ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onEnderChest(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ENDER_CHEST) return;
        if (bypass(event.getPlayer())) return;
        if (!rm().isAllowed(block.getLocation(), RegionFlag.ENDER_CHEST)) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(mm.deserialize("<#e22c27>Ender chests estão desativadas nesta região!"));
        }
    }

    // ---- Crop Trample ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCropTrample(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.FARMLAND) return;
        if (bypass(event.getPlayer())) return;
        if (!rm().isAllowed(block.getLocation(), RegionFlag.CROP_TRAMPLE)) {
            event.setCancelled(true);
        }
    }

    // ---- Leaf Decay ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeafDecay(LeavesDecayEvent event) {
        if (!rm().isAllowed(event.getBlock().getLocation(), RegionFlag.LEAF_DECAY)) {
            event.setCancelled(true);
        }
    }

    // ---- Ice Melt ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIceMelt(BlockFadeEvent event) {
        Material type = event.getBlock().getType();
        if (type != Material.ICE && type != Material.FROSTED_ICE && type != Material.PACKED_ICE) return;
        if (!rm().isAllowed(event.getBlock().getLocation(), RegionFlag.ICE_MELT)) {
            event.setCancelled(true);
        }
    }

    // ---- Snow Form ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSnowForm(BlockFormEvent event) {
        if (event.getNewState().getType() != Material.SNOW) return;
        if (!rm().isAllowed(event.getBlock().getLocation(), RegionFlag.SNOW_FORM)) {
            event.setCancelled(true);
        }
    }

    // ---- Portal Use ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortalUse(PlayerPortalEvent event) {
        if (bypass(event.getPlayer())) return;
        if (!rm().isAllowed(event.getPlayer().getLocation(), RegionFlag.PORTAL_USE)) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(mm.deserialize("<#e22c27>Portais estão desativados nesta região!"));
        }
    }

    // ---- Region entry/exit teleport tracker ----

    private void startRegionTracker() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                Set<String> previous = playerRegions.getOrDefault(uuid, Collections.emptySet());
                Set<String> current = new HashSet<>();
                for (Region r : rm().getRegionsAt(player.getLocation())) {
                    current.add(r.getName());
                }

                for (String name : current) {
                    if (!previous.contains(name)) {
                        Region r = rm().getRegion(name);
                        if (r != null && r.hasEntryTp()) {
                            Location tp = r.getEntryTpLocation();
                            if (tp != null) player.teleport(tp);
                        }
                    }
                }

                for (String name : previous) {
                    if (!current.contains(name)) {
                        Region r = rm().getRegion(name);
                        if (r != null && r.hasExitTp()) {
                            Location tp = r.getExitTpLocation();
                            if (tp != null) player.teleport(tp);
                        }
                    }
                }

                playerRegions.put(uuid, current);
            }
        }, 10L, 10L);
    }
}
