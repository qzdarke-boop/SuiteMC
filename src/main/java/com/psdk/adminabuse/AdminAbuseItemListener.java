package com.psdk.adminabuse;

import com.psdk.PSDK;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Liga os itens do arsenal aos poderes:
 *  • lançou bola de neve/ovo/wind charge do arsenal -> tagueia o projétil e repõe o item (munição infinita)
 *  • projétil tagueado caiu -> dispara o efeito (meteoro / baú / tornado)
 *  • clique direito com o Cajado do Trovão -> raio em cadeia na mira
 *  • dropar item do arsenal -> o item some (não vaza pros jogadores)
 */
public class AdminAbuseItemListener implements Listener {

    private static final long THUNDER_COOLDOWN_MS = 1200;
    private static final long CHICKEN_COOLDOWN_MS = 3000;
    private static final long FREEZE_COOLDOWN_MS  = 8000;
    private static final long NUKE_COOLDOWN_MS    = 8000;

    private final PSDK plugin;
    private final NamespacedKey projPowerKey;
    private final NamespacedKey projThrowerKey;
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    /** Quem lançou a Pérola Magnética: o teleporte vanilla da pearl é cancelado. */
    private final Map<UUID, Long> pearlNoTeleport = new ConcurrentHashMap<>();

    public AdminAbuseItemListener(PSDK plugin) {
        this.plugin = plugin;
        this.projPowerKey = new NamespacedKey(plugin, "abuse_proj_power");
        this.projThrowerKey = new NamespacedKey(plugin, "abuse_proj_thrower");
    }

    private AdminAbuseManager am() { return plugin.getAdminAbuseManager(); }

    // ── lançou um projétil do arsenal: tagueia + repõe a munição ──
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player p)) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        String power = AdminAbuseItems.powerOf(plugin, hand);
        if (power == null) return;

        if (!am().isActive()) {   // show acabou e sobrou item? cancela e limpa.
            event.setCancelled(true);
            p.getInventory().setItemInMainHand(null);
            return;
        }

        event.getEntity().getPersistentDataContainer().set(projPowerKey, PersistentDataType.STRING, power);
        event.getEntity().getPersistentDataContainer().set(projThrowerKey, PersistentDataType.STRING, p.getName());
        if (AdminAbuseItems.POWER_GATHER.equals(power)) {
            pearlNoTeleport.put(p.getUniqueId(), System.currentTimeMillis() + 15_000);
        }

        // munição infinita: 1 tick depois (após o consumo), enche o item de volta
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (ItemStack item : p.getInventory().getContents()) {
                if (item != null && power.equals(AdminAbuseItems.powerOf(plugin, item))) item.setAmount(16);
            }
        });
    }

    // ── ovo do arsenal nunca choca galinha ──
    @EventHandler
    public void onEggThrow(PlayerEggThrowEvent event) {
        String power = event.getEgg().getPersistentDataContainer().get(projPowerKey, PersistentDataType.STRING);
        if (AdminAbuseItems.POWER_CHEST.equals(power)) event.setHatching(false);
    }

    // ── projétil tagueado caiu: dispara o efeito ──
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        Projectile proj = event.getEntity();
        String power = proj.getPersistentDataContainer().get(projPowerKey, PersistentDataType.STRING);
        if (power == null) return;
        String thrower = proj.getPersistentDataContainer().get(projThrowerKey, PersistentDataType.STRING);

        Location at;
        Block hitBlock = event.getHitBlock();
        if (hitBlock != null) at = hitBlock.getLocation().add(0.5, 1, 0.5);
        else if (event.getHitEntity() != null) at = event.getHitEntity().getLocation();
        else at = proj.getLocation();

        switch (power) {
            case AdminAbuseItems.POWER_METEOR  -> am().meteorStrike(at);
            case AdminAbuseItems.POWER_CHEST   -> am().eggChest(thrower != null ? thrower : "Admin", at);
            case AdminAbuseItems.POWER_TORNADO -> am().tornadoAt(at);
            case AdminAbuseItems.POWER_GATHER  -> am().gatherAt(at, thrower != null ? thrower : "Admin");
        }
    }

    // ── projéteis do arsenal não causam dano nem colocam ninguém em combate ──
    @EventHandler(priority = EventPriority.LOWEST)
    public void onArsenalProjectileDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Projectile proj
                && proj.getPersistentDataContainer().has(projPowerKey, PersistentDataType.STRING)) {
            event.setCancelled(true);   // o efeito vem do poder, não do projétil
        }
    }

    // ── a Pérola Magnética não teleporta o lançador (quem puxa é o poder) ──
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPearlTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;
        Long until = pearlNoTeleport.remove(event.getPlayer().getUniqueId());
        if (until != null && System.currentTimeMillis() <= until) event.setCancelled(true);
    }

    // ── Itens de clique direito: Cajado do Trovão e Pena do Apocalipse ──
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        ItemStack hand = event.getItem();
        String power = AdminAbuseItems.powerOf(plugin, hand);
        if (power == null) return;
        long cdMs = switch (power) {
            case AdminAbuseItems.POWER_THUNDER -> THUNDER_COOLDOWN_MS;
            case AdminAbuseItems.POWER_CHICKEN -> CHICKEN_COOLDOWN_MS;
            case AdminAbuseItems.POWER_FREEZE  -> FREEZE_COOLDOWN_MS;
            case AdminAbuseItems.POWER_NUKE    -> NUKE_COOLDOWN_MS;
            default -> -1;   // itens de arremesso: o interact não cuida deles
        };
        if (cdMs < 0) return;
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);

        if (!am().isActive()) { p.getInventory().setItemInMainHand(null); return; }
        if (onCooldown(p, power, cdMs)) return;

        Block target = p.getTargetBlockExact(60);
        Location at = target != null
                ? target.getLocation().add(0.5, 1, 0.5)
                : p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(30));

        switch (power) {
            case AdminAbuseItems.POWER_THUNDER -> am().thunderAt(at);
            case AdminAbuseItems.POWER_CHICKEN -> am().chickenRain(at);
            case AdminAbuseItems.POWER_FREEZE  -> am().freezeAll(p);
            case AdminAbuseItems.POWER_NUKE    -> am().nukeAt(at);
        }
    }

    private boolean onCooldown(Player p, String power, long cdMs) {
        long now = System.currentTimeMillis();
        String key = p.getUniqueId() + ":" + power;
        Long last = cooldowns.get(key);
        if (last != null && now - last < cdMs) return true;
        cooldowns.put(key, now);
        return false;
    }

    // ── dropar item do arsenal: some (não vaza pro público) ──
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (AdminAbuseItems.powerOf(plugin, event.getItemDrop().getItemStack()) != null) {
            event.getItemDrop().remove();
        }
    }
}
