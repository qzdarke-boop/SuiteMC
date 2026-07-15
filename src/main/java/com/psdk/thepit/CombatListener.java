package com.psdk.thepit;

import com.psdk.PSDK;
import com.psdk.region.RegionFlag;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public class CombatListener implements Listener {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public CombatListener(PSDK plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (plugin.getAfkManager().isInAfkWorld(victim)) return;
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker == victim) return;
        if (plugin.getAfkManager().isInAfkWorld(attacker)) return;
        if (!plugin.getRegionManager().isAllowed(victim.getLocation(), RegionFlag.PVP)) {
            // Na zona segura o PvP só continua quando AMBOS já estão em combate
            // (mesma exceção do RegionProtectionListener). Nesse caso o golpe é
            // válido e PRECISA renovar o tempo de combate — senão o cooldown ia
            // só descendo e expirava mesmo enquanto a luta continuava na área segura.
            if (!plugin.getCombatManager().isInCombat(attacker)
                    || !plugin.getCombatManager().isInCombat(victim)) return;
        }
        plugin.getCombatManager().registerHit(victim, attacker);
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        PlayerData victimData = plugin.getPlayerDataManager().getPlayerData(victim);
        if (victimData != null) {
            victimData.addDeath();
        }

        plugin.getCombatManager().clear(victim.getUniqueId());

        if (killer == null || killer == victim) return;
        plugin.getLevelManager().onKill(killer);

        // Recompensa por abate (bounty): se a vítima tinha recompensa, o killer leva os coins.
        if (plugin.getBountyManager() != null) {
            plugin.getBountyManager().handleKill(killer, victim);
        }

        // Sifão: ao matar, o killer recupera um pouco de vida.
        applySifao(killer);
    }

    /** Quantos pontos de vida o Sifão recupera por kill (6 = 3 corações). */
    private static final double SIFAO_HEAL = 6.0;

    private void applySifao(Player killer) {
        var attr = killer.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double max = (attr != null) ? attr.getValue() : 20.0;
        killer.setHealth(Math.min(max, killer.getHealth() + SIFAO_HEAL));
        killer.getWorld().spawnParticle(org.bukkit.Particle.HEART,
                killer.getLocation().add(0, 1.8, 0), 5, 0.3, 0.3, 0.3, 0);
        killer.playSound(killer.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_DRINK, 0.6f, 1.4f);
    }
}
