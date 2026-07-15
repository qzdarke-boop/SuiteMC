package com.psdk.boss;

import com.psdk.PSDK;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Zombie;
import org.bukkit.GameMode;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Boss "Spooky" (modelo littleroom pumpkin_king). Usa TODAS as animações do
 * blueprint: idle, walk, greet, swing_1, swing_2, summon, teleport, throw_scythe,
 * death — e os sons custom do pack (littleroom_pumpkinking:*).
 */
public class PumpkinKing implements BossEntity {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // ── AJUSTES ──
    public static final String MODEL_ID = "pumpkin_king";                       // ID do blueprint do ModelEngine
    public static final String NAME_PLAIN = "Spooky";
    public static final String NAME = "<gradient:#ff9f00:#ff6a00:#7a00cc><bold>" + NAME_PLAIN + "</bold></gradient>";
    private static final double MAX_HP = 8000.0;
    private static final double REAL_MAX = 1024.0;
    private static final double DMG_MULT = 0.35;
    private static final double BOSSBAR_RANGE = 50.0;
    private static final double HOLO_Y = 6.0;   // boss bem grandão -> holograma bem em cima

    // Sons custom do pack (namespace littleroom_pumpkinking).
    private static final String SND_SWING  = "littleroom_pumpkinking:swing";
    private static final String SND_SCYTHE = "littleroom_pumpkinking:scythe_throw";
    private static final String SND_TELE   = "littleroom_pumpkinking:teleport";
    private static final String SND_LAUGH  = "littleroom_pumpkinking:laugh";
    private static final String SND_GRUNT  = "littleroom_pumpkinking:grunt";
    private static final String SND_TOMB   = "littleroom_pumpkinking:tombstone";
    private static final String SND_DEATH  = "littleroom_pumpkinking:death";

    private final PSDK plugin;
    private final BossManager manager;
    private final Zombie base;
    private final Object model;
    private final BossBar bossBar;
    private final Set<UUID> barViewers = new HashSet<>();
    private final Map<UUID, Double> damage = new HashMap<>();
    private final Map<UUID, String> damagerNames = new HashMap<>();

    private BukkitRunnable controller;
    private Location lastLoc;
    private String currentLoop = "";
    private boolean dead = false;
    private boolean engaged = false;
    private int busy = 0, skillCd = 40, scytheCd = 100, teleCd = 200, summonCd = 300, auraTick = 0;
    private double hp = MAX_HP;
    private TextDisplay holo;
    private double lastHoloHp = -1;

    public PumpkinKing(PSDK plugin, BossManager manager, Location loc) {
        this.plugin = plugin;
        this.manager = manager;
        this.base = loc.getWorld().spawn(loc, Zombie.class, z -> {
            z.setAdult();
            z.setInvisible(true);
            z.setSilent(true);
            z.setRemoveWhenFarAway(false);
            z.setShouldBurnInDay(false);
            z.setCanPickupItems(false);
            z.setCustomNameVisible(false);
            setAttr(z, Attribute.MAX_HEALTH, REAL_MAX);
            z.setHealth(REAL_MAX);
            setAttr(z, Attribute.MOVEMENT_SPEED, 0.26);
            setAttr(z, Attribute.KNOCKBACK_RESISTANCE, 1.0);
            setAttr(z, Attribute.FOLLOW_RANGE, 40.0);
            setAttr(z, Attribute.ATTACK_DAMAGE, 0.0);
        });
        this.model = ModelEngineHook.applyModel(base, MODEL_ID);
        this.bossBar = BossBar.bossBar(MM.deserialize(NAME), 1.0f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_12);
        this.lastLoc = base.getLocation();
        playLoop("idle");
        sfx(SND_LAUGH, Sound.ENTITY_WITCH_AMBIENT, 1.4f, 0.7f);
        spawnBurst();
        this.holo = spawnHolo();
        startController();
    }

    // ───── BossEntity ─────
    @Override public Entity getBase() { return base; }
    @Override public boolean isDead() { return dead; }
    @Override public boolean isShielded() { return false; }
    @Override public double getHp() { return hp; }
    @Override public double getMaxHp() { return MAX_HP; }
    @Override public String getNamePlain() { return NAME_PLAIN; }
    @Override public Map<UUID, Double> getDamageMap() { return damage; }
    @Override public Map<UUID, String> getDamagerNames() { return damagerNames; }
    @Override public void damageBoss(double amount) { hp = Math.max(0, hp - amount); }

    @Override public void recordDamage(UUID uuid, String name, double amount) {
        if (dead) return;
        damage.merge(uuid, amount, Double::sum);
        damagerNames.put(uuid, name);
    }

    @Override public void onHurt() {
        if (dead || busy > 0) return;
        if (ThreadLocalRandom.current().nextInt(100) < 12)
            sfx(SND_GRUNT, Sound.ENTITY_IRON_GOLEM_HURT, 1f, 1f);
    }

    // ───── controlador ─────
    private void startController() {
        controller = new BukkitRunnable() {
            @Override public void run() {
                if (dead) return;
                if (base.isDead() || !base.isValid()) { manager.onBossVanished(PumpkinKing.this); return; }
                pinHealth();
                updateBossBar();
                updateHolo();
                ambientAura();

                if (skillCd > 0) skillCd--;
                if (scytheCd > 0) scytheCd--;
                if (teleCd > 0) teleCd--;
                if (summonCd > 0) summonCd--;
                if (busy > 0) { busy--; return; }

                double moved = base.getLocation().distance(lastLoc);
                lastLoc = base.getLocation();

                LivingEntity t = base.getTarget();
                if (t instanceof Player p && p.isValid() && !p.isDead()
                        && p.getGameMode() != GameMode.SPECTATOR) {
                    double dist = base.getLocation().distance(p.getLocation());
                    if (!engaged) { engaged = true; greet(); return; }
                    if (summonCd <= 0 && !nearbyPlayers(9).isEmpty()) { summon(); summonCd = 360; return; }
                    if (scytheCd <= 0 && dist > 4 && dist < 24) { throwScythe(p); scytheCd = 150; return; }
                    if (teleCd <= 0 && dist > 9 && dist < 32) { teleportTo(p); teleCd = 170; return; }
                    if (dist <= 5 && skillCd <= 0) { swingCombo(p); skillCd = 45; return; }
                }
                if (moved > 0.10) playLoop("walk");
                else playLoop("idle");
            }
        };
        controller.runTaskTimer(plugin, 1L, 1L);
    }

    // ───── skills (todas as animações do pack) ─────
    private void greet() {
        busy = 26;
        playOnce("greet", 1.0, 26);
        sfx(SND_LAUGH, Sound.ENTITY_WITCH_CELEBRATE, 1.4f, 0.7f);
        base.getWorld().spawnParticle(Particle.FLAME, base.getLocation().add(0, 1, 0), 50, 0.9, 1.1, 0.9, 0.05);
        base.getWorld().spawnParticle(Particle.SOUL, base.getLocation().add(0, 1, 0), 40, 0.9, 1.1, 0.9, 0.05);
        for (Player p : nearbyPlayers(BOSSBAR_RANGE))
            p.sendMessage(MM.deserialize("<#ff9f00><bold>Spooky</bold> <#a4a4a4>desperta na arena... corram!"));
    }

    private void swingCombo(Player target) {
        face(target);
        busy = 30;
        playOnce("swing_1", 1.0, 0);
        sfx(SND_SWING, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.9f);
        slashArc(3.6, Particle.FLAME, 10);
        meleeHit(4.2, dmg(8, 12), 0.6);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dead || !base.isValid()) return;
            playOnce("swing_2", 1.0, 0);
            sfx(SND_SWING, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 1.1f);
            slashArc(4.0, Particle.SOUL_FIRE_FLAME, 12);
            meleeHit(4.5, dmg(10, 14), 0.7);
        }, 15L);
    }

    private void throwScythe(Player target) {
        face(target);
        busy = 28;
        playOnce("throw_scythe", 1.0, 0);
        sfx(SND_SCYTHE, Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 0.9f);
        final Vector dir = target.getLocation().toVector().subtract(base.getLocation().toVector()).setY(0);
        if (dir.lengthSquared() < 0.001) return;
        dir.normalize();
        final Location start = base.getLocation().add(0, 1.2, 0);
        final Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            double d = 1.5;
            @Override public void run() {
                if (dead || !base.isValid() || d > 26) { cancel(); return; }
                Location at = start.clone().add(dir.clone().multiply(d));
                base.getWorld().spawnParticle(Particle.FLAME, at, 6, 0.2, 0.2, 0.2, 0.01);
                base.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, at, 4, 0.2, 0.2, 0.2, 0.01);
                base.getWorld().spawnParticle(Particle.CRIT, at, 3, 0.2, 0.2, 0.2, 0.05);
                for (Player p : base.getWorld().getPlayers()) {
                    if (p.getGameMode() == GameMode.SPECTATOR) continue;
                    if (p.getLocation().distance(at) <= 1.8 && hit.add(p.getUniqueId())) {
                        p.damage(dmg(9, 13), base);
                        knock(p, 0.5, 0.3);
                    }
                }
                d += 1.2;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void teleportTo(Player target) {
        busy = 24;
        playOnce("teleport", 1.0, 0);
        sfx(SND_TELE, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f);
        base.getWorld().spawnParticle(Particle.PORTAL, base.getLocation().add(0, 1, 0), 50, 0.6, 1, 0.6, 0.6);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dead || !base.isValid() || !target.isValid()) return;
            Vector behind = target.getLocation().getDirection().setY(0).normalize().multiply(-2.5);
            Location to = target.getLocation().add(behind);
            to.setY(target.getLocation().getY());
            base.teleport(to);
            face(target);
            sfx(SND_TELE, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.1f);
            base.getWorld().spawnParticle(Particle.FLAME, base.getLocation().add(0, 1, 0), 30, 0.6, 1, 0.6, 0.05);
        }, 12L);
    }

    /** SUMMON: levanta lápides ao redor, dá dano e PRENDE quem está perto por 5s (igual o Cavaleiro). */
    private void summon() {
        busy = 50;
        playOnce("summon", 1.0, 50);
        sfx(SND_TOMB, Sound.ENTITY_WITHER_SPAWN, 1.3f, 0.8f);
        base.getWorld().spawnParticle(Particle.SOUL, base.getLocation().add(0, 1, 0), 70, 1, 1.2, 1, 0.1);
        for (Player p : nearbyPlayers(BOSSBAR_RANGE))
            p.sendMessage(MM.deserialize("<#ff9f00>Spooky <#a4a4a4>conjura as lápides...</#a4a4a4>"));

        // 18 ticks depois (no meio da animação): as lápides sobem + dano + prende.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dead || !base.isValid()) return;
            sfx(SND_TOMB, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1.2f, 0.7f);
            for (int i = 0; i < 7; i++) {
                double ang = 2 * Math.PI * i / 7;
                double r = 3.0 + ThreadLocalRandom.current().nextDouble() * 1.5;
                Location at = base.getLocation().add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
                vfxModel("pumpkin_king_tombstone", at, 90);   // a LÁPIDE do pack
                base.getWorld().spawnParticle(Particle.SOUL, at.clone().add(0, 0.4, 0), 20, 0.3, 0.6, 0.3, 0.05);
                base.getWorld().spawnParticle(Particle.FLAME, at, 16, 0.3, 0.4, 0.3, 0.02);
                base.getWorld().spawnParticle(Particle.BLOCK, at, 12, 0.4, 0.2, 0.4, 0,
                        org.bukkit.Material.DEEPSLATE_TILES.createBlockData());
            }
            ring(5.0, Particle.FLAME, 30, 0.25);
            // Dano + PRENDE por 5s (escudo levantado NÃO prende, igual o Cavaleiro; ancorado no chão = sem fly bug).
            for (Player p : nearbyPlayers(7.0)) {
                boolean blocking = p.isBlocking();
                p.damage(dmg(7, 11), base);
                if (!blocking) manager.freezePlayer(p, base, 100);
            }
        }, 18L);
    }

    /** Spawna um modelo do pack (ex: a lápide) numa posição e remove depois de {@code life} ticks. */
    private void vfxModel(String modelId, Location loc, long life) {
        if (!ModelEngineHook.isAvailable()) return;
        ArmorStand host = loc.getWorld().spawn(loc, ArmorStand.class, a -> {
            a.setInvisible(true);
            a.setMarker(true);
            a.setGravity(false);
            a.setInvulnerable(true);
            a.setCollidable(false);
            a.setSilent(true);
            a.setPersistent(false);
        });
        ModelEngineHook.applyModel(host, modelId);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if (host.isValid()) host.remove(); }, life);
    }

    // ───── morte ─────
    @Override public void startDeathSequence() {
        if (dead) return;
        dead = true;
        if (controller != null) controller.cancel();
        base.setAI(false);
        base.setInvulnerable(true);
        playOnce("death", 1.0, 0);
        sfx(SND_DEATH, Sound.ENTITY_WITHER_DEATH, 1.4f, 0.8f);
        sfx(SND_LAUGH, Sound.ENTITY_WITCH_DEATH, 1f, 0.7f);
        base.getWorld().spawnParticle(Particle.FLAME, base.getLocation().add(0, 1, 0), 90, 1, 1.5, 1, 0.1);
        base.getWorld().spawnParticle(Particle.SOUL, base.getLocation().add(0, 1, 0), 70, 1, 1.5, 1, 0.05);
        base.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, base.getLocation().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            manager.onBossDefeated(this, damage, damagerNames);
            hardRemove();
        }, 55L);
    }

    @Override public void remove() {
        dead = true;
        if (controller != null) controller.cancel();
        hardRemove();
    }

    private void hardRemove() {
        for (UUID id : barViewers) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) p.hideBossBar(bossBar);
        }
        barViewers.clear();
        if (holo != null && holo.isValid()) holo.remove();
        if (base.isValid()) base.remove();
    }

    // ───── bossbar / holo ─────
    private void pinHealth() {
        var mh = base.getAttribute(Attribute.MAX_HEALTH);
        double m = (mh != null) ? mh.getValue() : 20.0;
        if (base.getHealth() < m) base.setHealth(m);
    }

    private void updateBossBar() {
        bossBar.progress((float) Math.max(0.0, Math.min(1.0, hp / MAX_HP)));
        Set<UUID> nowNear = new HashSet<>();
        for (Player p : base.getWorld().getPlayers()) {
            if (p.getLocation().distance(base.getLocation()) <= BOSSBAR_RANGE) {
                nowNear.add(p.getUniqueId());
                if (barViewers.add(p.getUniqueId())) p.showBossBar(bossBar);
            }
        }
        barViewers.removeIf(id -> {
            if (!nowNear.contains(id)) {
                Player p = plugin.getServer().getPlayer(id);
                if (p != null) p.hideBossBar(bossBar);
                return true;
            }
            return false;
        });
    }

    private TextDisplay spawnHolo() {
        return base.getWorld().spawn(base.getLocation().add(0, HOLO_Y, 0), TextDisplay.class, t -> {
            t.setBillboard(Display.Billboard.VERTICAL);
            t.setSeeThrough(false);
            t.setShadowed(true);
            t.setPersistent(false);
            t.setViewRange(10000f);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setAlignment(TextDisplay.TextAlignment.CENTER);
            t.setBrightness(new Display.Brightness(15, 15));
            t.text(MM.deserialize(holoText()));
        });
    }

    private void updateHolo() {
        if (holo == null || !holo.isValid()) return;
        holo.teleport(base.getLocation().add(0, HOLO_Y, 0));
        if (hp != lastHoloHp) { lastHoloHp = hp; holo.text(MM.deserialize(holoText())); }
    }

    private String holoText() {
        double pct = Math.max(0, Math.min(1, hp / MAX_HP));
        String cor = pct > 0.5 ? "#10fc46" : pct > 0.25 ? "#fcc850" : "#e22c27";
        return NAME + "\n<" + cor + ">" + String.format(Locale.US, "%,.0f", hp) + " ❤";
    }

    // ───── util ─────
    private void playLoop(String name) {
        if (name.equals(currentLoop)) return;
        currentLoop = name;
        ModelEngineHook.playAnimation(model, name, 0.2, 0.2, 1.0, false);
    }

    private void playOnce(String name, double speed, int busyTicks) {
        currentLoop = "";
        if (busyTicks > 0) busy = Math.max(busy, busyTicks);
        ModelEngineHook.playAnimation(model, name, 0.1, 0.1, speed, true);
    }

    private void sfx(String customKey, Sound vanilla, float vol, float pitch) {
        base.getWorld().playSound(net.kyori.adventure.sound.Sound.sound(Key.key(customKey),
                net.kyori.adventure.sound.Sound.Source.MASTER, vol, pitch), base.getX(), base.getY(), base.getZ());
        base.getWorld().playSound(base.getLocation(), vanilla, vol, pitch);
    }

    private double dmg(double min, double max) {
        return (min + ThreadLocalRandom.current().nextDouble() * (max - min)) * DMG_MULT;
    }

    private void knock(Player p, double h, double v) {
        Vector kb = p.getLocation().toVector().subtract(base.getLocation().toVector()).setY(0);
        if (kb.lengthSquared() < 0.001) kb = base.getLocation().getDirection().setY(0);
        kb.normalize().multiply(h * 0.7).setY(Math.min(v, 0.33));
        p.setVelocity(p.getVelocity().add(kb));
    }

    private void face(LivingEntity t) {
        if (t == null) return;
        Vector dir = t.getLocation().toVector().subtract(base.getLocation().toVector());
        if (dir.getX() == 0 && dir.getZ() == 0) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
        base.setRotation(yaw, 0f);
    }

    private void meleeHit(double reach, double damage, double kb) {
        Vector facing = base.getLocation().getDirection().setY(0).normalize();
        for (Player p : nearbyPlayers(reach)) {
            Vector to = p.getLocation().toVector().subtract(base.getLocation().toVector()).setY(0);
            if (to.lengthSquared() < 0.001 || facing.angle(to) <= Math.toRadians(100)) {
                p.damage(damage, base);
                knock(p, kb, 0.25);
            }
        }
    }

    private void slashArc(double reach, Particle p, int count) {
        Vector dir = base.getLocation().getDirection().setY(0).normalize();
        Location eye = base.getLocation().add(0, 1.2, 0);
        for (int i = 0; i <= count; i++) {
            double a = -70 + (140.0 * i / count);
            Vector v = rotY(dir, Math.toRadians(a)).multiply(reach);
            base.getWorld().spawnParticle(p, eye.clone().add(v), 1, 0, 0, 0, 0);
        }
    }

    private void ring(double radius, Particle p, int points, double yOff) {
        Location c = base.getLocation().add(0, yOff, 0);
        for (int i = 0; i < points; i++) {
            double t = 2 * Math.PI * i / points;
            base.getWorld().spawnParticle(p, c.clone().add(Math.cos(t) * radius, 0, Math.sin(t) * radius), 1, 0, 0, 0, 0);
        }
    }

    private void ambientAura() {
        if (++auraTick % 4 != 0) return;
        Location feet = base.getLocation();
        base.getWorld().spawnParticle(Particle.FLAME, feet.clone().add(0, 0.1, 0), 2, 0.35, 0.05, 0.35, 0.005);
        base.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, feet.clone().add(0, 1.0, 0), 2, 0.35, 0.7, 0.35, 0.01);
        if (auraTick % 90 == 0) sfx(SND_LAUGH, Sound.ENTITY_WITCH_AMBIENT, 0.8f, 0.7f);
    }

    private void spawnBurst() {
        base.getWorld().spawnParticle(Particle.SOUL, base.getLocation().add(0, 1, 0), 50, 0.6, 1, 0.6, 0.1);
        base.getWorld().spawnParticle(Particle.FLAME, base.getLocation().add(0, 1, 0), 40, 0.6, 1, 0.6, 0.05);
    }

    private List<Player> nearbyPlayers(double r) {
        List<Player> out = new ArrayList<>();
        for (Player p : base.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            if (p.getLocation().distance(base.getLocation()) <= r) out.add(p);
        }
        return out;
    }

    private static Vector rotY(Vector v, double rad) {
        double cos = Math.cos(rad), sin = Math.sin(rad);
        return new Vector(v.getX() * cos - v.getZ() * sin, v.getY(), v.getX() * sin + v.getZ() * cos);
    }

    private static void setAttr(LivingEntity e, Attribute a, double v) {
        var inst = e.getAttribute(a);
        if (inst != null) inst.setBaseValue(v);
    }
}
