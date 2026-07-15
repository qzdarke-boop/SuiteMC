package com.psdk.adminabuse;

import com.psdk.PSDK;
import com.psdk.boss.ModelEngineHook;
import com.psdk.lootchest.LootRarity;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ADMIN ABUSE 2.0 — o show do servidor.
 *
 * Fluxo: /adminabuse abre a GUI -> "Iniciar o Show" roda a cutscene
 * (fade preto via Blindness/Darkness, 3s de tela preta, fade some,
 * Pigstep toca pra TODOS em qualquer lugar) e entrega o ARSENAL ao admin:
 *
 *   ☄ Bola de neve  -> meteoro com impacto (VFX do Cavaleiro) que lança todos pro alto
 *   🥚 Ovo           -> invoca um baú ÉPICO ou LENDÁRIO (broadcast se lendário)
 *   🌀 Wind charge   -> tornado que suga os jogadores e cospe pro céu
 *   ⚡ Vara de blaze -> raio em cadeia onde a mira apontar
 *
 * Eventos avulsos da GUI: Mining 2x (toggle), Chuva de Baús, Chuva de Coins,
 * Festa de Fogos. Tudo com broadcast/bossbar/som — estilo show ao vivo.
 */
public class AdminAbuseManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // ============== AJUSTES (EDITE AQUI) ==============
    private static final long FADE_TICKS = 80L;             // ~1s fechando + 3s preto (fade-out é automático)
    private static final long REVEAL_AT = FADE_TICKS + 30L; // quando o preto já sumiu -> música + arsenal
    private static final double METEOR_RADIUS = 8.0;        // raio do lançamento do meteoro
    private static final double METEOR_LAUNCH_Y = 1.35;     // força pra cima
    private static final int COIN_RAIN_AMOUNT = 250;        // coins por jogador na chuva de coins
    private static final double LEGENDARY_CHANCE = 0.35;    // chance do ovo dar baú LENDÁRIO
    // VFX do pack (mesmos blueprints do Cavaleiro das Sombras):
    private static final String VFX_RUPTURE = "vfx_earthquake_rupture_1";
    private static final String VFX_RUBBLES = "vfx_rubbles";
    // ==================================================

    private final PSDK plugin;

    private boolean active = false;
    private String hostName = "";
    private BukkitRunnable barsTask;
    private final List<BukkitRunnable> liveTasks = new ArrayList<>();
    private final Map<String, BossBar> eventBars = new LinkedHashMap<>();
    /** Quem foi lançado por um poder: dano de queda zerado até o timestamp. */
    private final Map<UUID, Long> noFall = new ConcurrentHashMap<>();
    /** Mobs de evento (galinhas etc.) pra limpar no stop. */
    private final List<Entity> spawnedMobs = new ArrayList<>();

    /** Multiplicador de coins da mineração (BlockMineListener multiplica por isso). */
    private volatile double miningMultiplier = 1.0;

    // ============== EVENTO AUTOMÁTICO (happy hour) ==============
    /** A cada quantos minutos sai uma "rajada" extra (coins + fogos) durante o evento. */
    private static final int AUTO_BURST_INTERVAL_MIN = 3;
    private boolean autoEventActive = false;
    private final List<BukkitRunnable> autoTasks = new ArrayList<>();
    private BossBar autoBar;

    public AdminAbuseManager(PSDK plugin) { this.plugin = plugin; }

    public boolean isActive() { return active; }
    public String getHostName() { return hostName; }
    public double getMiningMultiplier() { return miningMultiplier; }
    public boolean isMining2x() { return miningMultiplier > 1.0; }
    public boolean isAutoEventActive() { return autoEventActive; }

    // ════════════════════════════ INICIAR O SHOW ════════════════════════════
    public void start(Player admin) {
        if (active) { admin.sendMessage(MM.deserialize("<#FF0000>O show já está rolando! Use Encerrar antes.")); return; }
        active = true;
        hostName = admin.getName();

        // t=0: tela fecha no preto (fade-in do Blindness é automático) + som de tensão.
        for (Player p : Bukkit.getOnlinePlayers()) blackout(p, (int) FADE_TICKS + 20);
        playToAll("minecraft:block.beacon.activate", 1.2f, 0.6f);
        playToAll("minecraft:entity.warden.heartbeat", 1.4f, 0.8f);

        // t=30 (tela já preta): o anúncio aparece no escuro.
        later(30, () -> titleAll(
                "<gradient:#ff1c1c:#ffd031><bold>⚡ ADMIN ABUSE ⚡</bold></gradient>",
                "<#fcc850>" + hostName + " <white>assumiu o servidor...",
                500, 3000, 800));

        // t=REVEAL (preto sumiu com fade): bossbar + arsenal + caos.
        later(REVEAL_AT, () -> {
            if (!active) return;
            showEventBar("show", BossBar.bossBar(
                    MM.deserialize("<gradient:#ff1c1c:#ff8b2d:#ffd031><bold>⚡ ADMIN ABUSE — " + hostName + " ⚡</bold></gradient>"),
                    1.0f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_20));
            broadcast(" ");
            broadcast("<gradient:#ff1c1c:#ffd031><bold>⚡⚡ ADMIN ABUSE ATIVADO ⚡⚡</bold></gradient>");
            broadcast("<white>O <#fcc850>" + hostName + " <white>tomou o controle do servidor...");
            broadcast("<#a4a4a4>Corram. Sério.");
            broadcast(" ");
            playToAll("minecraft:entity.ender_dragon.growl", 0.9f, 1.3f);

            if (admin.isOnline()) {
                World w = admin.getWorld();
                w.strikeLightningEffect(admin.getLocation());
                w.spawnParticle(Particle.EXPLOSION_EMITTER, admin.getLocation(), 2, 0.5, 0.5, 0.5, 0);
                giveArsenal(admin);
            }
        });
        admin.sendMessage(MM.deserialize("<#10fc46>Show iniciado! O arsenal chega quando a tela clarear."));
    }

    /** Entrega (ou repõe) o arsenal de itens-poder. */
    public void giveArsenal(Player admin) {
        clearArsenal(admin);
        for (ItemStack item : AdminAbuseItems.arsenal(plugin)) admin.getInventory().addItem(item);
        admin.playSound(admin.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        admin.sendMessage(MM.deserialize("<#fcc850><bold>SEU ARSENAL CHEGOU:"));
        admin.sendMessage(MM.deserialize("  <#ff4e2d>☄ Cometa do Caos <#a4a4a4>- jogue e veja o meteoro cair"));
        admin.sendMessage(MM.deserialize("  <#fcc850>🥚 Ovo do Tesouro <#a4a4a4>- invoca um baú épico/lendário"));
        admin.sendMessage(MM.deserialize("  <#71f3ec>🌀 Tornado Portátil <#a4a4a4>- jogue e suga todo mundo"));
        admin.sendMessage(MM.deserialize("  <#ffe14e>⚡ Cajado do Trovão <#a4a4a4>- clique direito: raio em cadeia"));
        admin.sendMessage(MM.deserialize("  <#fcfcfc>🐔 Pena do Apocalipse <#a4a4a4>- clique direito: chuva de galinhas"));
        admin.sendMessage(MM.deserialize("  <#b85afc>🧲 Pérola Magnética <#a4a4a4>- jogue e puxa TODOS pra lá"));
        admin.sendMessage(MM.deserialize("  <#9ce8ff>🧊 Gelo Eterno <#a4a4a4>- clique direito: congela todo mundo 5s"));
        admin.sendMessage(MM.deserialize("  <#ff2222>☢ Nuke de Mentira <#a4a4a4>- clique direito: explosão nuclear fake"));
    }

    // ════════════════════════════ ENCERRAR ════════════════════════════
    public void stop() {
        boolean wasActive = active;
        active = false;
        hostName = "";
        miningMultiplier = 1.0;
        autoEventActive = false;
        autoBar = null;
        hideAllBars();
        for (BukkitRunnable t : autoTasks) { try { t.cancel(); } catch (Exception ignored) {} }
        autoTasks.clear();
        for (BukkitRunnable t : liveTasks) { try { t.cancel(); } catch (Exception ignored) {} }
        liveTasks.clear();
        for (Player p : Bukkit.getOnlinePlayers()) clearArsenal(p);
        for (Entity e : spawnedMobs) { if (e.isValid()) e.remove(); }
        spawnedMobs.clear();
        noFall.clear();
        restoreAllSizes();
        if (wasActive) {
            broadcast(" ");
            broadcast("<#e22c27><bold>⚡ ADMIN ABUSE ENCERRADO.");
            broadcast("<white>O servidor voltou ao normal... por enquanto.");
            broadcast(" ");
            playToAll("minecraft:block.beacon.deactivate", 1f, 0.8f);
        }
    }

    private void clearArsenal(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (AdminAbuseItems.powerOf(plugin, contents[i]) != null) p.getInventory().setItem(i, null);
        }
    }

    // ════════════════════════════ ☄ METEORO (bola de neve) ════════════════════════════
    public void meteorStrike(Location ground) {
        World w = ground.getWorld();
        if (w == null) return;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        final Location target = ground.clone();
        final double startOffX = (rng.nextBoolean() ? 1 : -1) * (6 + rng.nextDouble() * 5);
        final double startOffZ = (rng.nextBoolean() ? 1 : -1) * (6 + rng.nextDouble() * 5);
        final double fallH = 34;

        w.playSound(target, org.bukkit.Sound.ENTITY_WITHER_SHOOT, 1.4f, 0.5f);

        final BlockDisplay rock = w.spawn(target.clone().add(startOffX, fallH, startOffZ), BlockDisplay.class, d -> {
            d.setBlock(Material.MAGMA_BLOCK.createBlockData());
            Transformation tr = d.getTransformation();
            tr.getScale().set(2.2f, 2.2f, 2.2f);
            d.setTransformation(tr);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setGlowing(true);
            d.setPersistent(false);
        });
        // Registra a rocha pra ser limpa no stop()/onDisable — senão, se o evento
        // encerrar no meio da queda, o BlockDisplay fica "travado" parado no céu.
        spawnedMobs.add(rock);

        BukkitRunnable fall = new BukkitRunnable() {
            double prog = 0;   // 0 -> 1
            @Override public void run() {
                if (!rock.isValid()) { spawnedMobs.remove(rock); cancel(); liveTasks.remove(this); return; }
                // Se o show foi encerrado, remove a rocha imediatamente (sem deixar travada).
                if (!active) { rock.remove(); spawnedMobs.remove(rock); cancel(); liveTasks.remove(this); return; }
                prog += 0.07;
                double y = fallH * (1 - prog);
                Location at = target.clone().add(startOffX * (1 - prog), y, startOffZ * (1 - prog));
                rock.teleport(at);
                w.spawnParticle(Particle.FLAME, at.clone().add(0, 1.6, 0), 8, 0.3, 0.7, 0.3, 0.02);
                w.spawnParticle(Particle.LAVA, at.clone().add(0, 1.2, 0), 3, 0.2, 0.4, 0.2, 0);
                w.spawnParticle(Particle.LARGE_SMOKE, at.clone().add(0, 2.2, 0), 4, 0.25, 0.5, 0.25, 0.01);
                if (prog >= 1) {
                    rock.remove();
                    spawnedMobs.remove(rock);
                    meteorImpact(target);
                    cancel();
                    liveTasks.remove(this);
                }
            }
        };
        liveTasks.add(fall);
        fall.runTaskTimer(plugin, 0L, 1L);
    }

    private void meteorImpact(Location impact) {
        World w = impact.getWorld();
        if (w == null) return;
        // VFX 3D do pack (rachadura + escombros do boss) + explosão.
        vfxAt(VFX_RUPTURE, impact, "skill", 50);
        vfxAt(VFX_RUBBLES, impact, "skill", 50);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, impact.clone().add(0, 0.4, 0), 3, 0.8, 0.4, 0.8, 0);
        w.spawnParticle(Particle.LAVA, impact.clone().add(0, 0.5, 0), 40, 2, 0.6, 2, 0);
        shockwaveAt(impact, METEOR_RADIUS);
        w.playSound(impact, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1.8f, 0.6f);
        w.playSound(impact, org.bukkit.Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.8f);

        // Joga TODO MUNDO perto pro alto (sem dano — só voo; queda protegida).
        for (Player p : w.getPlayers()) {
            double dist = p.getLocation().distance(impact);
            if (dist > METEOR_RADIUS) continue;
            Vector away = p.getLocation().toVector().subtract(impact.toVector()).setY(0);
            if (away.lengthSquared() < 0.01) away = new Vector(0.3, 0, 0.3);
            away.normalize().multiply(0.55).setY(METEOR_LAUNCH_Y);
            p.setVelocity(p.getVelocity().add(away));
            protectFall(p, 10_000);
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.7f);
        }
    }

    // ════════════════════════════ 🥚 OVO DO TESOURO ════════════════════════════
    public void eggChest(String throwerName, Location hitLoc) {
        var lc = plugin.getLootChestManager();
        if (lc == null) return;
        boolean legendary = ThreadLocalRandom.current().nextDouble() < LEGENDARY_CHANCE;
        LootRarity rarity = legendary ? LootRarity.LENDARIO : LootRarity.EPICO;
        boolean ok = lc.spawnChest(rarity);
        if (!ok) {   // sem coordenada livre dessa raridade -> tenta a outra
            rarity = legendary ? LootRarity.EPICO : LootRarity.LENDARIO;
            legendary = rarity == LootRarity.LENDARIO;
            ok = lc.spawnChest(rarity);
        }

        World w = hitLoc.getWorld();
        if (w != null) {
            w.spawnParticle(Particle.TOTEM_OF_UNDYING, hitLoc.clone().add(0, 1, 0), 50, 0.8, 1, 0.8, 0.25);
            w.playSound(hitLoc, org.bukkit.Sound.ITEM_TOTEM_USE, 1f, 1.3f);
        }
        if (!ok) {
            Player thrower = Bukkit.getPlayerExact(throwerName);
            if (thrower != null) thrower.sendMessage(MM.deserialize("<#FF0000>Não havia local livre pra spawnar o baú!"));
            return;
        }

        if (legendary) {
            broadcast(" ");
            broadcast("<gradient:#fcc850:#ff8b2d><bold>✦✦ BAÚ LENDÁRIO INVOCADO ✦✦</bold></gradient>");
            broadcast("<white>O <#fcc850>" + throwerName + " <white>spawnou um <#fcc850><bold>BAÚ LENDÁRIO</bold> <white>para todos!");
            broadcast(" ");
            titleAll("<#fcc850><bold>✦ BAÚ LENDÁRIO ✦</bold>",
                    "<white>spawnado por <#fcc850>" + throwerName, 200, 2500, 600);
            playToAll("minecraft:entity.ender_dragon.growl", 1f, 1.5f);
            playToAll("minecraft:ui.toast.challenge_complete", 1f, 1.1f);
        } else {
            broadcast("<#b85afc><bold>✦ <white>O <#fcc850>" + throwerName
                    + " <white>spawnou um <#b85afc>BAÚ ÉPICO<white> para todos!");
            playToAll("minecraft:entity.player.levelup", 1f, 0.8f);
        }
    }

    // ════════════════════════════ 🌀 TORNADO (wind charge) ════════════════════════════
    public void tornadoAt(Location center) {
        World w = center.getWorld();
        if (w == null) return;
        w.playSound(center, org.bukkit.Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.5f, 0.6f);

        BukkitRunnable spin = new BukkitRunnable() {
            int t = 0;
            final int DURATION = 100;   // 5s
            @Override public void run() {
                if (!active && t > 0) { cancel(); liveTasks.remove(this); return; }
                t++;
                // espiral de nuvens subindo (3 braços)
                for (int arm = 0; arm < 3; arm++) {
                    for (int hStep = 0; hStep < 8; hStep++) {
                        double h = hStep * 1.1;
                        double r = 0.6 + (h / 8.0) * 3.0;
                        double ang = t * 0.45 + arm * (2 * Math.PI / 3) + h * 0.5;
                        Location at = center.clone().add(Math.cos(ang) * r, h, Math.sin(ang) * r);
                        w.spawnParticle(Particle.CLOUD, at, 1, 0.05, 0.05, 0.05, 0.01);
                    }
                }
                if (t % 8 == 0) {
                    w.spawnParticle(Particle.GUST, center.clone().add(0, 0.4, 0), 1, 0.3, 0.1, 0.3, 0);
                    w.playSound(center, org.bukkit.Sound.ENTITY_BREEZE_IDLE_GROUND, 1f, 0.7f);
                }

                // suga jogadores pro centro; perto do olho -> levanta girando
                for (Player p : w.getPlayers()) {
                    double dist = p.getLocation().distance(center);
                    if (dist > 9) continue;
                    protectFall(p, 10_000);
                    Vector pull = center.toVector().subtract(p.getLocation().toVector()).setY(0);
                    if (dist > 1.8) {
                        pull.normalize().multiply(0.42);
                        p.setVelocity(p.getVelocity().multiply(0.5).add(pull));
                    } else {
                        p.setVelocity(new Vector(Math.cos(t * 0.5) * 0.25, 0.55, Math.sin(t * 0.5) * 0.25));
                    }
                }

                if (t >= DURATION) {   // FINAL: cospe todo mundo pro céu
                    w.spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0, 1, 0), 2, 0.5, 0.5, 0.5, 0);
                    w.playSound(center, org.bukkit.Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.8f, 1.2f);
                    for (Player p : w.getPlayers()) {
                        if (p.getLocation().distance(center) > 6) continue;
                        p.setVelocity(new Vector(0, 1.5, 0));
                        protectFall(p, 15_000);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 80, 0, false, false, false));
                    }
                    cancel();
                    liveTasks.remove(this);
                }
            }
        };
        liveTasks.add(spin);
        spin.runTaskTimer(plugin, 0L, 1L);
    }

    // ════════════════════════════ ⚡ RAIO EM CADEIA (vara de blaze) ════════════════════════════
    public void thunderAt(Location target) {
        World w = target.getWorld();
        if (w == null) return;
        w.strikeLightningEffect(target);
        w.spawnParticle(Particle.ELECTRIC_SPARK, target.clone().add(0, 0.6, 0), 40, 1, 0.8, 1, 0.15);
        // corrente: o raio "pula" pros jogadores próximos do ponto (sem dano, só susto + empurrão)
        int jumps = 0;
        for (Player p : w.getPlayers()) {
            if (jumps >= 3) break;
            double dist = p.getLocation().distance(target);
            if (dist > 7 || dist < 0.5) continue;
            w.strikeLightningEffect(p.getLocation());
            p.setVelocity(p.getVelocity().add(new Vector(0, 0.55, 0)));
            protectFall(p, 8_000);
            jumps++;
        }
        playToAll("minecraft:entity.lightning_bolt.thunder", 0.7f, 1.0f);
    }

    // ════════════════════════════ 🐔 APOCALIPSE DE GALINHAS (pena) ════════════════════════════
    public void chickenRain(Location center) {
        World w = center.getWorld();
        if (w == null) return;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        w.playSound(center, org.bukkit.Sound.ENTITY_CHICKEN_AMBIENT, 2f, 0.6f);
        for (int i = 0; i < 30; i++) {
            long delay = i * 3L;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Location at = center.clone().add(rng.nextDouble(-6, 6), 10 + rng.nextDouble() * 6, rng.nextDouble(-6, 6));
                Chicken chicken = w.spawn(at, Chicken.class, c -> {
                    c.setPersistent(false);
                    c.setRemoveWhenFarAway(true);
                    if (rng.nextDouble() < 0.3) c.setBaby();
                });
                spawnedMobs.add(chicken);
                w.spawnParticle(Particle.CLOUD, at, 4, 0.2, 0.2, 0.2, 0.02);
                w.playSound(at, org.bukkit.Sound.ENTITY_CHICKEN_HURT, 0.8f, rng.nextFloat() * 0.6f + 0.8f);
                // some sozinha depois de ~12s, com pufe
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (chicken.isValid()) {
                        chicken.getWorld().spawnParticle(Particle.POOF, chicken.getLocation().add(0, 0.4, 0), 8, 0.2, 0.2, 0.2, 0.02);
                        chicken.remove();
                    }
                    spawnedMobs.remove(chicken);
                }, 240L);
            }, delay);
        }
    }

    // ════════════════════════════ 🧲 PÉROLA MAGNÉTICA (ender pearl) ════════════════════════════
    public void gatherAt(Location center, String throwerName) {
        World w = center.getWorld();
        if (w == null) return;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        broadcast("<#b85afc><bold>🧲 <white>O <#fcc850>" + throwerName + " <white>puxou TODO MUNDO pra perto dele!");
        playToAll("minecraft:entity.enderman.teleport", 1f, 0.7f);
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location spot = center.clone().add(rng.nextDouble(-2, 2), 0.3, rng.nextDouble(-2, 2));
            spot.setYaw(p.getLocation().getYaw());
            spot.setPitch(p.getLocation().getPitch());
            p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.4);
            p.teleport(spot);
            w.spawnParticle(Particle.PORTAL, spot.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.4);
        }
        w.spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
    }

    // ════════════════════════════ 🔀 EMBARALHAR JOGADORES (GUI) ════════════════════════════
    public void shuffleAll(Player admin) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.size() < 2) {
            admin.sendMessage(MM.deserialize("<#FF0000>Precisa de pelo menos 2 jogadores online!"));
            return;
        }
        broadcast(" ");
        broadcast("<gradient:#b85afc:#71f3ec><bold>🔀 EMBARALHADOS! 🔀</bold></gradient>");
        broadcast("<white>Todo mundo trocou de lugar com alguém. Boa sorte achando o caminho de volta!");
        broadcast(" ");
        titleAll("<#b85afc><bold>🔀 TROCA-TROCA 🔀</bold>", "<white>cadê você agora?", 200, 2000, 500);

        List<Location> spots = new ArrayList<>(players.size());
        for (Player p : players) spots.add(p.getLocation().clone());
        Collections.shuffle(spots, java.util.concurrent.ThreadLocalRandom.current());
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.5);
            p.teleport(spots.get(i));
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f);
        }
        admin.sendMessage(MM.deserialize("<#10fc46>" + players.size() + " jogadores embaralhados!"));
    }

    // ════════════════════════════ 🚀 FOGUETE COLETIVO (GUI) ════════════════════════════
    public void rocketAll(Player admin) {
        broadcast(" ");
        broadcast("<gradient:#71f3ec:#ff5af0><bold>🚀 FOGUETE COLETIVO! 🚀</bold></gradient>");
        broadcast("<white>TODO MUNDO foi lançado pro céu. Aproveitem a vista!");
        broadcast(" ");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setVelocity(new Vector(0, 2.8, 0));
            protectFall(p, 30_000);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 320, 0, false, false, true));
            p.getWorld().spawnParticle(Particle.FIREWORK, p.getLocation(), 40, 0.4, 0.3, 0.4, 0.12);
            p.getWorld().spawnParticle(Particle.GUST, p.getLocation(), 1, 0, 0, 0, 0);
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.5f, 0.8f);
        }
        admin.sendMessage(MM.deserialize("<#10fc46>Todo mundo voando!"));
    }

    // ════════════════════════════ 💨 MODO TURBO (GUI) ════════════════════════════
    public void turboAll(Player admin) {
        broadcast(" ");
        broadcast("<#71f3ec><bold>💨 MODO TURBO ATIVADO! 💨");
        broadcast("<white>Velocidade + força nos braços + super pulo pra todo mundo por <#71f3ec>30 segundos<white>!");
        broadcast(" ");
        titleAll("<#71f3ec><bold>💨 MODO TURBO 💨</bold>", "<white>30 segundos de velocidade máxima!", 200, 2000, 500);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 1, false, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 600, 1, false, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 600, 1, false, false, true));
            protectFall(p, 35_000);
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_HORSE_GALLOP, 1f, 1.4f);
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 16, 0.4, 0.2, 0.4, 0.06);
        }
        admin.sendMessage(MM.deserialize("<#10fc46>Turbo entregue a todos!"));
    }

    // ════════════════════════════ 🧊 GELO ETERNO (item) ════════════════════════════
    public void freezeAll(Player caster) {
        broadcast("<#9ce8ff><bold>🧊 <white>O <#fcc850>" + caster.getName()
                + " <white>CONGELOU todo mundo por <#9ce8ff>5 segundos<white>!");
        playToAll("minecraft:block.glass.break", 1f, 0.6f);
        playToAll("minecraft:entity.player.hurt_freeze", 1f, 0.8f);
        List<Player> frozen = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(caster)) continue;
            frozen.add(p);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 9, false, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 4, false, false, false));
        }
        BukkitRunnable shake = new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t += 5;
                for (Player p : frozen) {
                    if (!p.isOnline()) continue;
                    p.setFreezeTicks(130);   // tremedeira + vinheta de gelo (abaixo de 140 = sem dano)
                    p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1, 0), 6, 0.3, 0.6, 0.3, 0.01);
                }
                if (t >= 100) {
                    for (Player p : frozen) if (p.isOnline()) p.setFreezeTicks(0);
                    cancel();
                    liveTasks.remove(this);
                }
            }
        };
        liveTasks.add(shake);
        shake.runTaskTimer(plugin, 0L, 5L);
    }

    // ════════════════════════════ ☢ NUKE DE MENTIRA (item) ════════════════════════════
    public void nukeAt(Location center) {
        World w = center.getWorld();
        if (w == null) return;
        titleAll("<#ff2222><bold>☢ NUKE ☢</bold>", "<white>abaixem-se!", 100, 1500, 600);
        playToAll("minecraft:entity.wither.spawn", 1f, 0.6f);
        w.spawnParticle(Particle.FLASH, center.clone().add(0, 1, 0), 4, 0.5, 0.5, 0.5, 0);

        // barragem de explosões + cogumelo de fumaça subindo
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < 7; i++) {
            final int step = i;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Location at = center.clone().add(rng.nextDouble(-3, 3), step * 0.4, rng.nextDouble(-3, 3));
                w.spawnParticle(Particle.EXPLOSION_EMITTER, at, 2, 0.5, 0.3, 0.5, 0);
                w.playSound(at, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1.8f, 0.5f + step * 0.05f);
            }, i * 3L);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (double h = 0; h <= 14; h += 2) {
                w.spawnParticle(Particle.LARGE_SMOKE, center.clone().add(0, h, 0), 14, 1.0 + h * 0.1, 0.8, 1.0 + h * 0.1, 0.01);
            }
            int pts = 26;   // "chapéu" do cogumelo
            for (int k = 0; k < pts; k++) {
                double a = 2 * Math.PI * k / pts;
                w.spawnParticle(Particle.LARGE_SMOKE, center.clone().add(Math.cos(a) * 4.5, 14, Math.sin(a) * 4.5), 4, 0.4, 0.4, 0.4, 0.01);
            }
            w.playSound(center, org.bukkit.Sound.ENTITY_WARDEN_SONIC_BOOM, 1.4f, 0.6f);
        }, 10L);
        shockwaveAt(center, 14);

        // arremessa todo mundo num raio gigante (sem dano de queda)
        for (Player p : w.getPlayers()) {
            double dist = p.getLocation().distance(center);
            if (dist > 14) continue;
            Vector away = p.getLocation().toVector().subtract(center.toVector()).setY(0);
            if (away.lengthSquared() < 0.01) away = new Vector(0.3, 0, 0.3);
            away.normalize().multiply(0.85).setY(1.6);
            p.setVelocity(p.getVelocity().add(away));
            protectFall(p, 12_000);
        }
    }

    // ════════════════════════════ 🧟 INVASÃO ZUMBI (GUI) ════════════════════════════
    public void zombieInvasion(Player admin) {
        broadcast(" ");
        broadcast("<#5b8731><bold>🧟 INVASÃO ZUMBI! 🧟");
        broadcast("<white>Eles estão por TODA PARTE... mas a mordida deles é fraca demais. CORRAM (de rir)!");
        broadcast(" ");
        titleAll("<#5b8731><bold>🧟 INVASÃO ZUMBI 🧟</bold>", "<white>eles não machucam... muito.", 200, 2000, 500);
        playToAll("minecraft:entity.zombie.ambient", 1.6f, 0.6f);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (int i = 0; i < 4; i++) {
                Location at = p.getLocation().clone().add(rng.nextDouble(-5, 5), 0.5, rng.nextDouble(-5, 5));
                Zombie z = p.getWorld().spawn(at, Zombie.class, zb -> {
                    zb.setBaby();                        // bebês = mais engraçado e mais rápido
                    zb.setShouldBurnInDay(false);
                    zb.setPersistent(false);
                    zb.setRemoveWhenFarAway(true);
                    var atk = zb.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
                    if (atk != null) atk.setBaseValue(0.0);   // perseguem mas NÃO machucam
                });
                z.setTarget(p);
                spawnedMobs.add(z);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (z.isValid()) {
                        z.getWorld().spawnParticle(Particle.POOF, z.getLocation().add(0, 0.4, 0), 8, 0.2, 0.2, 0.2, 0.02);
                        z.remove();
                    }
                    spawnedMobs.remove(z);
                }, 300L);
            }
        }
        admin.sendMessage(MM.deserialize("<#10fc46>Horda liberada! Some sozinha em 15s."));
    }

    // ════════════════════════════ 🎰 SORTEIO RELÂMPAGO (GUI) ════════════════════════════
    public void lotteryDraw(Player admin) {
        broadcast(" ");
        broadcast("<gradient:#ffe14e:#ff8b2d><bold>🎰 SORTEIO RELÂMPAGO! 🎰</bold></gradient>");
        broadcast("<white>Girando a roleta... alguém vai levar <#ffe14e><bold>500 COINS</bold><white>!");
        broadcast(" ");
        playToAll("minecraft:block.note_block.pling", 1f, 1.2f);
        BukkitRunnable suspense = new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                playToAll("minecraft:ui.button.click", 0.8f, 0.8f + t * 0.15f);
                if (t >= 6) {
                    List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
                    if (!players.isEmpty()) {
                        Player winner = players.get(ThreadLocalRandom.current().nextInt(players.size()));
                        plugin.getEconomyManager().addCoins(winner.getUniqueId(), winner.getName(), 500);
                        broadcast(" ");
                        broadcast("<#ffe14e><bold>🏆 <white>O grande sorteado foi... <#ffe14e><bold>" + winner.getName()
                                + "</bold><white>! Levou <#ffe14e>+500 coins<white>!");
                        broadcast(" ");
                        titleAll("<#ffe14e><bold>🏆 " + winner.getName() + " 🏆</bold>", "<white>ganhou 500 coins!", 200, 2500, 600);
                        playToAll("minecraft:ui.toast.challenge_complete", 1f, 1f);
                        for (int i = 0; i < 3; i++) {
                            final Location at = winner.getLocation().clone().add(ThreadLocalRandom.current().nextDouble(-2, 2), 0.5, ThreadLocalRandom.current().nextDouble(-2, 2));
                            plugin.getServer().getScheduler().runTaskLater(plugin, () -> spawnFirework(at), i * 8L);
                        }
                    }
                    cancel();
                    liveTasks.remove(this);
                }
            }
        };
        liveTasks.add(suspense);
        suspense.runTaskTimer(plugin, 10L, 10L);
    }

    // ════════════════════════════ 🤏 TAMANHO ALEATÓRIO (GUI) ════════════════════════════
    /** Jogadores que estão com escala alterada — restaurados no fim/quit/join/stop. */
    private final java.util.Set<UUID> sizedPlayers = ConcurrentHashMap.newKeySet();

    public void sizeChaos(Player admin) {
        broadcast(" ");
        broadcast("<gradient:#ff5af0:#ffe14e><bold>🤏 TAMANHO ALEATÓRIO! 🤏</bold></gradient>");
        broadcast("<white>Cada um virou um tamanho diferente por <#ffe14e>30 segundos<white>. Formiga ou gigante?");
        broadcast(" ");
        titleAll("<#ff5af0><bold>🤏 TAMANHO ALEATÓRIO 🤏</bold>", "<white>se olha agora kkkkk", 200, 2000, 500);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (Player p : Bukkit.getOnlinePlayers()) {
            var scale = p.getAttribute(org.bukkit.attribute.Attribute.SCALE);
            if (scale == null) continue;
            double s = rng.nextBoolean() ? rng.nextDouble(0.3, 0.6) : rng.nextDouble(1.6, 2.5);
            scale.setBaseValue(s);
            sizedPlayers.add(p.getUniqueId());
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, s < 1 ? 1.8f : 0.6f);
            p.getWorld().spawnParticle(Particle.POOF, p.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.03);
        }
        later(600, this::restoreAllSizes);
        admin.sendMessage(MM.deserialize("<#10fc46>Tamanhos embaralhados por 30s!"));
    }

    private void restoreSize(Player p) {
        var scale = p.getAttribute(org.bukkit.attribute.Attribute.SCALE);
        if (scale != null) scale.setBaseValue(1.0);
        sizedPlayers.remove(p.getUniqueId());
    }

    private void restoreAllSizes() {
        for (Player p : Bukkit.getOnlinePlayers())
            if (sizedPlayers.contains(p.getUniqueId())) restoreSize(p);
    }

    // ════════════════════════════ 💣 BATATA QUENTE (GUI) ════════════════════════════
    public void hotPotato(Player admin) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) return;
        final Player potato = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        broadcast(" ");
        broadcast("<#ff8b2d><bold>💣 BATATA QUENTE! 💣");
        broadcast("<white>O <#ff8b2d><bold>" + potato.getName() + "</bold><white> está com a bomba! Explode em <#ff8b2d>10 segundos<white> — SAIAM DE PERTO!");
        broadcast(" ");
        potato.showTitle(Title.title(
                MM.deserialize("<#ff8b2d><bold>💣 VOCÊ É A BATATA 💣</bold>"),
                MM.deserialize("<white>corre pra perto dos outros!"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(500))));
        potato.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, false, false));

        BukkitRunnable tick = new BukkitRunnable() {
            int sec = 0;
            @Override public void run() {
                sec++;
                if (!potato.isOnline()) { cancel(); liveTasks.remove(this); return; }
                playToAll("minecraft:block.note_block.hat", 1f, 0.8f + sec * 0.12f);
                potato.getWorld().spawnParticle(Particle.LAVA, potato.getLocation().add(0, 1, 0), 3, 0.3, 0.5, 0.3, 0);
                if (sec >= 10) {   // 💥 estourou!
                    Location at = potato.getLocation();
                    World w = at.getWorld();
                    w.spawnParticle(Particle.EXPLOSION_EMITTER, at.clone().add(0, 0.5, 0), 3, 0.6, 0.4, 0.6, 0);
                    w.playSound(at, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1.8f, 0.7f);
                    shockwaveAt(at, 6);
                    for (Player p : w.getPlayers()) {
                        if (p.getLocation().distance(at) > 6) continue;
                        Vector away = p.getLocation().toVector().subtract(at.toVector()).setY(0);
                        if (away.lengthSquared() < 0.01) away = new Vector(0.3, 0, 0.3);
                        away.normalize().multiply(0.6).setY(1.3);
                        p.setVelocity(p.getVelocity().add(away));
                        protectFall(p, 10_000);
                    }
                    broadcast("<#ff8b2d><bold>💥 <white>A batata estourou no <#ff8b2d>" + potato.getName() + "<white>!");
                    cancel();
                    liveTasks.remove(this);
                }
            }
        };
        liveTasks.add(tick);
        tick.runTaskTimer(plugin, 20L, 20L);
    }

    // ════════════════════════════ 🌈 RASTRO ARCO-ÍRIS (GUI) ════════════════════════════
    public void rainbowTrails(Player admin) {
        broadcast(" ");
        broadcast("<gradient:#ff1c1c:#ffe14e:#10fc46:#71b0ec:#b85afc><bold>🌈 RASTRO ARCO-ÍRIS! 🌈</bold></gradient>");
        broadcast("<white>Todo mundo deixa um rastro colorido por <#71f3ec>60 segundos<white>!");
        broadcast(" ");
        playToAll("minecraft:block.amethyst_block.chime", 1f, 1.2f);
        BukkitRunnable trail = new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t += 2;
                float hue = (t % 60) / 60f;
                int rgb = java.awt.Color.HSBtoRGB(hue, 1f, 1f);
                Particle.DustOptions dust = new Particle.DustOptions(
                        Color.fromRGB(rgb & 0xFFFFFF), 1.5f);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 0.2, 0), 3, 0.25, 0.05, 0.25, 0, dust);
                }
                if (t >= 1200) { cancel(); liveTasks.remove(this); }
            }
        };
        liveTasks.add(trail);
        trail.runTaskTimer(plugin, 0L, 2L);
        admin.sendMessage(MM.deserialize("<#10fc46>Arco-íris ligado por 60s!"));
    }

    // ════════════════════════════ ⚡ TEMPESTADE (GUI) ════════════════════════════
    public void thunderstorm(Player admin) {
        broadcast(" ");
        broadcast("<#ffe14e><bold>⚡ TEMPESTADE ELÉTRICA! ⚡");
        broadcast("<white>Raios caindo por todo lado durante <#ffe14e>20 segundos<white>. (Eles não machucam... eu acho.)");
        broadcast(" ");
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        BukkitRunnable storm = new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t += 10;
                List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
                if (!players.isEmpty()) {
                    Player p = players.get(rng.nextInt(players.size()));
                    Location near = p.getLocation().clone().add(rng.nextDouble(-8, 8), 0, rng.nextDouble(-8, 8));
                    p.getWorld().strikeLightningEffect(near);   // só efeito: sem fogo, sem dano
                }
                if (t >= 400) { cancel(); liveTasks.remove(this); }
            }
        };
        liveTasks.add(storm);
        storm.runTaskTimer(plugin, 0L, 10L);
        admin.sendMessage(MM.deserialize("<#10fc46>Tempestade no ar!"));
    }

    // ════════════════════════════ proteção de queda ════════════════════════════
    /** Zera o dano de queda do jogador pelos próximos {@code ms} milissegundos. */
    private void protectFall(Player p, long ms) {
        noFall.put(p.getUniqueId(), System.currentTimeMillis() + ms);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player p)) return;
        Long until = noFall.get(p.getUniqueId());
        if (until == null) return;
        if (System.currentTimeMillis() <= until) event.setCancelled(true);
        else noFall.remove(p.getUniqueId());
    }

    // ════════════════════════════ ⛏ MINING 2X (GUI) ════════════════════════════
    public void toggleMining2x(Player admin) {
        if (isMining2x()) disableMining2x(); else enableMining2x();
        admin.sendMessage(MM.deserialize("<#10fc46>Mining 2x: <#fcc850>" + (isMining2x() ? "LIGADO" : "DESLIGADO")));
    }

    /** Liga a mineração 2x (com aviso/bossbar) — usado pelo toggle e pelo evento automático. */
    private void enableMining2x() {
        miningMultiplier = 2.0;
        showEventBar("mining", BossBar.bossBar(
                MM.deserialize("<gradient:#10fc46:#55ffff><bold>⛏ MINING 2X ⛏</bold></gradient>"),
                1.0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS));
        broadcast(" ");
        broadcast("<#10fc46><bold>⛏ MINERAÇÃO 2X ATIVADA! ⛏");
        broadcast("<white>Todo minério vale <#10fc46><bold>DOBRO</bold><white> de coins. VAI VAI VAI!");
        broadcast(" ");
        titleAll("<#10fc46><bold>⛏ MINING 2X ⛏</bold>", "<white>todo minério vale dobro!", 200, 2000, 500);
        playToAll("minecraft:entity.experience_orb.pickup", 1f, 0.6f);
        playToAll("minecraft:block.note_block.pling", 1f, 1.6f);
    }

    /** Desliga a mineração 2x. */
    private void disableMining2x() {
        miningMultiplier = 1.0;
        hideEventBar("mining");
        broadcast("<#fcc850><bold>⛏ Mineração 2x encerrada. <white>Voltou ao normal.");
    }

    // ════════════════════════════ ☄ CHUVA DE BAÚS (GUI) ════════════════════════════
    public void chestAll(Player admin) {
        if (!doChestAll()) { admin.sendMessage(MM.deserialize("<#FF0000>Sistema de baús indisponível.")); return; }
        admin.sendMessage(MM.deserialize("<#10fc46>Chuva de baús disparada!"));
    }

    private boolean doChestAll() {
        var lc = plugin.getLootChestManager();
        if (lc == null) return false;
        broadcast(" ");
        broadcast("<#fc9d1a><bold>☄ CHUVA DE BAÚS! ☄");
        broadcast("<white>Baús de TODAS as raridades estão caindo AGORA. CORRAM!");
        broadcast(" ");
        titleAll("<#fc9d1a><bold>☄ CHUVA DE BAÚS ☄</bold>", "<white>corram atrás deles!", 200, 2000, 500);
        playToAll("minecraft:item.totem.use", 1f, 0.7f);
        for (LootRarity rarity : LootRarity.values()) {
            int n = Math.max(2, rarity.getSpawnCount() * 2);
            for (int i = 0; i < n; i++)
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> lc.spawnChest(rarity), (long) i * 8L);
        }
        return true;
    }

    // ════════════════════════════ 💰 CHUVA DE COINS (GUI) ════════════════════════════
    public void coinRain(Player admin) {
        doCoinRain(admin.getName());
        admin.sendMessage(MM.deserialize("<#10fc46>Chuva de coins entregue a todos os online!"));
    }

    private void doCoinRain(String host) {
        broadcast(" ");
        broadcast("<#ffe14e><bold>💰 CHUVA DE COINS! 💰");
        broadcast("<white>TODO MUNDO ganhou <#ffe14e><bold>+" + COIN_RAIN_AMOUNT + " coins</bold><white>!");
        broadcast(" ");
        titleAll("<#ffe14e><bold>💰 +" + COIN_RAIN_AMOUNT + " COINS 💰</bold>",
                "<white>cortesia do <#fcc850>" + host, 200, 2000, 500);
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.getEconomyManager().addCoins(p.getUniqueId(), p.getName(), COIN_RAIN_AMOUNT);
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.8f);
            p.getWorld().spawnParticle(Particle.WAX_OFF, p.getLocation().add(0, 1.2, 0), 30, 0.6, 0.8, 0.6, 0.3);
        }
    }

    // ════════════════════════════ 🎆 FESTA DE FOGOS (GUI) ════════════════════════════
    public void fireworksParty(Player admin) {
        doFireworksParty();
        admin.sendMessage(MM.deserialize("<#10fc46>Festa de fogos disparada!"));
    }

    private void doFireworksParty() {
        broadcast(" ");
        broadcast("<gradient:#ff5af0:#71f3ec><bold>🎆 FESTA NO SERVIDOR! 🎆</bold></gradient>");
        broadcast("<white>Fogos + <#71f3ec>SUPER PULO<white> pra todo mundo por 15 segundos!");
        broadcast(" ");
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 300, 2, false, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 300, 0, false, false, true));
            for (int i = 0; i < 4; i++) {
                Location at = p.getLocation().clone().add(rng.nextDouble(-4, 4), 0.5, rng.nextDouble(-4, 4));
                long delay = i * 9L;
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> spawnFirework(at), delay);
            }
        }
        playToAll("minecraft:entity.firework_rocket.large_blast", 1f, 1f);
    }

    // ════════════════════════════ ⏰ EVENTO AUTOMÁTICO (happy hour) ════════════════════════════
    /**
     * Dispara o evento automático: liga Mineração 2x e solta Chuva de Coins + Chuva de
     * Baús + Festa de Fogos de uma vez, mantendo o 2x e rajadas extras de coins/fogos
     * durante {@code durationMinutes}. No fim, desliga sozinho. Sem arsenal/admin.
     * @return false se já houver um evento automático em andamento.
     */
    public boolean startAutoEvent(int durationMinutes) {
        if (autoEventActive) return false;
        if (durationMinutes <= 0) durationMinutes = 15;
        autoEventActive = true;
        final int totalSec = durationMinutes * 60;

        broadcast(" ");
        broadcast("<gradient:#ff1c1c:#ff8b2d:#ffd031><bold>⚡⚡ EVENTO RELÂMPAGO ⚡⚡</bold></gradient>");
        broadcast("<white>Por <#fcc850>" + durationMinutes + " minutos<white>: <#10fc46>Mineração 2x<white>, "
                + "<#ffe14e>Chuva de Coins<white>, <#fc9d1a>Chuva de Baús<white> e <#ff5af0>Fogos<white>!");
        broadcast(" ");
        titleAll("<gradient:#ff1c1c:#ffd031><bold>⚡ EVENTO RELÂMPAGO ⚡</bold></gradient>",
                "<white>aproveite por <#fcc850>" + durationMinutes + " min", 300, 2500, 600);
        playToAll("minecraft:ui.toast.challenge_complete", 1f, 1f);
        playToAll("minecraft:entity.ender_dragon.growl", 0.7f, 1.4f);

        // Rajada inicial: tudo de uma vez.
        enableMining2x();
        doCoinRain("Servidor");
        doChestAll();
        doFireworksParty();

        // Bossbar de contagem do evento.
        autoBar = BossBar.bossBar(autoBarName(totalSec),
                1.0f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        showEventBar("auto", autoBar);

        // Task de 1s: atualiza a bossbar, solta rajadas extras e encerra no fim.
        BukkitRunnable tick = new BukkitRunnable() {
            int elapsed = 0;
            @Override public void run() {
                if (!autoEventActive) { cancel(); return; }
                elapsed++;
                int left = totalSec - elapsed;
                if (autoBar != null) {
                    autoBar.progress(Math.max(0f, Math.min(1f, left / (float) totalSec)));
                    autoBar.name(autoBarName(left));
                }
                if (left > 0 && elapsed % (AUTO_BURST_INTERVAL_MIN * 60) == 0) {
                    doCoinRain("Servidor");
                    doFireworksParty();
                }
                if (left <= 0) {
                    cancel();
                    stopAutoEvent();
                }
            }
        };
        tick.runTaskTimer(plugin, 20L, 20L);
        autoTasks.add(tick);
        return true;
    }

    /** Encerra o evento automático (chamado no fim do tempo, no /adminabuse stop e no onDisable). */
    public void stopAutoEvent() {
        if (!autoEventActive) return;
        autoEventActive = false;
        for (BukkitRunnable t : autoTasks) { try { t.cancel(); } catch (Exception ignored) {} }
        autoTasks.clear();
        hideEventBar("auto");
        autoBar = null;
        if (isMining2x()) disableMining2x();
        broadcast(" ");
        broadcast("<#e22c27><bold>⚡ EVENTO RELÂMPAGO ENCERRADO.");
        broadcast("<white>Valeu por participar! O próximo vem logo.");
        broadcast(" ");
        playToAll("minecraft:block.beacon.deactivate", 1f, 1f);
    }

    private net.kyori.adventure.text.Component autoBarName(int secondsLeft) {
        int m = Math.max(0, secondsLeft) / 60;
        int s = Math.max(0, secondsLeft) % 60;
        return MM.deserialize(String.format(
                "<gradient:#ff1c1c:#ff8b2d:#ffd031><bold>⚡ EVENTO RELÂMPAGO ⚡</bold></gradient> <white>%02d:%02d", m, s));
    }

    private void spawnFirework(Location at) {
        World w = at.getWorld();
        if (w == null) return;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Color[] palette = {Color.RED, Color.ORANGE, Color.YELLOW, Color.LIME, Color.AQUA, Color.FUCHSIA, Color.PURPLE};
        Firework fw = w.spawn(at, Firework.class, f -> {
            FireworkMeta meta = f.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .with(rng.nextBoolean() ? FireworkEffect.Type.BALL_LARGE : FireworkEffect.Type.STAR)
                    .withColor(palette[rng.nextInt(palette.length)], palette[rng.nextInt(palette.length)])
                    .withFade(palette[rng.nextInt(palette.length)])
                    .trail(true).flicker(true).build());
            meta.setPower(rng.nextInt(1, 3));
            f.setFireworkMeta(meta);
        });
        fw.setPersistent(false);
    }

    // ════════════════════════════ 🇧🇷 BRAZIL EVENT (GUI) ════════════════════════════
    public void brazilFade(Player admin) {
        broadcast(" ");
        broadcast("<gradient:#009C3B:#FFDF00:#009C3B><bold>🇧🇷 BRAZIL EVENT! 🇧🇷</bold></gradient>");
        broadcast("<white>A tela está escurecendo... alguém coloca a música!");
        broadcast(" ");
        titleAll("<gradient:#009C3B:#FFDF00><bold>🇧🇷 BRAZIL EVENT 🇧🇷</bold></gradient>",
                "<white>pode começar a música!", 500, 3000, 800);
        for (Player p : Bukkit.getOnlinePlayers()) blackout(p, 300);
        admin.sendMessage(MM.deserialize("<#10fc46>Fade aplicado! Coloque a música agora."));
    }

    // ════════════════════════════ VFX helpers ════════════════════════════
    /** Spawna um modelo de VFX do pack numa posição, toca a animação e remove (fallback: nada). */
    private void vfxAt(String modelId, Location loc, String anim, long life) {
        if (!ModelEngineHook.isAvailable()) return;
        Location at = loc.clone();
        ArmorStand host = at.getWorld().spawn(at, ArmorStand.class, a -> {
            a.setInvisible(true);
            a.setMarker(true);
            a.setSmall(true);
            a.setGravity(false);
            a.setInvulnerable(true);
            a.setCollidable(false);
            a.setSilent(true);
            a.setPersistent(false);
        });
        Object m = ModelEngineHook.applyModel(host, modelId);
        if (m != null) ModelEngineHook.playAnimation(m, anim, 0.0, 0.1, 1.0, true);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if (host.isValid()) host.remove(); }, life);
    }

    /** Anéis de choque expandindo a partir do centro. */
    private void shockwaveAt(Location center, double maxR) {
        World w = center.getWorld();
        if (w == null) return;
        for (int step = 0; step < 6; step++) {
            final double rr = 1.0 + step * (maxR - 1.0) / 5.0;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                int pts = (int) Math.max(8, rr * 7);
                for (int k = 0; k < pts; k++) {
                    double a = 2 * Math.PI * k / pts;
                    Location at = center.clone().add(Math.cos(a) * rr, 0.2, Math.sin(a) * rr);
                    w.spawnParticle(Particle.FLAME, at, 1, 0.05, 0.05, 0.05, 0.01);
                    if (k % 3 == 0) w.spawnParticle(Particle.LAVA, at, 1, 0.1, 0.05, 0.1, 0);
                }
            }, step * 2L);
        }
    }

    // ════════════════════════════ util ════════════════════════════
    private void blackout(Player p, int ticks) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, ticks, 0, false, false, false));
    }

    private void later(long ticks, Runnable r) {
        plugin.getServer().getScheduler().runTaskLater(plugin, r, ticks);
    }

    private void broadcast(String mini) {
        plugin.getServer().sendMessage(MM.deserialize(mini));
    }

    private void titleAll(String titleMini, String subMini, long fadeInMs, long stayMs, long fadeOutMs) {
        Title title = Title.title(MM.deserialize(titleMini), MM.deserialize(subMini),
                Title.Times.times(Duration.ofMillis(fadeInMs), Duration.ofMillis(stayMs), Duration.ofMillis(fadeOutMs)));
        for (Player p : Bukkit.getOnlinePlayers()) p.showTitle(title);
    }

    private void playToAll(String key, float vol, float pitch) {
        Sound s = Sound.sound(Key.key(key), Sound.Source.MASTER, vol, pitch);
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(s);
    }

    // ════════════════════════════ bossbars ════════════════════════════
    private void showEventBar(String key, BossBar bar) {
        BossBar old = eventBars.put(key, bar);
        if (old != null) for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(old);
        for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(bar);
        ensureBarsTask();
    }

    private void hideEventBar(String key) {
        BossBar bar = eventBars.remove(key);
        if (bar != null) for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
    }

    private void hideAllBars() {
        for (BossBar bar : eventBars.values())
            for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
        eventBars.clear();
    }

    private void ensureBarsTask() {
        if (barsTask != null) return;
        barsTask = new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (eventBars.isEmpty()) { cancel(); barsTask = null; return; }
                t++;
                float prog = 0.8f + (float) Math.sin(t / 8.0) * 0.2f;   // "respira" (não é timer)
                for (BossBar bar : eventBars.values()) bar.progress(Math.max(0.05f, Math.min(1f, prog)));
            }
        };
        barsTask.runTaskTimer(plugin, 0L, 2L);
    }

    /** Quem entra durante o show: vê as bossbars. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (BossBar bar : eventBars.values()) event.getPlayer().showBossBar(bar);
        // Relogou com tamanho alterado (caiu durante o Tamanho Aleatório)? Restaura.
        if (sizedPlayers.contains(event.getPlayer().getUniqueId())) restoreSize(event.getPlayer());
    }

    /** Deslogou no meio do Tamanho Aleatório: restaura já (o atributo persiste no save do player). */
    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        if (sizedPlayers.contains(event.getPlayer().getUniqueId())) restoreSize(event.getPlayer());
    }
}
