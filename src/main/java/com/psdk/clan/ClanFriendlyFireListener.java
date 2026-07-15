package com.psdk.clan;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Bloqueia dano entre membros do mesmo clan quando friendly-fire está desativado.
 * Quando o líder (ou membro autorizado) ativa PvP aliado via GUI, o dano é liberado.
 */
public class ClanFriendlyFireListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final PSDK plugin;

    public ClanFriendlyFireListener(PSDK plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.equals(victim)) return;

        ClanManager cm = plugin.getClanManager();
        Clan victimClan   = cm.getClanByPlayer(victim.getUniqueId());
        if (victimClan == null) return;

        Clan attackerClan = cm.getClanByPlayer(attacker.getUniqueId());
        if (attackerClan == null) return;

        if (victimClan.getId() == attackerClan.getId()) {
            if (!victimClan.isFriendlyFire()) {
                event.setCancelled(true);
                attacker.sendActionBar(MM.deserialize("<#FF6B6B>PvP entre membros do clan está <bold>desativado</bold>!"));
            }
            return;
        }

        // Clans diferentes: se são aliados, PvP só é liberado quando AMBOS os clans
        // ativaram ally_ff. Rivais (ou sem relação) sempre podem se atacar.
        if (!victimClan.isAllyFriendlyFire() || !attackerClan.isAllyFriendlyFire()) {
            if (cm.areAllied(victimClan.getId(), attackerClan.getId())) {
                event.setCancelled(true);
                attacker.sendActionBar(MM.deserialize("<#FF6B6B>PvP entre clans aliados está <bold>desativado</bold>!"));
            }
        }
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) return p;
        if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }
}
