package com.psdk.social;

import com.psdk.PSDK;
import com.psdk.boss.ModelEngineHook;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;

/**
 * NPC do Wumpus (ModelEngine) que, ao ser clicado, mostra o link do Discord.
 * Invisível (só o modelo aparece), escalado pra ficar maior, com animação idle.
 * A posição é salva na tabela {@code settings} e o NPC reaparece ao reiniciar.
 */
public class DiscordNpcManager {

    public static final String MODEL_ID = "nm_wumpus";    // arquivo nm_wumpus.bbmodel (pasta nogs_menagerie)
    private static final double SCALE = 1.8;              // tamanho do MODELO ("maiorzinho")
    private static final double HITBOX_SCALE = 1.8;       // tamanho da HITBOX (colisão + área de clique)
    private static final String SETTING_KEY = "discord_npc_loc";

    private final PSDK plugin;
    private Zombie npc;
    private Object model;
    private Location loc;
    private BukkitRunnable animTask;

    public DiscordNpcManager(PSDK plugin) { this.plugin = plugin; }

    public void load() {
        String val = readSetting();
        if (val == null) return;
        Location l = parseLoc(val);
        if (l == null) return;
        this.loc = l;
        // Atraso: garante que os mundos já carregaram antes de spawnar.
        plugin.getServer().getScheduler().runTaskLater(plugin, this::spawnEntity, 40L);
    }

    public void setLocation(Location l) {
        this.loc = l.clone();
        saveSetting(serializeLoc(l));
        spawnEntity();
    }

    public boolean hasNpc() { return loc != null; }

    public void removeNpc() {
        despawn();
        loc = null;
        deleteSetting();
    }

    private void spawnEntity() {
        despawn();
        if (loc == null || loc.getWorld() == null) return;
        npc = loc.getWorld().spawn(loc, Zombie.class, z -> {
            z.setAI(false);                 // não anda nem ataca
            z.setBaby(false);
            z.setInvisible(true);           // só o modelo aparece
            z.setGravity(false);
            z.setInvulnerable(true);
            z.setCollidable(true);          // jogador ESBARRA nele (não atravessa)
            z.setSilent(true);
            z.setPersistent(false);
            z.setRemoveWhenFarAway(false);
            z.setShouldBurnInDay(false);
            z.setCustomNameVisible(false);
            var scale = z.getAttribute(Attribute.SCALE);
            if (scale != null) scale.setBaseValue(HITBOX_SCALE);   // aumenta a HITBOX (colisão + clique)
        });
        model = ModelEngineHook.applyModelAny(npc, MODEL_ID, "wumpus", "nogs_wumpus", "nog_wumpus");
        if (model != null) {
            ModelEngineHook.setScale(model, SCALE);
            ModelEngineHook.playAnimation(model, "idle", 0.2, 0.2, 1.0, false);
        }
        // Mantém a idle e RE-ANCORA (não deixa empurrarem o NPC pra longe).
        animTask = new BukkitRunnable() {
            @Override public void run() {
                if (npc == null || !npc.isValid()) { cancel(); return; }
                if (loc != null && npc.getWorld().equals(loc.getWorld())
                        && npc.getLocation().distanceSquared(loc) > 0.02) {
                    npc.teleport(loc);
                }
                if (model != null) ModelEngineHook.playAnimation(model, "idle", 0.2, 0.2, 1.0, false);
            }
        };
        animTask.runTaskTimer(plugin, 10L, 10L);
    }

    public boolean isNpc(Entity e) {
        return npc != null && e != null && e.getUniqueId().equals(npc.getUniqueId());
    }

    public void despawn() {
        if (animTask != null) { animTask.cancel(); animTask = null; }
        if (npc != null && npc.isValid()) npc.remove();
        npc = null;
        model = null;
    }

    // ───── persistência (tabela settings) ─────
    private String serializeLoc(Location l) {
        return l.getWorld().getName() + ";" + l.getX() + ";" + l.getY() + ";" + l.getZ()
                + ";" + l.getYaw() + ";" + l.getPitch();
    }

    private Location parseLoc(String s) {
        try {
            String[] p = s.split(";");
            World w = Bukkit.getWorld(p[0]);
            if (w == null) return null;
            return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                    Double.parseDouble(p[3]), Float.parseFloat(p[4]), Float.parseFloat(p[5]));
        } catch (Exception e) { return null; }
    }

    private String readSetting() {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection()
                .prepareStatement("SELECT value FROM settings WHERE key = ?")) {
            ps.setString(1, SETTING_KEY);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("value");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao ler NPC do discord", e);
        }
        return null;
    }

    private void saveSetting(String val) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection()
                .prepareStatement("INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)")) {
            ps.setString(1, SETTING_KEY);
            ps.setString(2, val);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao salvar NPC do discord", e);
        }
    }

    private void deleteSetting() {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection()
                .prepareStatement("DELETE FROM settings WHERE key = ?")) {
            ps.setString(1, SETTING_KEY);
            ps.executeUpdate();
        } catch (SQLException ignored) { }
    }
}
