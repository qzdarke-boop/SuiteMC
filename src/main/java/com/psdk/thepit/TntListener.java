package com.psdk.thepit;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

public class TntListener implements Listener {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final double DAMAGE_MULTIPLIER = 0.5;   // dano da TNT nos players

    private final PSDK plugin;

    public TntListener(PSDK plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTntExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) return;

        ArenaManager arenaManager = plugin.getArenaManager();
        if (!arenaManager.getArenaData().isDefined()) return;

        World arenaWorld = arenaManager.getCachedWorld();
        if (arenaWorld == null || !arenaWorld.equals(event.getEntity().getWorld())) return;

        Player placer = (tnt.getSource() instanceof Player p) ? p : null;
        double[] coinsEarned = {0};

        event.blockList().removeIf(block -> {
            if (!arenaManager.isInsideArena(block.getX(), block.getY(), block.getZ())) return false;

            // Player-placed ore block — give coins and clear silently
            if (placer != null) {
                BlockMineListener.MineableBlock data = BlockMineListener.MINEABLE.get(block.getType());
                if (data != null) {
                    coinsEarned[0] += data.coins();
                    block.setType(Material.AIR, false); // no drops, no physics update
                    return true; // already cleared, skip explosion processing
                }
            }

            // Other player-placed block — let the explosion handle it normally
            return false;
        });

        if (placer != null && coinsEarned[0] > 0) {
            double coins = coinsEarned[0];
            plugin.getEconomyManager().addCoins(placer.getUniqueId(), placer.getName(), coins);
            placer.sendActionBar(mm.deserialize(
                    "<#fcc850>+<bold>" + (int) coins + "</bold> <#ffff55>coins <#a4a4a4>(TNT)"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTntDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof TNTPrimed tnt)) return;
        if (!plugin.getArenaManager().isInsideArena(victim.getLocation())) return;

        // Jaula: explosão do outro lado da estrutura → sem dano (defesa em profundidade,
        // na própria rota da TNT vanilla; independe do handler da CageListener).
        if (plugin.getCageManager() != null
                && plugin.getCageManager().isSeparatedByActiveCage(tnt.getLocation(), victim)) {
            event.setCancelled(true);
            return;
        }

        event.setDamage(event.getDamage() * DAMAGE_MULTIPLIER);
    }
}
