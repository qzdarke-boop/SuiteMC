package com.psdk.thepit;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatManager {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    public static final long COMBAT_DURATION_MS       = 15_000L;
    public static final long COMBAT_QUIT_KILL_MS      = COMBAT_DURATION_MS;
    public static final long FREE_DISPLAY_DURATION_MS = 3_000L;

    private final PSDK plugin;
    private final Map<UUID, Long> lastDamage = new ConcurrentHashMap<>();
    private final Map<UUID, Long> freeUntil  = new ConcurrentHashMap<>();

    public CombatManager(PSDK plugin) {
        this.plugin = plugin;
        startActionBarTask();
    }

    public void registerHit(Player victim, Player attacker) {
        long now = System.currentTimeMillis();
        if (victim != null)   lastDamage.put(victim.getUniqueId(), now);
        if (attacker != null) lastDamage.put(attacker.getUniqueId(), now);
    }

    public boolean isInCombat(UUID uuid) {
        Long last = lastDamage.get(uuid);
        if (last == null) return false;
        return (System.currentTimeMillis() - last) < COMBAT_DURATION_MS;
    }

    public boolean isInCombat(Player p) { return p != null && isInCombat(p.getUniqueId()); }

    public boolean shouldKillOnQuit(Player p) {
        if (p == null) return false;
        Long last = lastDamage.get(p.getUniqueId());
        if (last == null) return false;
        return (System.currentTimeMillis() - last) < COMBAT_QUIT_KILL_MS;
    }

    public long remainingMs(UUID uuid) {
        Long last = lastDamage.get(uuid);
        if (last == null) return 0;
        long diff = COMBAT_DURATION_MS - (System.currentTimeMillis() - last);
        return Math.max(0, diff);
    }

    public void clear(UUID uuid) {
        lastDamage.remove(uuid);
        freeUntil.remove(uuid);
    }

    private void startActionBarTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    UUID id = p.getUniqueId();
                    long remaining = remainingMs(id);

                    if (remaining > 0) {
                        long seg = (long) Math.ceil(remaining / 1000.0);
                        p.sendActionBar(mm.deserialize(
                                "<#e22c27>Você está em combate! Aguarde <bold>" + seg + "</bold>s"));
                        freeUntil.put(id, now + FREE_DISPLAY_DURATION_MS);
                    } else if (freeUntil.containsKey(id)) {
                        long until = freeUntil.get(id);
                        if (now <= until) {
                            p.sendActionBar(mm.deserialize("<#10fc46>Você saiu de combate!"));
                        } else {
                            freeUntil.remove(id);
                            lastDamage.remove(id);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }
}
