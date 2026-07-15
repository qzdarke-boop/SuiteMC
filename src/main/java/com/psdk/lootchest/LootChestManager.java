package com.psdk.lootchest;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gerencia os baús de loot que caem pelo mapa: spawn por timers, animação de
 * meteoro caindo, baú grande girando com holograma, contagem ao fechar e
 * animação de voar embora. Tudo em memória (sem persistência) — ao reiniciar,
 * {@link #clearOrphans()} limpa displays órfãos e os timers respawnam.
 */
public class LootChestManager {

    public static final String WORLD = "pit";
    private static final String TAG = "psdk_loot";

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final AxisAngle4f IDENT = new AxisAngle4f(0f, 0f, 0f, 1f);

    private static final float CHEST_SCALE = 2f;
    private static final float CHEST_HOVER = 0.7f;    // baú flutua um pouco acima do solo
    private static final float METEOR_SCALE = 4.2f;   // meteoro bem maior e mais imponente
    private static final double COLLISION_SCALE = 1.8; // hitbox sólido (shulker invisível) cobrindo o baú
    private static final float VIEW_RANGE = 2000f;      // baú + holograma visíveis de bem longe (~máx. tracking range)
    private static final long SPIN_INTERVAL = 2L;     // ticks entre cada passo do giro (mais fluido)
    private static final float SPIN_STEP = 0.12f;     // radianos por passo
    private static final int FALL_HEIGHT = 36;        // blocos acima do solo
    private static final int FALL_TICKS = 60;         // duração da queda do meteoro
    /** Watchdog: se uma queda não pousar nesse tempo, considera-se travada e é limpa. */
    private static final long FALL_TIMEOUT_MS = 6_000L;
    /** Watchdog: se um baú não terminar de "voar embora" nesse tempo, é removido à força. */
    private static final long LEAVING_TIMEOUT_MS = 8_000L;
    /** Se um baú pousado ficar este tempo sem ninguém abrir, ele voa embora sozinho (libera o local). */
    private static final long IDLE_TIMEOUT_MS = 5 * 60_000L;

    /** Texto explicativo exibido abaixo de todos os baús. */
    private static final String INFO_TEXT =
            "<#a4a4a4>Clique com o <#fcc850>botão direito <#a4a4a4>para abrir!\n" +
            "<#848c94>Some após 3s sem ninguém no baú.";

    private final PSDK plugin;
    private final List<LootChest> active = new ArrayList<>();
    private boolean warnedNoWorld = false;

    public LootChestManager(PSDK plugin) {
        this.plugin = plugin;
    }

    // ===================== Schedulers =====================

    public void startSchedulers() {
        for (LootRarity rarity : LootRarity.values()) {
            long interval = rarity.getIntervalTicks();
            new BukkitRunnable() {
                @Override public void run() {
                    for (int i = 0; i < rarity.getSpawnCount(); i++) spawnChest(rarity);
                }
            }.runTaskTimer(plugin, interval, interval);
        }

        // Reaper: se as entidades de um baú sumirem (ex.: chunk descarregado), libera o local
        // para não ficar "preso ocupado" e impedir novos spawns ali. Também faz o watchdog
        // da QUEDA: se a animação do meteoro travar/morrer antes de pousar, limpa o meteoro
        // órfão e libera o local (antes só era limpo no próximo boot).
        new BukkitRunnable() {
            @Override public void run() {
                for (LootChest c : new ArrayList<>(active)) {
                    if (c.isLeaving()) {
                        // Voando embora: se a animação de saída travou/morreu, força a limpeza.
                        if (System.currentTimeMillis() - c.getLeavingAtMs() > LEAVING_TIMEOUT_MS) removeChest(c);
                        continue;
                    }
                    if (!c.isLanded()) {
                        // Ainda caindo: se demorou muito além da queda normal, a task morreu.
                        if (System.currentTimeMillis() - c.getSpawnedAtMs() > FALL_TIMEOUT_MS) {
                            plugin.getLogger().warning("[LootChest] Queda travada em "
                                    + c.getLoc().getBlockX() + "," + c.getLoc().getBlockY() + ","
                                    + c.getLoc().getBlockZ() + " — limpando meteoro órfão.");
                            abortFall(c);
                        }
                        continue;
                    }
                    if (!isChunkLoaded(c.getLoc())) continue;
                    Entity e = Bukkit.getEntity(c.getBlockDisplayUuid());
                    if (e == null || !e.isValid()) { removeChest(c); continue; }
                    // Baú ocioso: ninguém abriu por muito tempo -> voa embora e libera o local
                    // pra novos spawns (senão ele ficaria ocupando a coordenada pra sempre).
                    if (c.getInventory().getViewers().isEmpty()
                            && System.currentTimeMillis() - c.getSpawnedAtMs() > IDLE_TIMEOUT_MS) {
                        flyAway(c);
                    }
                }
                // Rede de segurança: limpa displays órfãos travados no mundo (meteoros/baús
                // que ficaram presos por reload de chunk ou task morta). Roda sempre, mesmo
                // sem baús ativos — é o que impede os "blocos de magma congelados" no mapa.
                sweepOrphans();
            }
        }.runTaskTimer(plugin, 100L, 100L); // a cada 5s

        if (Bukkit.getWorld(WORLD) == null) {
            plugin.getLogger().warning("[LootChest] Mundo '" + WORLD + "' ainda não carregado; "
                    + "os baús só vão spawnar quando ele existir.");
        } else {
            plugin.getLogger().info("[LootChest] Mundo '" + WORLD + "' encontrado; schedulers iniciados.");
        }
    }

    // ===================== Spawn + animações =====================

    /** Spawna um baú da raridade dada em um local livre aleatório. */
    public boolean spawnChest(LootRarity rarity) {
        World w = Bukkit.getWorld(WORLD);
        if (w == null) {
            if (!warnedNoWorld) {
                plugin.getLogger().warning("Mundo '" + WORLD + "' não encontrado; spawn ignorado.");
                warnedNoWorld = true;
            }
            return false;
        }

        // Sem ninguém no mundo: não adianta soltar meteoro (ninguém veria e a queda
        // cairia em chunk descarregado, virando display órfão preso no mapa).
        if (w.getPlayers().isEmpty()) return false;

        List<Location> livres = freeCoords(rarity, w);
        if (livres.isEmpty()) return false;

        Location ground = livres.get(ThreadLocalRandom.current().nextInt(livres.size()));
        LootChest chest = new LootChest(rarity, ground);
        active.add(chest); // reserva o local já no início (evita spawn duplo durante a queda)

        animateMeteor(w, chest);
        return true;
    }

    private List<Location> freeCoords(LootRarity rarity, World w) {
        List<Location> free = new ArrayList<>();
        for (int[] c : rarity.getCoords()) {
            Location loc = new Location(w, c[0], c[1], c[2]);
            if (!isOccupied(loc)) free.add(loc);
        }
        return free;
    }

    private boolean isOccupied(Location loc) {
        for (LootChest c : active) {
            Location cl = c.getLoc();
            if (cl.getWorld() != null && cl.getWorld().equals(loc.getWorld())
                    && cl.getBlockX() == loc.getBlockX()
                    && cl.getBlockY() == loc.getBlockY()
                    && cl.getBlockZ() == loc.getBlockZ()) {
                return true;
            }
        }
        return false;
    }

    private void animateMeteor(World w, LootChest chest) {
        Location ground = chest.getLoc();
        final double tx = center(ground).getX();
        final double tz = center(ground).getZ();
        final double targetY = center(ground).getY();

        // Entrada em ângulo, como um meteoro de verdade (offset horizontal aleatório).
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        final double startX = tx + (rng.nextBoolean() ? 1 : -1) * (10 + rng.nextDouble() * 6);
        final double startZ = tz + (rng.nextBoolean() ? 1 : -1) * (10 + rng.nextDouble() * 6);
        final double startY = targetY + FALL_HEIGHT;

        final Color rc = rarityColor(chest.getRarity());
        final Particle.DustOptions ember = new Particle.DustOptions(Color.fromRGB(0xFC, 0x78, 0x2C), 2.2f);
        final Particle.DustOptions trail = new Particle.DustOptions(rc, 2.0f);
        final Particle.DustOptions reticle = new Particle.DustOptions(rc, 1.6f);
        final Location mark = center(ground).add(0, 0.1, 0);

        BlockDisplay meteor = w.spawn(new Location(w, startX, startY, startZ), BlockDisplay.class, e -> {
            e.setBlock(Material.MAGMA_BLOCK.createBlockData());
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setGlowing(false);
            e.addScoreboardTag(TAG);
            // teleportDuration casa com a cadência de teleporte (1 tick): assim o
            // cliente segue a posição real sem ficar arrastando atrás nem deixar
            // "fantasma" quando o meteoro é removido no impacto.
            e.setTeleportDuration(1);       // interpola o deslocamento entre teleportes
            e.setInterpolationDuration(1);  // interpola a rotação (tumble)
            e.setBrightness(new Display.Brightness(15, 15));
            applyTumble(e, 0f, METEOR_SCALE);
        });
        final UUID meteorId = meteor.getUniqueId();
        chest.setMeteorUuid(meteorId); // rastreia o meteoro p/ o reaper/watchdog poder limpá-lo

        regionSound(ground, Sound.ENTITY_GHAST_SHOOT, 1.2f, 0.5f);
        regionSound(ground, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 0.6f);

        // Telégrafo de impacto: anel giratório que encolhe ("lock-on") + coluna de luz no alvo.
        new BukkitRunnable() {
            int t = 0;
            double ang = 0;
            @Override public void run() {
                if (t++ > FALL_TICKS || !active.contains(chest)) { cancel(); return; }
                ang += 0.4;
                double prog = (double) t / FALL_TICKS;
                double radius = 3.2 * (1 - prog) + 0.6;
                for (int i = 0; i < 3; i++) {
                    double a = ang + (Math.PI * 2 / 3) * i;
                    w.spawnParticle(Particle.DUST,
                            mark.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius),
                            1, 0, 0, 0, 0.0, reticle);
                }
                if (t % 2 == 0) {
                    w.spawnParticle(Particle.END_ROD, mark.clone().add(0, (t % 16) * 0.35, 0),
                            1, 0.02, 0.0, 0.02, 0.0);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        new BukkitRunnable() {
            int t = 0;
            float tumble = 0f;
            @Override public void run() {
                // Se o baú já foi liberado (reaper/clearAll), aborta a queda.
                if (!active.contains(chest)) { cancel(); return; }
                Entity en = Bukkit.getEntity(meteorId);
                if (!(en instanceof BlockDisplay bd) || !bd.isValid()) {
                    if (!isChunkLoaded(chest.getLoc())) return;
                    cancel();
                    abortFall(chest);
                    return;
                }

                float p = Math.min(1f, (float) t / FALL_TICKS);
                double eased = p * p; // ease-in: acelera como gravidade
                double x = startX + (tx - startX) * eased;
                double y = startY + (targetY - startY) * eased;
                double z = startZ + (tz - startZ) * eased;

                Location at = new Location(w, x, y, z);
                bd.setInterpolationDelay(0);
                bd.teleport(at);
                tumble += 0.45f; // gira rápido
                applyTumble(bd, tumble, METEOR_SCALE);

                // núcleo em chamas + brasas + fumaça persistente que forma o "risco" no céu
                World ww = bd.getWorld();
                ww.spawnParticle(Particle.FLAME, at, 45, 0.95, 0.95, 0.95, 0.05);
                ww.spawnParticle(Particle.LAVA, at, 7, 0.55, 0.55, 0.55, 0);
                ww.spawnParticle(Particle.DUST, at, 22, 0.95, 0.95, 0.95, 0.0, ember);
                ww.spawnParticle(Particle.LARGE_SMOKE, at, 16, 0.45, 0.45, 0.45, 0.01);
                ww.spawnParticle(Particle.SMALL_FLAME, at, 24, 0.8, 0.8, 0.8, 0.03);
                ww.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 8, 0.5, 0.5, 0.5, 0.02);
                ww.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, at, 2, 0.18, 0.18, 0.18, 0.0); // fica parada -> rastro visível

                // tripla hélice de luz girando ao redor do meteoro (cor da raridade)
                for (int s = 0; s < 3; s++) {
                    double a = tumble * 0.6 + (Math.PI * 2 / 3) * s;
                    Location hp = at.clone().add(Math.cos(a) * 2.1, Math.sin(tumble * 0.4) * 0.6, Math.sin(a) * 2.1);
                    ww.spawnParticle(Particle.END_ROD, hp, 1, 0, 0, 0, 0);
                    ww.spawnParticle(Particle.DUST, hp, 2, 0.1, 0.1, 0.1, 0.0, trail);
                }

                if (t % 4 == 0) regionSound(ground, Sound.ENTITY_BLAZE_SHOOT, 0.6f, 0.5f);

                if (p >= 1f) {
                    bd.remove();
                    chest.setMeteorUuid(null); // o meteoro acabou; settle() assume o display do baú
                    impact(w, chest);
                    cancel();
                    return;
                }
                t++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void impact(World w, LootChest chest) {
        Location ground = chest.getLoc();
        Location c = center(ground).add(0, 0.6, 0);

        final Color rc = rarityColor(chest.getRarity());
        final Particle.DustOptions rdust = new Particle.DustOptions(rc, 2.2f);

        // Onda de impacto: quem estiver perto é arremessado para cima.
        knockbackNearby(w, c);

        regionSoundEcho(ground, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);
        regionSound(ground, Sound.BLOCK_ANVIL_LAND, 0.7f, 0.6f);
        regionSound(ground, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.2f);
        regionSound(ground, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.0f);

        w.spawnParticle(Particle.FLASH, c, 6, 0.3, 0.1, 0.3, 0);
        w.spawnParticle(Particle.SONIC_BOOM, c, 1, 0, 0, 0, 0);          // anel de choque cinematográfico
        w.spawnParticle(Particle.EXPLOSION_EMITTER, c, 5, 0.8, 0.3, 0.8, 0);
        w.spawnParticle(Particle.EXPLOSION, c, 24, 1.2, 0.4, 1.2, 0);
        w.spawnParticle(Particle.FLAME, c, 300, 1.5, 0.5, 1.5, 0.3);
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, c, 110, 1.3, 0.35, 1.3, 0.12); // brasas "infernais"
        w.spawnParticle(Particle.LARGE_SMOKE, c, 160, 1.5, 0.5, 1.5, 0.14);
        w.spawnParticle(Particle.LAVA, c, 60, 1.1, 0.5, 1.1, 0);
        w.spawnParticle(Particle.FIREWORK, c, 100, 1.1, 0.6, 1.1, 0.3);
        w.spawnParticle(Particle.DUST, c, 130, 1.3, 0.55, 1.3, 0.0, rdust);

        // Duas ondas de choque concêntricas (fogo + cor da raridade).
        final Location ringCenter = c.clone();
        new BukkitRunnable() {
            double r = 0.6;
            @Override public void run() {
                int points = (int) Math.max(18, r * 6);
                for (int i = 0; i < points; i++) {
                    double ang = (Math.PI * 2 / points) * i;
                    double cos = Math.cos(ang), sin = Math.sin(ang);
                    w.spawnParticle(Particle.FLAME,
                            new Location(w, ringCenter.getX() + cos * r, ringCenter.getY() - 0.4, ringCenter.getZ() + sin * r),
                            1, 0, 0, 0, 0);
                    w.spawnParticle(Particle.LARGE_SMOKE,
                            new Location(w, ringCenter.getX() + cos * r, ringCenter.getY() - 0.4, ringCenter.getZ() + sin * r),
                            1, 0, 0, 0, 0);
                    w.spawnParticle(Particle.DUST,
                            new Location(w, ringCenter.getX() + cos * (r * 0.7), ringCenter.getY() + 0.2, ringCenter.getZ() + sin * (r * 0.7)),
                            1, 0, 0, 0, 0.0, rdust);
                }
                r += 0.9;
                if (r > 7.0) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Coluna de poeira/brasas subindo do impacto.
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t++ > 14) { cancel(); return; }
                Location up = ringCenter.clone().add(0, t * 0.25, 0);
                w.spawnParticle(Particle.LARGE_SMOKE, up, 4, 0.3, 0.1, 0.3, 0.01);
                w.spawnParticle(Particle.FLAME, up, 2, 0.2, 0.1, 0.2, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        settle(w, chest);
    }

    /** Arremessa para cima os jogadores próximos ao ponto de impacto (com queda de força pela distância). */
    private void knockbackNearby(World w, Location c) {
        final double radius = 6.0;
        for (Player p : w.getPlayers()) {
            if (p.isDead() || !p.isValid()) continue;
            double dist = p.getLocation().distance(c);
            if (dist > radius) continue;

            double falloff = 1.0 - (dist / radius);              // 1 = no centro, 0 = na borda
            Vector dir = p.getLocation().toVector().subtract(c.toVector());
            dir.setY(0);
            if (dir.lengthSquared() < 1.0e-4) {
                dir = new Vector(Math.random() - 0.5, 0, Math.random() - 0.5);
            }
            dir.normalize().multiply(0.55 * falloff);            // empurrão horizontal leve para fora
            double up = 0.55 + 0.85 * falloff;                   // impulso vertical (jogado pra cima)

            p.setVelocity(p.getVelocity().add(new Vector(dir.getX(), up, dir.getZ())));
            p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.1f);
        }
    }

    private void settle(World w, LootChest chest) {
        Location base = chest.getLoc();
        Location entityLoc = center(base);

        // Baú nasce achatado (impacto) e dá o "spring" até o tamanho normal.
        BlockDisplay disp = w.spawn(entityLoc.clone().add(0, CHEST_HOVER, 0), BlockDisplay.class, e -> {
            e.setBlock(chest.getRarity().getBlock().createBlockData());
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.addScoreboardTag(TAG);
            e.setInterpolationDuration(1);
            e.setViewRange(VIEW_RANGE);                          // visível de bem longe
            e.setBrightness(new Display.Brightness(15, 15));     // sempre bem iluminado à distância
            applyChest(e, 0f, 2.6f, 1.2f, 0f);
        });
        chest.setBlockDisplayUuid(disp.getUniqueId());
        chest.setMeteorUuid(null);
        chest.setLanded(true); // a partir daqui o reaper vigia o display do baú

        // Holograma do nome: começa invisível (escala 0) e cresce subindo.
        TextDisplay holo = w.spawn(entityLoc.clone().add(0, 0.5, 0), TextDisplay.class, e -> {
            e.text(MM.deserialize(chest.getRarity().getHologramText()));
            e.setBillboard(Display.Billboard.VERTICAL);
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            e.setSeeThrough(false);
            e.setShadowed(true);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0)); // fundo transparente
            e.setBrightness(new Display.Brightness(15, 15));   // sempre legível
            e.setViewRange(VIEW_RANGE);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.addScoreboardTag(TAG);
            e.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f), IDENT, new Vector3f(0f, 0f, 0f), IDENT));
        });
        chest.setHologramUuid(holo.getUniqueId());
        animateHologramIn(chest);

        // Holograma explicativo em todos os baús, abaixo do bloco, rente ao solo.
        TextDisplay info = w.spawn(entityLoc.clone().add(0, 0.5, 0), TextDisplay.class, e -> {
            e.text(MM.deserialize(INFO_TEXT));
            e.setBillboard(Display.Billboard.VERTICAL);
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            e.setSeeThrough(false);
            e.setShadowed(true);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setLineWidth(400);
            e.setViewRange(VIEW_RANGE);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.addScoreboardTag(TAG);
            e.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f), IDENT, new Vector3f(0f, 0f, 0f), IDENT));
        });
        chest.setInfoUuid(info.getUniqueId());

        // Interação (clique direito p/ abrir): nasce no solo e envolve totalmente o hitbox
        // sólido abaixo, garantindo que o raio do clique acerte a Interaction antes do shulker.
        Interaction inter = w.spawn(entityLoc, Interaction.class, e -> {
            e.setInteractionWidth(2.6f);
            e.setInteractionHeight(2.9f);
            e.setResponsive(true);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.addScoreboardTag(TAG);
        });
        chest.setInteractionUuid(inter.getUniqueId());

        // Hitbox sólido: shulker invisível travado no lugar para o jogador não atravessar/
        // entrar dentro do baú. Sem IA (não anda nem atira) e silencioso.
        Shulker collision = w.spawn(entityLoc, Shulker.class, e -> {
            e.setAI(false);
            e.setAware(false);
            e.setInvisible(true);
            e.setSilent(true);
            e.setInvulnerable(true);
            e.setPersistent(false);
            e.setCollidable(true);
            e.setGravity(false);
            e.setRemoveWhenFarAway(false);
            try { e.setPeek(0f); } catch (Throwable ignored) {}
            AttributeInstance scale = e.getAttribute(Attribute.SCALE);
            if (scale != null) scale.setBaseValue(COLLISION_SCALE);
            e.addScoreboardTag(TAG);
        });
        chest.setCollisionUuid(collision.getUniqueId());

        regionSoundEcho(base, Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.4f);
        regionSound(base, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);

        announceSpawn(chest);
        bounceThenSpin(chest);
    }

    /** Altura final (translação Y) do holograma do nome acima do baú. */
    private static final float HOLO_NAME_Y = 3.8f;
    /** Escala do holograma do nome. */
    private static final float HOLO_SCALE = 2.1f;
    /** Escala do texto informativo — próxima ao tamanho do nome. */
    private static final float HOLO_INFO_SCALE = 1.4f;
    /** Translação Y do info: logo abaixo do nome do baú. */
    private static final float HOLO_INFO_Y = 2.5f;

    /** Holograma do nome sobe e cresce; o texto informativo cresce alinhado logo abaixo, do mesmo tamanho. */
    private void animateHologramIn(LootChest chest) {
        new BukkitRunnable() {
            @Override public void run() {
                if (Bukkit.getEntity(chest.getHologramUuid()) instanceof TextDisplay td && td.isValid()) {
                    td.setInterpolationDelay(0);
                    td.setInterpolationDuration(12);
                    td.setTransformation(new Transformation(
                            new Vector3f(0f, HOLO_NAME_Y, 0f), IDENT,
                            new Vector3f(HOLO_SCALE, HOLO_SCALE, HOLO_SCALE), IDENT));
                }
                // Info: abaixo do baú, rente ao solo, escala pequena.
                if (chest.getInfoUuid() != null
                        && Bukkit.getEntity(chest.getInfoUuid()) instanceof TextDisplay info && info.isValid()) {
                    info.setInterpolationDelay(0);
                    info.setInterpolationDuration(12);
                    info.setTransformation(new Transformation(
                            new Vector3f(0f, HOLO_INFO_Y, 0f), IDENT,
                            new Vector3f(HOLO_INFO_SCALE, HOLO_INFO_SCALE, HOLO_INFO_SCALE), IDENT));
                }
            }
        }.runTaskLater(plugin, 3L);
    }

    /** Spring de aterrissagem (achatado -> normal com leve overshoot) e depois inicia o giro ocioso. */
    private void bounceThenSpin(LootChest chest) {
        new BukkitRunnable() {
            int t = 0;
            final int dur = 14;
            @Override public void run() {
                Entity en = Bukkit.getEntity(chest.getBlockDisplayUuid());
                if (!(en instanceof BlockDisplay bd) || !bd.isValid()) { cancel(); return; }
                float p = Math.min(1f, (float) t / dur);
                float scaleY = lerp(1.2f, CHEST_SCALE, easeOutBack(p));
                float scaleXZ = lerp(2.6f, CHEST_SCALE, Math.min(1f, p * 1.3f));
                bd.setInterpolationDelay(0);
                bd.setInterpolationDuration(1);
                applyChest(bd, 0f, scaleXZ, scaleY, 0f);
                if (t++ >= dur) {
                    cancel();
                    startIdleSpin(chest);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Giro contínuo fluido + leve flutuação vertical. */
    private void startIdleSpin(LootChest chest) {
        BukkitTask spin = new BukkitRunnable() {
            double time = 0;
            @Override public void run() {
                Entity en = Bukkit.getEntity(chest.getBlockDisplayUuid());
                if (!(en instanceof BlockDisplay bd) || !bd.isValid()) { cancel(); return; }
                time += SPIN_INTERVAL;
                chest.setSpinAngle(chest.getSpinAngle() + SPIN_STEP);
                float bob = (float) (Math.sin(time * 0.08) * 0.12);
                bd.setInterpolationDelay(0);
                bd.setInterpolationDuration((int) SPIN_INTERVAL);
                applyChest(bd, chest.getSpinAngle(), CHEST_SCALE, CHEST_SCALE, bob);
            }
        }.runTaskTimer(plugin, SPIN_INTERVAL, SPIN_INTERVAL);
        chest.setSpinTask(spin);
    }

    /** Transforma o baú: giro em Y + escala (squash/stretch) + flutuação, mantendo-o centralizado na coluna. */
    private void applyChest(BlockDisplay bd, float yaw, float scaleXZ, float scaleY, float bobY) {
        AxisAngle4f rot = new AxisAngle4f(yaw, 0f, 1f, 0f);
        Vector3f scaledCenter = new Vector3f(scaleXZ / 2f, 0f, scaleXZ / 2f);
        Vector3f rotated = new Vector3f(scaledCenter);
        rot.transform(rotated);
        Vector3f translation = new Vector3f(-rotated.x, bobY, -rotated.z);
        bd.setTransformation(new Transformation(
                translation, rot, new Vector3f(scaleXZ, scaleY, scaleXZ), IDENT));
    }

    /** Tumble 3D (meteoro girando no ar) centralizado no próprio centro. */
    private void applyTumble(BlockDisplay bd, float angle, float scale) {
        AxisAngle4f rot = new AxisAngle4f(angle, 0.358f, 0.894f, 0.268f); // eixo já normalizado
        Vector3f c = new Vector3f(scale / 2f, scale / 2f, scale / 2f);
        Vector3f rotated = new Vector3f(c);
        rot.transform(rotated);
        Vector3f translation = new Vector3f(-rotated.x, -rotated.y, -rotated.z);
        bd.setTransformation(new Transformation(
                translation, rot, new Vector3f(scale, scale, scale), IDENT));
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    /** Easing com leve overshoot no fim (efeito de mola). */
    private static float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1f;
        float x = t - 1f;
        return 1f + c3 * x * x * x + c1 * x * x;
    }

    // ===================== Contagem + voar embora =====================

    public void startCountdown(LootChest chest) {
        if (chest.isLeaving()) return;
        cancelCountdown(chest);
        setHologramCountdown(chest, 3);

        BukkitTask task = new BukkitRunnable() {
            int secs = 3;
            @Override public void run() {
                secs--;
                if (secs <= 0) {
                    chest.setCountdownTask(null);
                    cancel();
                    flyAway(chest);
                    return;
                }
                setHologramCountdown(chest, secs);
            }
        }.runTaskTimer(plugin, 20L, 20L);
        chest.setCountdownTask(task);
    }

    public void cancelCountdown(LootChest chest) {
        if (chest.getCountdownTask() != null) {
            chest.getCountdownTask().cancel();
            chest.setCountdownTask(null);
        }
        setHologramText(chest, chest.getRarity().getHologramText());
        if (Bukkit.getEntity(chest.getInfoUuid()) instanceof TextDisplay td && td.isValid()) {
            td.text(MM.deserialize(INFO_TEXT));
        }
    }

    private void flyAway(LootChest chest) {
        if (chest.isLeaving()) return;
        chest.setLeaving(true);

        // Fecha o inventário pra quem estava com o baú aberto — ele vai sumir, então
        // ninguém pode continuar saqueando depois que voou embora.
        for (org.bukkit.entity.HumanEntity viewer : new ArrayList<>(chest.getInventory().getViewers())) {
            viewer.closeInventory();
        }

        if (chest.getSpinTask() != null) { chest.getSpinTask().cancel(); chest.setSpinTask(null); }
        Location loc = chest.getLoc();
        removeEntity(chest.getHologramUuid(), loc);
        removeEntity(chest.getInfoUuid(), loc);
        removeEntity(chest.getInteractionUuid(), loc);
        removeEntity(chest.getCollisionUuid(), loc);

        final World w = chest.getLoc().getWorld();
        final Color rc = rarityColor(chest.getRarity());
        final Particle.DustOptions rdust = new Particle.DustOptions(rc, 1.8f);
        final Location cc = center(chest.getLoc()).add(0, 1, 0);

        Entity en = Bukkit.getEntity(chest.getBlockDisplayUuid());
        if (w == null || !(en instanceof BlockDisplay bd) || !bd.isValid()) {
            removeChest(chest);
            return;
        }
        bd.setTeleportDuration(3);
        final Location startLoc = bd.getLocation();

        regionSound(chest.getLoc(), Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 0.8f);

        // FASE 1 — carga: partículas convergem para o baú (implosão), com tremor e pitch subindo.
        new BukkitRunnable() {
            int t = 0;
            final int dur = 14;
            @Override public void run() {
                Entity e = Bukkit.getEntity(chest.getBlockDisplayUuid());
                if (!(e instanceof BlockDisplay d) || !d.isValid()) { cancel(); removeChest(chest); return; }
                double prog = (double) t / dur;
                for (int i = 0; i < 6; i++) {
                    double a = (Math.PI * 2 / 6) * i + t * 0.3;
                    double rad = 3.0 * (1 - prog) + 0.3;
                    Location p = cc.clone().add(Math.cos(a) * rad, (1 - prog) * 1.2, Math.sin(a) * rad);
                    w.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0.0, rdust);
                    w.spawnParticle(Particle.END_ROD, p, 1, 0, 0, 0, 0);
                }
                float s = CHEST_SCALE + (float) Math.sin(t * 1.2) * 0.06f; // tremor
                applyChest(d, chest.getSpinAngle(), s, s, (float) (prog * 0.2));
                if (t % 3 == 0) regionSound(chest.getLoc(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 0.8f + (float) prog);
                if (t++ >= dur) { cancel(); launch(chest, w, rdust, startLoc); }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** FASE 2 do voo: disparo tipo foguete com hélice da cor da raridade, encolhendo, e explosão final. */
    private void launch(LootChest chest, World w, Particle.DustOptions rdust, Location startLoc) {
        regionSoundEcho(chest.getLoc(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 0.7f);
        regionSound(chest.getLoc(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 0.6f);

        new BukkitRunnable() {
            int t = 0;
            final int dur = 30;
            float yaw = chest.getSpinAngle();
            @Override public void run() {
                Entity e = Bukkit.getEntity(chest.getBlockDisplayUuid());
                if (!(e instanceof BlockDisplay d) || !d.isValid()) { cancel(); removeChest(chest); return; }

                float p = Math.min(1f, (float) t / dur);
                double eased = p * p; // dispara como foguete
                double y = startLoc.getY() + eased * 50;
                Location at = new Location(w, startLoc.getX(), y, startLoc.getZ());

                d.setInterpolationDelay(0);
                d.teleport(at);
                yaw += 0.3f + p * 0.9f;                   // gira cada vez mais rápido
                float scale = lerp(CHEST_SCALE, 0.05f, p); // encolhe até sumir
                applyChest(d, yaw, scale, scale, 0f);

                // exausto + hélice da cor da raridade
                w.spawnParticle(Particle.CLOUD, at, 10, 0.3, 0.2, 0.3, 0.03);
                w.spawnParticle(Particle.FLAME, at, 8, 0.2, 0.3, 0.2, 0.03);
                w.spawnParticle(Particle.LARGE_SMOKE, at.clone().add(0, -0.5, 0), 4, 0.2, 0.1, 0.2, 0.02);
                for (int s = 0; s < 2; s++) {
                    double a = yaw + Math.PI * s;
                    Location hp = at.clone().add(Math.cos(a) * 0.9, 0, Math.sin(a) * 0.9);
                    w.spawnParticle(Particle.DUST, hp, 1, 0, 0, 0, 0.0, rdust);
                    w.spawnParticle(Particle.END_ROD, hp, 1, 0, 0, 0, 0);
                }

                if (t++ >= dur) {
                    Location top = at.clone();
                    w.spawnParticle(Particle.FLASH, top, 1, 0, 0, 0, 0);
                    w.spawnParticle(Particle.FIREWORK, top, 60, 0.6, 0.6, 0.6, 0.3);
                    w.spawnParticle(Particle.DUST, top, 60, 0.7, 0.7, 0.7, 0.0, rdust);
                    w.spawnParticle(Particle.EXPLOSION, top, 4, 0.3, 0.3, 0.3, 0);
                    regionSound(chest.getLoc(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0f, 1.0f);
                    regionSound(chest.getLoc(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.7f, 1.0f);
                    cancel();
                    removeChest(chest);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ===================== Holograma helpers =====================

    private void setHologramText(LootChest chest, String mini) {
        if (Bukkit.getEntity(chest.getHologramUuid()) instanceof TextDisplay td && td.isValid()) {
            td.text(MM.deserialize(mini));
        }
    }

    private void setHologramCountdown(LootChest chest, int secs) {
        setHologramText(chest, chest.getRarity().getHologramText());
        if (Bukkit.getEntity(chest.getInfoUuid()) instanceof TextDisplay td && td.isValid()) {
            td.text(MM.deserialize(INFO_TEXT + "\n<gray>Fechando em <#fcc850>" + secs + "s"));
        }
    }

    // ===================== Lookup / limpeza =====================

    public LootChest getChestByInteraction(UUID interactionUuid) {
        if (interactionUuid == null) return null;
        for (LootChest c : active) {
            if (interactionUuid.equals(c.getInteractionUuid())) return c;
        }
        return null;
    }

    /**
     * Resolve o baú por QUALQUER entidade dele (interação OU shulker de colisão).
     * Assim, mesmo que o clique acerte o hitbox sólido invisível em vez da
     * interação, o baú ainda abre — e o clique é cancelado (não dá pra colocar
     * bloco no hitbox nem bater nele).
     */
    public LootChest getChestByEntity(UUID entityUuid) {
        if (entityUuid == null) return null;
        for (LootChest c : active) {
            if (entityUuid.equals(c.getInteractionUuid())
                    || entityUuid.equals(c.getCollisionUuid())) return c;
        }
        return null;
    }

    private void removeChest(LootChest chest) {
        if (chest.getSpinTask() != null) chest.getSpinTask().cancel();
        if (chest.getCountdownTask() != null) chest.getCountdownTask().cancel();
        Location loc = chest.getLoc();
        removeEntity(chest.getMeteorUuid(), loc);
        removeEntity(chest.getBlockDisplayUuid(), loc);
        removeEntity(chest.getHologramUuid(), loc);
        removeEntity(chest.getInfoUuid(), loc);
        removeEntity(chest.getInteractionUuid(), loc);
        removeEntity(chest.getCollisionUuid(), loc);
        active.remove(chest);
    }

    /**
     * Aborta uma queda travada/interrompida: remove o meteoro órfão (se ainda
     * existir) e libera o local. Idempotente — seguro chamar mais de uma vez.
     */
    private void abortFall(LootChest chest) {
        removeEntity(chest.getMeteorUuid(), chest.getLoc());
        chest.setMeteorUuid(null);
        removeChest(chest);
    }

    private boolean isChunkLoaded(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        return loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    private void ensureChunkLoaded(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        loc.getWorld().getChunkAt(loc);
    }

    private void removeEntity(UUID uuid, Location near) {
        if (uuid == null) return;
        if (near != null) ensureChunkLoaded(near);
        Entity e = Bukkit.getEntity(uuid);
        if (e != null) e.remove();
    }

    /** Remove todos os baús ativos e suas entidades. */
    public void clearAll() {
        for (LootChest c : new ArrayList<>(active)) removeChest(c);
        active.clear();
        clearOrphans();
    }

    /**
     * Rede de segurança em tempo real: remove qualquer entidade com a {@link #TAG}
     * que NÃO pertença a nenhum baú ativo. Pega meteoros/baús/hologramas presos no
     * mundo (task morta, reload de chunk, etc.). Antes isso só era limpo no próximo
     * boot — por isso os blocos de magma ficavam "congelados" espalhados pelo mapa.
     */
    private void sweepOrphans() {
        World w = Bukkit.getWorld(WORLD);
        if (w == null) return;
        java.util.Set<UUID> referenced = collectReferencedUuids();
        for (Entity e : w.getEntities()) {
            if (e.getScoreboardTags().contains(TAG) && !referenced.contains(e.getUniqueId())) {
                e.remove();
            }
        }
    }

    /** UUIDs de todas as entidades pertencentes a baús atualmente ativos. */
    private java.util.Set<UUID> collectReferencedUuids() {
        java.util.Set<UUID> ids = new java.util.HashSet<>();
        for (LootChest c : active) {
            addIf(ids, c.getMeteorUuid());
            addIf(ids, c.getBlockDisplayUuid());
            addIf(ids, c.getHologramUuid());
            addIf(ids, c.getInfoUuid());
            addIf(ids, c.getInteractionUuid());
            addIf(ids, c.getCollisionUuid());
        }
        return ids;
    }

    private static void addIf(java.util.Set<UUID> set, UUID u) { if (u != null) set.add(u); }

    /** Remove displays/interações órfãos (tag {@link #TAG}) do mundo — usado no boot. */
    public void clearOrphans() {
        World w = Bukkit.getWorld(WORLD);
        if (w == null) return;
        int removed = 0;
        for (Entity e : w.getEntities()) {
            if (e.getScoreboardTags().contains(TAG)) { e.remove(); removed++; }
        }
        if (removed > 0) plugin.getLogger().info("[LootChest] " + removed + " entidade(s) órfã(s) removida(s).");
    }

    private void regionSound(Location blockLoc, Sound sound, float vol, float pitch) {
        if (blockLoc.getWorld() == null) return;
        blockLoc.getWorld().playSound(center(blockLoc), sound, vol, pitch);
    }

    /**
     * Som "encorpado" ao redor do baú: toca no centro, em pontos ao redor (surround
     * para quem está perto) e com repetições mais graves/baixas (eco).
     */
    private void regionSoundEcho(Location blockLoc, Sound sound, float vol, float pitch) {
        World w = blockLoc.getWorld();
        if (w == null) return;
        Location c = center(blockLoc).add(0, 1, 0);

        // Volume > 1 estende o alcance audível (~16 * volume blocos): o som chega bem mais longe.
        float far = Math.min(3.2f, vol * 2.6f);
        w.playSound(c, sound, far, pitch);

        // Surround: pontos ao redor enriquecem a espacialização para quem está perto.
        double r = 4.5;
        for (int i = 0; i < 6; i++) {
            double a = (Math.PI * 2 / 6) * i;
            w.playSound(c.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), sound, vol * 0.6f, pitch);
        }

        // Eco: várias repetições decaindo, cada vez mais graves e distantes (~2s de cauda).
        int[] delays = {5, 11, 18, 27, 38};
        for (int k = 0; k < delays.length; k++) {
            final float ev = far * (float) Math.pow(0.62, k + 1);
            final float ep = Math.max(0.5f, pitch * (1f - 0.09f * (k + 1)));
            Bukkit.getScheduler().runTaskLater(plugin, () -> w.playSound(c, sound, ev, ep), delays[k]);
        }
    }

    /** Mensagem global ao spawnar um baú. */
    private void announceSpawn(LootChest chest) {
        LootRarity r = chest.getRarity();
        plugin.getServer().sendMessage(MM.deserialize(
                "<#848c94>Um<reset> " + r.getHologramText()
                        + " <#848c94>caiu no mapa! <#848c94>Encontre e saqueie antes que alguém pegue!"));
    }

    private static Location center(Location blockLoc) {
        return blockLoc.clone().add(0.5, 0, 0.5);
    }

    /** Cor da paleta usada nas partículas temáticas de cada raridade. */
    private static Color rarityColor(LootRarity r) {
        return switch (r) {
            case NORMAL   -> Color.fromRGB(0xCB, 0xD1, 0xD7);
            case RARO     -> Color.fromRGB(0x71, 0xB0, 0xEC);
            case EPICO    -> Color.fromRGB(0xB8, 0x5A, 0xFC);
            case LENDARIO -> Color.fromRGB(0xFC, 0xC8, 0x50);
        };
    }
}