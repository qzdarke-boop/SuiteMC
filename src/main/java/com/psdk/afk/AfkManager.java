package com.psdk.afk;

import com.psdk.PSDK;
import com.psdk.vip.VipBonus;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class AfkManager {

    public static final String AFK_WORLD = "afk";
    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final long FARM_INTERVAL_SECONDS = 60;

    // Tiers em ordem decrescente — primeiro match vence.
    // Base (sem permissão): 30 coins/min.
    private static final int[] COIN_TIERS = {250, 200, 150, 100, 75, 50};
    private static final String[] TIER_PERMS = {
            "psdk.afk.tier5", "psdk.afk.tier4", "psdk.afk.tier3",
            "psdk.afk.tier2", "psdk.afk.tier1", "psdk.afk.tier0"
    };
    private static final int COIN_BASE = 30;

    private final PSDK plugin;
    private final Set<UUID> afkPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> secondsElapsed = new ConcurrentHashMap<>();
    private Location afkSpawn;

    public AfkManager(PSDK plugin) {
        this.plugin = plugin;
        loadAfkSpawn();
        startFarmTask();
    }

    public boolean isAfk(Player player) {
        return player != null && afkPlayers.contains(player.getUniqueId());
    }

    public boolean isAfk(UUID uuid) {
        return afkPlayers.contains(uuid);
    }

    public void setAfk(Player player) {
        afkPlayers.add(player.getUniqueId());
        secondsElapsed.put(player.getUniqueId(), 0);
    }

    public void removeAfk(Player player) {
        afkPlayers.remove(player.getUniqueId());
        secondsElapsed.remove(player.getUniqueId());
    }

    public void removeAfk(UUID uuid) {
        afkPlayers.remove(uuid);
        secondsElapsed.remove(uuid);
    }

    public Location getAfkSpawn() {
        return afkSpawn;
    }

    public void setAfkSpawn(Location loc) {
        this.afkSpawn = loc;
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "INSERT OR REPLACE INTO settings (key, value) VALUES ('afk_spawn', ?)")) {
            ps.setString(1, loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," +
                    loc.getZ() + "," + loc.getYaw() + "," + loc.getPitch());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao salvar afk spawn", e);
        }
    }

    public int getCoinsPerCycle(Player player) {
        for (int i = 0; i < TIER_PERMS.length; i++) {
            if (player.hasPermission(TIER_PERMS[i])) return COIN_TIERS[i];
        }
        return COIN_BASE;
    }

    private void startFarmTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    if (!afkPlayers.contains(uuid)) continue;
                    if (!isInAfkWorld(player)) continue;

                    int elapsed = secondsElapsed.getOrDefault(uuid, 0) + 1;
                    int baseCoins = getCoinsPerCycle(player);
                    int vipBonus = VipBonus.getBonusCoins(player);
                    int coins = baseCoins + vipBonus;
                    int remaining = (int) (FARM_INTERVAL_SECONDS - (elapsed % FARM_INTERVAL_SECONDS));

                    String plural = (coins > 1 ? "s" : "");
                    if (elapsed % FARM_INTERVAL_SECONDS == 0) {
                        plugin.getEconomyManager().addCoins(uuid, player.getName(), coins);
                        player.sendActionBar(mm.deserialize(
                                "<#efa600>Farmando Coins... <#a4a4a4>[<#fcc850><bold>+</bold>" + coins + " coin" + plural + "<#a4a4a4>]"));
                    } else {
                        player.sendActionBar(mm.deserialize(
                                "<#efa600>Farmando Coins... <#a4a4a4>[<#fcc850><bold>+</bold>" + coins + " coin" + plural + " em " + remaining + "s<#a4a4a4>]"));
                    }

                    secondsElapsed.put(uuid, elapsed);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Verifica se o jogador está no mundo de AFK. Considera tanto o mundo cujo
     * nome é {@code "afk"} quanto o mundo do spawn AFK configurado — assim o farm
     * funciona independentemente do nome real do mundo no servidor.
     */
    public boolean isInAfkWorld(Player player) {
        World world = player.getWorld();
        if (world.getName().equalsIgnoreCase(AFK_WORLD)) return true;
        return afkSpawn != null && afkSpawn.getWorld() != null
                && world.equals(afkSpawn.getWorld());
    }

    private void loadAfkSpawn() {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT value FROM settings WHERE key = 'afk_spawn'")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String[] p = rs.getString("value").split(",");
                if (p.length >= 6) {
                    World w = Bukkit.getWorld(p[0]);
                    if (w != null) {
                        afkSpawn = new Location(w,
                                Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                                Float.parseFloat(p[4]), Float.parseFloat(p[5]));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao carregar afk spawn", e);
        }
    }
}
