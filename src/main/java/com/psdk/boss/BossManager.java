package com.psdk.boss;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Gerencia o boss ativo (Cavaleiro das Sombras): spawn, despawn, rastreio de
 * dano, interceptação do golpe letal (pra rodar a animação de morte) e a
 * entrega de recompensa pra quem mais causou dano.
 */
public class BossManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // ================= AJUSTES DO BOSS (EDITE AQUI) =================
    /** Recompensas (coins) do TOP 5 em dano, na ordem #1..#5. */
    private static final int[] REWARDS = {10000, 6000, 4000, 2000, 1000};
    /** Multiplicador da recompensa quando o boss EXPIRA (ninguém matou a tempo). 0.4 = 40% do prêmio normal. */
    private static final double EXPIRE_REWARD_MULT = 0.4;
    /** Reanuncia "o boss está vivo" a cada X minutos. */
    private static final int ANUNCIO_MINUTOS = 10;
    /** Intervalo PADRÃO do auto-spawn (min) se nada estiver configurado (270 = 4h30). */
    private static final int DEFAULT_AUTOSPAWN_MINUTOS = 270;
    /** Tempo PADRÃO (min) que o boss fica vivo; se ninguém matar, vai embora (240 = 4h). */
    private static final int DEFAULT_LIFETIME_MINUTOS = 240;
    /** Jogadores online mínimos para o boss nascer sozinho (evita spawn em servidor vazio). */
    private static final int MIN_PLAYERS_AUTOSPAWN = 1;
    /** Percentuais de vida que disparam um aviso enquanto o boss está vivo (item 8). */
    private static final int[] HP_THRESHOLDS = {90, 75, 50, 25};
    /** Aviso especial quando faltam ~10 min para o próximo spawn (item 10). */
    private static final long PRE_SPAWN_WARN_MS = 10 * 60 * 1000L;
    /** Cadência dos avisos de "próximo spawn" (item 9): 1 hora. */
    private static final long NEXT_SPAWN_ANNOUNCE_MS = 60 * 60 * 1000L;
    // Texto das mensagens -> métodos broadcastAlive() e onBossDefeated() abaixo.
    // Nome do boss        -> ShadowKnight.NAME_PLAIN
    // Vida / cooldowns / dano -> constantes no topo do ShadowKnight.java
    // ===============================================================

    private final PSDK plugin;
    private BossEntity active;
    private BukkitRunnable aliveTask;
    private BukkitRunnable lifetimeTask;   // expira o boss se ninguém matar a tempo
    private BukkitRunnable autoSpawnTask;  // timer do auto-spawn (reagendável)
    private int autoSpawnMinutos;          // intervalo configurável do auto-spawn
    private int lifetimeMinutos;           // tempo de vida configurável do boss
    private long nextAutoSpawnMillis;      // quando o próximo auto-spawn vai disparar (epoch ms)
    private long bossExpireMillis;         // quando o boss vivo atual vai embora (epoch ms; 0 = sem boss)
    private Location arenaLoc;   // destino do /boss
    private Location spawnLoc;   // onde o boss nasce (auto a cada 15 min e /pboss spawn)
    private Location forcedChunkCenter;  // centro dos chunks force-loaded enquanto o boss vive

    /** Jogadores travados no lugar (root hand-coded via PlayerMoveEvent) -> posição travada. */
    private final Map<UUID, Location> frozen = new HashMap<>();

    /** Percentuais de vida já anunciados do boss ATUAL (resetado a cada spawn). */
    private final Set<Integer> announcedThresholds = new HashSet<>();
    private BukkitRunnable announceTask;   // avisos de próximo spawn / 10 min
    private long trackedNextSpawn;         // horário de spawn que estamos acompanhando (detecta reagendamento)
    private boolean tenMinWarned;          // aviso de 10 min já enviado neste ciclo
    private long lastHourlyAnnounce;       // último aviso horário de próximo spawn

    public BossManager(PSDK plugin) {
        this.plugin = plugin;
        this.arenaLoc = loadLoc("boss_arena");
        this.spawnLoc = loadLoc("boss_spawn");
        this.autoSpawnMinutos = loadInt("boss_autospawn_minutos", DEFAULT_AUTOSPAWN_MINUTOS);
        this.lifetimeMinutos = loadInt("boss_lifetime_minutos", DEFAULT_LIFETIME_MINUTOS);
        startAutoSpawn();
        startAnnounceTask();
    }

    /** Task periódica (30s) que envia os avisos de PRÓXIMO spawn e o aviso de 10 min. */
    private void startAnnounceTask() {
        if (announceTask != null) { announceTask.cancel(); announceTask = null; }
        announceTask = new BukkitRunnable() {
            @Override public void run() { tickNextSpawnAnnounce(); }
        };
        announceTask.runTaskTimer(plugin, 20L * 30, 20L * 30);
    }

    /** Avisos sincronizados com o próximo spawn REAL ({@link #nextAutoSpawnMillis}). */
    private void tickNextSpawnAnnounce() {
        if (active != null) return;                                  // boss vivo: não anuncia próximo spawn
        if (plugin.getServer().getOnlinePlayers().isEmpty()) return;

        long now = System.currentTimeMillis();
        long next = nextAutoSpawnMillis;
        if (next <= now) return;

        // Reagendamento (adiantado/adiado/reiniciado): reinicia os controles do ciclo.
        if (next != trackedNextSpawn) {
            trackedNextSpawn = next;
            tenMinWarned = false;
            lastHourlyAnnounce = 0L;
        }

        long remaining = next - now;

        // Aviso especial de 10 minutos (uma única vez por ciclo; margem segura <= 10min).
        if (!tenMinWarned && remaining <= PRE_SPAWN_WARN_MS) {
            tenMinWarned = true;
            var s = plugin.getServer();
            s.sendMessage(MM.deserialize(" "));
            s.sendMessage(MM.deserialize("<#e22c27><bold>O Boss surgirá em 10 minutos! <#cbd1d7>Prepare-se!"));
            s.sendMessage(MM.deserialize(" "));
            return;
        }

        // Aviso a cada 1 hora informando quanto falta (enquanto ainda falta mais de 10 min).
        if (remaining > PRE_SPAWN_WARN_MS
                && (lastHourlyAnnounce == 0L || now - lastHourlyAnnounce >= NEXT_SPAWN_ANNOUNCE_MS)) {
            lastHourlyAnnounce = now;
            var s = plugin.getServer();
            s.sendMessage(MM.deserialize(" "));
            s.sendMessage(MM.deserialize("<#cbd1d7>O Boss nascerá novamente em <#6817ff>"
                    + formatDuration(remaining) + "<#cbd1d7>!"));
            s.sendMessage(MM.deserialize(" "));
        }
    }

    /** Formata uma duração em "X horas e Y minutos" / "Z minutos" (arredondado ao minuto). */
    private String formatDuration(long ms) {
        long totalMin = Math.round(ms / 60000.0);
        long h = totalMin / 60;
        long m = totalMin % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) {
            sb.append(h).append(h == 1 ? " hora" : " horas");
            if (m > 0) sb.append(" e ").append(m).append(m == 1 ? " minuto" : " minutos");
        } else {
            if (m <= 0) m = 1;
            sb.append(m).append(m == 1 ? " minuto" : " minutos");
        }
        return sb.toString();
    }

    /** Avisa ao atravessar cada percentual de vida (uma vez por boss). */
    private void announceHealthThresholds() {
        if (active == null || active.isDead()) return;
        double max = active.getMaxHp();
        if (max <= 0) return;
        double pct = active.getHp() / max * 100.0;
        for (int t : HP_THRESHOLDS) {
            if (pct <= t && announcedThresholds.add(t)) {
                var s = plugin.getServer();
                s.sendMessage(MM.deserialize("<#e22c27><bold>" + active.getNamePlain()
                        + "</bold> <#cbd1d7>está com <#6817ff>" + t + "%<#cbd1d7> de vida! Ajude a derrotá-lo!"));
            }
        }
    }

    public Location getArena() { return arenaLoc; }
    public Location getBossSpawn() { return spawnLoc; }
    public void setArena(Location loc) { this.arenaLoc = loc; saveLoc("boss_arena", loc); }
    public void setBossSpawn(Location loc) { this.spawnLoc = loc; saveLoc("boss_spawn", loc); }

    public int getAutoSpawnMinutes() { return autoSpawnMinutos; }
    public int getLifetimeMinutes() { return lifetimeMinutos; }

    /** Define o intervalo do auto-spawn (min), persiste e reinicia o timer. */
    public void setAutoSpawnMinutes(int minutos) {
        this.autoSpawnMinutos = Math.max(1, minutos);
        saveInt("boss_autospawn_minutos", this.autoSpawnMinutos);
        startAutoSpawn();
    }

    /** Define quanto tempo (min) o boss fica vivo antes de ir embora. Vale a partir do próximo spawn. */
    public void setLifetimeMinutes(int minutos) {
        this.lifetimeMinutos = Math.max(1, minutos);
        saveInt("boss_lifetime_minutos", this.lifetimeMinutos);
    }

    /** Auto-spawn a cada {@link #autoSpawnMinutos} minutos (só se não houver boss vivo e houver gente online). */
    private void startAutoSpawn() {
        if (autoSpawnTask != null) { autoSpawnTask.cancel(); autoSpawnTask = null; }
        autoSpawnTask = new BukkitRunnable() {
            @Override public void run() {
                // Próximo disparo já fica agendado (o timer é de taxa fixa).
                nextAutoSpawnMillis = System.currentTimeMillis() + autoSpawnMinutos * 60000L;
                // active == null (e não !isActive()): não nasce um novo enquanto o atual
                // ainda está na animação de morte (senão a recompensa do anterior é perdida).
                // Exige gente online: sem isso, o boss nasceria de madrugada num servidor
                // vazio, ficaria vivo e BLOQUEARIA todos os próximos spawns.
                if (spawnLoc != null && active == null
                        && plugin.getServer().getOnlinePlayers().size() >= MIN_PLAYERS_AUTOSPAWN) {
                    spawn(spawnLoc);
                }
            }
        };
        long ticks = autoSpawnMinutos * 1200L;
        nextAutoSpawnMillis = System.currentTimeMillis() + autoSpawnMinutos * 60000L;
        autoSpawnTask.runTaskTimer(plugin, ticks, ticks);
    }

    /** Quando o próximo auto-spawn dispara (epoch ms). */
    public long getNextAutoSpawnMillis() { return nextAutoSpawnMillis; }

    /** Quando o boss vivo atual vai embora (epoch ms); 0 se não houver boss. */
    public long getBossExpireMillis() { return bossExpireMillis; }

    private void saveLoc(String key, Location loc) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)")) {
            ps.setString(1, key);
            ps.setString(2, loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," +
                    loc.getZ() + "," + loc.getYaw() + "," + loc.getPitch());
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("[Boss] erro ao salvar " + key + ": " + e.getMessage());
        }
    }

    private void saveInt(String key, int value) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)")) {
            ps.setString(1, key);
            ps.setString(2, String.valueOf(value));
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("[Boss] erro ao salvar " + key + ": " + e.getMessage());
        }
    }

    private int loadInt(String key, int def) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT value FROM settings WHERE key = ?")) {
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Integer.parseInt(rs.getString("value").trim());
        } catch (Exception e) {
            plugin.getLogger().warning("[Boss] erro ao carregar " + key + ": " + e.getMessage());
        }
        return def;
    }

    private Location loadLoc(String key) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT value FROM settings WHERE key = ?")) {
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String[] p = rs.getString("value").split(",");
                if (p.length >= 6) {
                    World w = plugin.getServer().getWorld(p[0]);
                    if (w != null) return new Location(w,
                            Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                            Float.parseFloat(p[4]), Float.parseFloat(p[5]));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Boss] erro ao carregar " + key + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Trava o jogador no lugar por {@code ticks} (root real, sem potion effect): o
     * {@link #onFrozenMove} segura a posição no PlayerMoveEvent (deixa só olhar).
     * Enquanto preso, leva dano de magma do {@code source}.
     */
    public void freezePlayer(Player player, LivingEntity source, int ticks) {
        final UUID id = player.getUniqueId();
        if (frozen.containsKey(id)) return;
        // Ancora no CHÃO antes de travar: se travar no ar, o servidor/anti-cheat dá kick de "flying".
        Location lock = player.getLocation().clone();
        while (lock.getBlockY() > player.getWorld().getMinHeight()
                && lock.clone().subtract(0, 1, 0).getBlock().getType().isAir()) {
            lock.subtract(0, 1, 0);
        }
        player.teleport(lock);
        frozen.put(id, lock);
        final boolean prevAllowFlight = player.getAllowFlight();
        player.setAllowFlight(true);   // reforço contra o kick "Flying is not enabled"
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                Player p = plugin.getServer().getPlayer(id);
                if (p == null || p.isDead() || source.isDead() || !source.isValid() || !frozen.containsKey(id)) {
                    frozen.remove(id);
                    if (p != null) { p.setFlying(false); p.setAllowFlight(prevAllowFlight); }
                    cancel();
                    return;
                }
                p.setFlying(false);   // não sai voando; a posição é segurada no onFrozenMove
                Location feet = p.getLocation();
                p.getWorld().spawnParticle(Particle.SMOKE, feet, 4, 0.3, 0.1, 0.3, 0.01);
                if (t > 0 && t % 20 == 0) {   // o boss continua atacando enquanto preso
                    p.damage(1 + ThreadLocalRandom.current().nextDouble() * 1.0, source);
                    p.getWorld().spawnParticle(Particle.LAVA, feet.clone().add(0, 1, 0), 6, 0.3, 0.5, 0.3, 0);
                }
                if (++t >= ticks) {
                    frozen.remove(id);
                    p.setFlying(false);
                    p.setAllowFlight(prevAllowFlight);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // Root hand-coded: segura a posição (from), deixando só girar a câmera.
    @EventHandler
    public void onFrozenMove(PlayerMoveEvent event) {
        Location lock = frozen.get(event.getPlayer().getUniqueId());
        if (lock == null) return;
        Location to = event.getTo();
        if (to == null) return;
        if (to.getX() != lock.getX() || to.getY() != lock.getY() || to.getZ() != lock.getZ()) {
            Location held = lock.clone();
            held.setYaw(to.getYaw());
            held.setPitch(to.getPitch());
            event.setTo(held);
        }
    }

    @EventHandler
    public void onFrozenQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (frozen.remove(p.getUniqueId()) != null
                && p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
            // estava travado: restaura o voo pro padrão (não deixa o fly preso = exploit ao reconectar)
            p.setFlying(false);
            p.setAllowFlight(false);
        }
    }

    public boolean isActive() {
        return active != null && !active.isDead();
    }

    /**
     * Spawn automático/aleatório: sorteia entre os bosses PRONTOS.
     * OBS: "tower" (Tower Skeleton) está em desenvolvimento e FICA DE FORA do sorteio
     * automático. Ele ainda pode ser invocado manualmente com /pboss spawn tower.
     * Quando ficar pronto, é só adicionar "tower" de volta neste array.
     */
    public void spawn(Location loc) {
        String[] types = {"cavaleiro", "spooky"};
        spawn(loc, types[ThreadLocalRandom.current().nextInt(types.length)]);
    }

    /** Spawna um boss específico: "cavaleiro" (Cavaleiro das Sombras) ou "spooky" (Pumpkin King). */
    public void spawn(Location loc, String type) {
        if (active != null) active.remove();
        if (aliveTask != null) { aliveTask.cancel(); aliveTask = null; }
        if (lifetimeTask != null) { lifetimeTask.cancel(); lifetimeTask = null; }

        // Mantém o chunk do boss carregado mesmo sem jogadores por perto: sem isso, o
        // chunk descarrega, a entidade fica inválida e o boss "some" logo após anunciar.
        setForcedChunks(loc, true);

        active = createBoss(type, loc);
        final BossEntity spawned = active;
        announcedThresholds.clear();   // novo boss → recomeça o controle de percentuais

        broadcastAlive();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.8f);
        }

        // Reanuncia a cada 10 minutos enquanto estiver vivo.
        aliveTask = new BukkitRunnable() {
            @Override public void run() {
                if (!isActive()) { cancel(); return; }
                broadcastAlive();
            }
        };
        aliveTask.runTaskTimer(plugin, ANUNCIO_MINUTOS * 1200L, ANUNCIO_MINUTOS * 1200L);

        // Timeout: se ninguém matar em lifetimeMinutos, o boss vai embora e libera o spawn.
        lifetimeTask = new BukkitRunnable() {
            @Override public void run() { expireBoss(spawned); }
        };
        lifetimeTask.runTaskLater(plugin, lifetimeMinutos * 1200L);
        bossExpireMillis = System.currentTimeMillis() + lifetimeMinutos * 60000L;

        // Reancora o ciclo automático: o próximo auto-spawn passa a contar a partir
        // DESTE spawn (manual ou automático), ficando previsível ("intervalo depois que
        // o boss nasceu") em vez de um relógio fixo desde o boot do servidor.
        startAutoSpawn();
    }

    /** Adiciona/remove tickets de chunk (raio 2 = área 5x5) para manter o boss carregado e "tickando". */
    private void setForcedChunks(Location loc, boolean load) {
        if (loc == null || loc.getWorld() == null) return;
        World w = loc.getWorld();
        int cx = loc.getBlockX() >> 4, cz = loc.getBlockZ() >> 4;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (load) w.getChunkAt(cx + dx, cz + dz).addPluginChunkTicket(plugin);
                else      w.getChunkAt(cx + dx, cz + dz).removePluginChunkTicket(plugin);
            }
        }
        forcedChunkCenter = load ? loc.clone() : null;
    }

    /**
     * Chamado pelo controlador do boss quando a entidade fica inválida fora do fluxo normal
     * (ex.: removida por outro plugin/comando). Limpa o estado para o ciclo não travar.
     */
    public void onBossVanished(BossEntity who) {
        if (active == null || active != who) { if (who != null) who.remove(); return; }
        plugin.getLogger().warning("[Boss] " + who.getNamePlain()
                + " ficou inválido (sumiu) — limpando estado para liberar o próximo spawn.");
        despawn();
    }

    /**
     * Boss expirou (ninguém matou a tempo): vai embora. Quem causou dano ainda recebe uma
     * recompensa REDUZIDA ({@link #EXPIRE_REWARD_MULT}) — bem menos do que matar de fato.
     */
    private void expireBoss(BossEntity who) {
        if (active == null || active != who) return;   // já foi morto/trocado
        String name = active.getNamePlain();

        // Captura o dano ANTES de limpar (despawn zera o active).
        Map<UUID, Double> dmg = new HashMap<>(active.getDamageMap());
        Map<UUID, String> names = new HashMap<>(active.getDamagerNames());
        despawn();

        var s = plugin.getServer();
        s.sendMessage(MM.deserialize(" "));
        if (dmg.isEmpty()) {
            s.sendMessage(MM.deserialize("<#e22c27><bold>" + name + "</bold> <#cbd1d7>foi embora — ninguém o enfrentou a tempo!"));
            s.sendMessage(MM.deserialize("<#848c94>Um novo boss aparecerá mais tarde."));
        } else {
            List<Map.Entry<UUID, Double>> ranked = dmg.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .limit(5)
                    .collect(Collectors.toList());
            final String[] cor = {"#6817ff", "#cbd1d7", "#fc9d1a", "#b1fcb6", "#71b0ec"};
            s.sendMessage(MM.deserialize("<#e22c27><bold>" + name + "</bold> <#cbd1d7>resistiu e fugiu antes de ser derrotado!"));
            s.sendMessage(MM.deserialize("<#848c94>Quem mais causou dano leva uma recompensa <#6817ff>reduzida<#848c94>:"));
            for (int i = 0; i < ranked.size(); i++) {
                Map.Entry<UUID, Double> e = ranked.get(i);
                String pn = names.getOrDefault(e.getKey(), "?");
                int reward = (int) Math.round(REWARDS[i] * EXPIRE_REWARD_MULT);
                plugin.getEconomyManager().addCoins(e.getKey(), pn, reward);
                s.sendMessage(MM.deserialize(
                        "<" + cor[i] + ">#" + (i + 1) + " <#cbd1d7>" + pn
                        + " <#848c94>(" + String.format(java.util.Locale.US, "%,.0f", e.getValue()) + " dano) "
                        + "<#cbd1d7>-> <#10fc46>+" + String.format(java.util.Locale.US, "%,d", reward) + " coins"));
                Player wp = plugin.getServer().getPlayer(e.getKey());
                if (wp != null) wp.playSound(wp.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
            }
        }
        s.sendMessage(MM.deserialize(" "));
        for (Player p : s.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 1.4f);
    }

    private BossEntity createBoss(String type, Location loc) {
        if (type != null) {
            String t = type.toLowerCase();
            if (t.equals("spooky") || t.equals("pumpkin") || t.equals("pumpkinking"))
                return new PumpkinKing(plugin, this, loc);
            if (t.equals("tower") || t.equals("skeleton") || t.equals("towerskeleton"))
                return new TowerSkeleton(plugin, this, loc);
        }
        return new ShadowKnight(plugin, this, loc);   // padrão: Cavaleiro das Sombras
    }

    /** Mensagem periódica "o boss está vivo" (com a vida atual). */
    private void broadcastAlive() {
        if (active == null) return;
        String hp = String.format(java.util.Locale.US, "%,.2f", active.getHp());
        var s = plugin.getServer();
        s.sendMessage(MM.deserialize(" "));
        s.sendMessage(MM.deserialize("<#e22c27><bold>O BOSS <gradient:#ff1c1c:#bf0000>" + active.getNamePlain() + "</gradient> <#e22c27>está vivo na arena!"));
        s.sendMessage(MM.deserialize("<#cbd1d7>Derrote-o e receba recompensas incríveis!"));
        s.sendMessage(MM.deserialize("<#848c94>(recompensas para os 5 TOP dano e último hit)"));
        s.sendMessage(MM.deserialize(" "));
        s.sendMessage(MM.deserialize("<#cbd1d7>Vida atual: <#e7332d>" + hp + "❤"));
        s.sendMessage(MM.deserialize(" "));
        s.sendMessage(MM.deserialize("<#cbd1d7>Use <#6817ff>/boss <#cbd1d7>para ir até a arena."));
        s.sendMessage(MM.deserialize(" "));
    }

    public void despawn() {
        if (active != null) { active.remove(); active = null; }
        if (aliveTask != null) { aliveTask.cancel(); aliveTask = null; }
        if (lifetimeTask != null) { lifetimeTask.cancel(); lifetimeTask = null; }
        if (forcedChunkCenter != null) { setForcedChunks(forcedChunkCenter, false); }
        bossExpireMillis = 0L;
    }

    /** Chamado pelo ShadowKnight ao fim da animação de morte. Premia o TOP 5 em dano. */
    public void onBossDefeated(BossEntity boss, Map<UUID, Double> damage, Map<UUID, String> names) {
        if (boss != active) return;
        active = null;
        if (aliveTask != null) { aliveTask.cancel(); aliveTask = null; }
        if (lifetimeTask != null) { lifetimeTask.cancel(); lifetimeTask = null; }
        if (forcedChunkCenter != null) { setForcedChunks(forcedChunkCenter, false); }
        bossExpireMillis = 0L;

        List<Map.Entry<UUID, Double>> ranked = damage.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(5)
                .collect(Collectors.toList());

        final String[] cor = {"#6817ff", "#cbd1d7", "#fc9d1a", "#b1fcb6", "#71b0ec"};

        var s = plugin.getServer();
        s.sendMessage(MM.deserialize(" "));
        s.sendMessage(MM.deserialize("<gradient:#e22c27:#fc564c><bold>" + boss.getNamePlain() + "</bold></gradient> <#10fc46>foi derrotado!"));
        s.sendMessage(MM.deserialize("<#cbd1d7>Recompensas dos <#6817ff>TOP 5 <#cbd1d7>em dano:"));
        for (int i = 0; i < ranked.size(); i++) {
            Map.Entry<UUID, Double> e = ranked.get(i);
            String name = names.getOrDefault(e.getKey(), "?");
            int reward = REWARDS[i];
            plugin.getEconomyManager().addCoins(e.getKey(), name, reward);
            s.sendMessage(MM.deserialize(
                    "<" + cor[i] + ">#" + (i + 1) + " <#cbd1d7>" + name
                    + " <#848c94>(" + String.format(java.util.Locale.US, "%,.0f", e.getValue()) + " dano) "
                    + "<#cbd1d7>-> <#10fc46>+" + String.format(java.util.Locale.US, "%,d", reward) + " coins"));
            Player wp = plugin.getServer().getPlayer(e.getKey());
            if (wp != null) wp.playSound(wp.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        }
        s.sendMessage(MM.deserialize(" "));
    }

    // Rastreia dano, intercepta o golpe letal e dá o flinch.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (active == null || event.getEntity() != active.getBase()) return;

        if (active.isDead()) { event.setCancelled(true); return; }

        if (active.isShielded()) event.setDamage(event.getDamage() * 0.4); // Barreira de Sangue: -60% dano

        double dmg = event.getFinalDamage();
        if (event instanceof EntityDamageByEntityEvent ev) {
            Player p = resolvePlayer(ev.getDamager());
            if (p != null) active.recordDamage(p.getUniqueId(), p.getName(), dmg);
        }

        // Tira da vida VIRTUAL (pool de 10k; o atributo do mob é limitado a 1024).
        active.damageBoss(dmg);
        if (active.getHp() <= 0) {
            event.setCancelled(true);     // não morre "vanilla"; rodamos a sequência de morte
            active.startDeathSequence();
        } else {
            announceHealthThresholds();   // avisa 90/75/50/25% (uma vez cada, por boss)
            active.onHurt();
        }
    }

    private Player resolvePlayer(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }
}
