package com.psdk.colina;

import com.psdk.PSDK;
import com.psdk.vip.VipBonus;
import net.kyori.adventure.text.minimessage.MiniMessage;
import com.psdk.util.ClanText;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class ColinaManager implements org.bukkit.event.Listener {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final long DEATH_GRACE_MS = 3000;   // quem morre não conta na colina por 3s

    private final PSDK plugin;
    private String world;
    private double x, y, z;
    private volatile String dono = "Sem Dono";   // volatile: placeholder lê o valor mais fresco
    private boolean active;
    private final java.util.Map<java.util.UUID, Long> lastDeath = new java.util.concurrent.ConcurrentHashMap<>();

    public ColinaManager(PSDK plugin) {
        this.plugin = plugin;
        load();
        if (active) startTasks();
    }

    private void load() {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection()
                .prepareStatement("SELECT * FROM colina WHERE id = 'main'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                world = rs.getString("world");
                x = rs.getDouble("x");
                y = rs.getDouble("y");
                z = rs.getDouble("z");
                dono = rs.getString("dono");
                active = world != null && !world.isEmpty();
                if (active) plugin.getLogger().info("Colina carregada em " + world + " " + x + ", " + y + ", " + z);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao carregar colina", e);
        }
        // Raio (guardado na tabela settings — evita migração de schema).
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection()
                .prepareStatement("SELECT value FROM settings WHERE key = 'colina_radius'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) radius = Double.parseDouble(rs.getString("value"));
        } catch (Exception ignored) { /* sem raio salvo -> usa o padrão */ }
    }

    public void setLocation(Location loc) { setLocation(loc, this.radius); }

    public void setLocation(Location loc, double raio) {
        this.world = loc.getWorld().getName();
        this.x = loc.getX();
        this.y = loc.getY();
        this.z = loc.getZ();
        this.radius = Math.max(2.0, Math.min(100.0, raio));
        this.dono = "Sem Dono";
        this.active = true;
        save();
        startTasks();
    }

    public void remove() {
        for (org.bukkit.scheduler.BukkitTask t : tasks) t.cancel();   // para o pagamento/particulas na hora
        tasks.clear();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection()
                .prepareStatement("DELETE FROM colina WHERE id = 'main'")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao remover colina", e);
        }
        this.active = false;
        this.world = null;
        this.dono = "Sem Dono";
    }

    private void save() {
        String sql = "INSERT OR REPLACE INTO colina (id, world, x, y, z, dono) VALUES ('main', ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setDouble(2, x);
            ps.setDouble(3, y);
            ps.setDouble(4, z);
            ps.setString(5, dono);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao salvar colina", e);
        }
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection()
                .prepareStatement("INSERT OR REPLACE INTO settings (key, value) VALUES ('colina_radius', ?)")) {
            ps.setString(1, String.valueOf(radius));
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private static final double DEFAULT_RADIUS = 12.0;   // raio padrão (ERA 51 -> cobria o spawn = bug do "dono ao entrar")
    private static final long JOIN_GRACE_MS = 5000;      // quem acabou de entrar não conta por 5s
    private static final double HEIGHT = 3.0;
    private double radius = DEFAULT_RADIUS;
    private final java.util.Map<java.util.UUID, Long> lastJoin = new java.util.concurrent.ConcurrentHashMap<>();

    public boolean isInside(Location loc) {
        if (!active || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(world)) return false;
        double lx = loc.getX(), ly = loc.getY(), lz = loc.getZ();
        double dx = lx - x, dz = lz - z;
        return (dx * dx + dz * dz) <= (radius * radius)
                && ly >= y && ly <= (y + HEIGHT);
    }

    public double getRadius() { return radius; }

    private final java.util.List<org.bukkit.scheduler.BukkitTask> tasks = new java.util.ArrayList<>();

    private void startTasks() {
        // Cancela as tasks anteriores: sem isso, rodar /setarcolina (ou recarregar)
        // mais de uma vez empilhava timers -> o dono ganhava 50 coins POR timer/ciclo.
        for (org.bukkit.scheduler.BukkitTask t : tasks) t.cancel();
        tasks.clear();

        // DONO em tempo real (pro placeholder): recalcula só o nome a cada 0.1s, SEM pagar.
        tasks.add(new BukkitRunnable() {
            @Override
            public void run() {
                if (!active) { cancel(); return; }
                updateDono();
            }
        }.runTaskTimer(plugin, 2L, 2L));

        // Pagamento do dono (ciclo lento): 50 coins a cada 5s.
        tasks.add(new BukkitRunnable() {
            @Override
            public void run() {
                if (!active) { cancel(); return; }
                rewardCycle();
            }
        }.runTaskTimer(plugin, 100L, 100L));

        tasks.add(new BukkitRunnable() {
            @Override
            public void run() {
                if (!active) { cancel(); return; }
                broadcastStatus();
            }
        }.runTaskTimer(plugin, 1200L, 1200L));

        tasks.add(new BukkitRunnable() {
            @Override
            public void run() {
                if (!active) { cancel(); return; }
                spawnParticles();
            }
        }.runTaskTimer(plugin, 20L, 20L));
    }

    private List<Player> computeInside() {
        List<Player> inside = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead() || p.getHealth() <= 0) continue;                         // morto/morrendo NÃO conta
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR
                    || p.getGameMode() == org.bukkit.GameMode.CREATIVE) continue;   // staff/spec NÃO dominam
            Long died = lastDeath.get(p.getUniqueId());
            if (died != null && now - died < DEATH_GRACE_MS) continue;              // morreu há <3s -> perde a colina
            Long joined = lastJoin.get(p.getUniqueId());
            if (joined != null && now - joined < JOIN_GRACE_MS) continue;           // acabou de ENTRAR -> não conta por 5s
            if (isInside(p.getLocation())) inside.add(p);
        }
        return inside;
    }

    /** Marca a hora da morte: quem morreu perde a colina na hora (não fica "dono" morto). */
    @org.bukkit.event.EventHandler
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        lastDeath.put(event.getEntity().getUniqueId(), System.currentTimeMillis());
        if (active) updateDono();   // recalcula o dono na hora
    }

    /** Marca a hora da entrada: quem acabou de entrar NÃO vira dono na hora (grace de 5s). */
    @org.bukkit.event.EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        lastJoin.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @org.bukkit.event.EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        lastJoin.remove(event.getPlayer().getUniqueId());
        lastDeath.remove(event.getPlayer().getUniqueId());
    }

    /** Atualiza SÓ o dono (placeholder em tempo real) — sem coins, sem mensagens. */
    private void updateDono() {
        List<Player> inside = computeInside();
        if (inside.size() == 1) dono = inside.getFirst().getName();
        else if (inside.size() > 1) dono = "Disputa!";
        else dono = "Sem Dono";
    }

    /** Ciclo lento: paga 50 + bônus VIP coins ao dono e manda os avisos de disputa. */
    private void rewardCycle() {
        List<Player> inside = computeInside();
        if (inside.size() == 1) {
            Player owner = inside.getFirst();
            int reward = 50 + VipBonus.getBonusCoins(owner);
            plugin.getEconomyManager().addCoins(owner.getUniqueId(), owner.getName(), reward);
            owner.sendActionBar(mm.deserialize(
                    "<#fcc850><bold>+</bold>" + reward + " coins <#a4a4a4>(Dono da Colina!)"));
        } else if (inside.size() > 1) {
            for (Player p : inside) {
                p.sendMessage(mm.deserialize(
                        "<#FF0000>Disputa na colina! <#a4a4a4>Elimine os outros para ganhar coins!"));
            }
        }
    }

    private String resolveTag(String playerName) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online == null) return playerName;
        return ClanText.resolvePlayerTag(online);
    }

    private void broadcastStatus() {
        if (dono.equals("Sem Dono")) return;

        if (!dono.equals("Disputa!")) {
            String tag = resolveTag(dono);
            plugin.getServer().sendMessage(mm.deserialize(
                    "\n  <#cbd1d7>O jogador <reset>" + tag + " <#cbd1d7>está dominando a colina!\n  <#cbd1d7>Vá até a colina e <#FF0000>elimine <#cbd1d7>ele!\n"));
        } else {
            plugin.getServer().sendMessage(mm.deserialize(
                    "\n  <#cbd1d7>Há uma <#FF0000><bold>disputa</bold> <#cbd1d7>na colina!\n  <#cbd1d7>Batalhe e ganhe a disputa! <#FF0000>Elimine<#cbd1d7> outros jogadores para ganhar coins!\n"));
        }
    }

    private static final int PARTICLE_POINTS = 200;

    private void spawnParticles() {
        World w = Bukkit.getWorld(world);
        if (w == null) return;

        Location center = new Location(w, x, y, z);
        boolean hasNearby = false;
        double viewDistSq = (radius + 32) * (radius + 32);
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(center) < viewDistSq) {
                hasNearby = true;
                break;
            }
        }
        if (!hasNearby) return;

        double py = y + 1;
        Particle.DustOptions dust = new Particle.DustOptions(Color.RED, 1.0f);

        for (int i = 0; i < PARTICLE_POINTS; i++) {
            double angle = 2 * Math.PI * i / PARTICLE_POINTS;
            double px = x + radius * Math.cos(angle);
            double pz = z + radius * Math.sin(angle);
            w.spawnParticle(Particle.DUST, new Location(w, px, py, pz), 1, dust);
        }
    }

    public String getDono() { return dono; }
    public boolean isActive() { return active; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public String getWorldName() { return world; }
}
