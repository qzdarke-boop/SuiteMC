package com.psdk.boss;

import com.psdk.PSDK;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.GameMode;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
 * Boss "Tower Skeleton" (modelo littleroom tower_skeleton). Duas fases:
 *  • FASE 1 (escudo): tanky (toma menos dano), shield_bash/slam/charge/flamethrower/stomp/scream.
 *  • Quando a vida chega a 50% o ESCUDO QUEBRA -> imbue_sword -> FASE 2 (espada de alma):
 *    agressivo, soul_sword_attack_1/2/3, combo, stomp, grab. Dorme até alguém chegar (sleep -> awaken).
 */
public class TowerSkeleton implements BossEntity {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public static final String MODEL_ID = "tower_skeleton";
    public static final String NAME_PLAIN = "Tower Skeleton";
    public static final String NAME = "<gradient:#9be7ff:#3a82c4:#1b2a55><bold>" + NAME_PLAIN + "</bold></gradient>";
    private static final double MAX_HP = 12000.0;
    private static final double REAL_MAX = 1024.0;
    private static final double DMG_MULT = 0.35;
    private static final double BOSSBAR_RANGE = 50.0;
    private static final double HOLO_Y = 6.5;          // boss alto

    // Sons custom (littleroom_towerskeleton)
    private static final String SND_SWORD   = "littleroom_towerskeleton:sword_swing";
    private static final String SND_SWING   = "littleroom_towerskeleton:swing";
    private static final String SND_SHIMP   = "littleroom_towerskeleton:shield_impact";
    private static final String SND_SLAM    = "littleroom_towerskeleton:slam";
    private static final String SND_SCREAM  = "littleroom_towerskeleton:scream";
    private static final String SND_GRUNT   = "littleroom_towerskeleton:grunt";
    private static final String SND_IMBUE   = "littleroom_towerskeleton:imbue_sword";
    private static final String SND_FLAME   = "littleroom_towerskeleton:shield_flamethrower_loop";
    private static final String SND_BREAK   = "littleroom_towerskeleton:shield_break_final";
    private static final String SND_JUMP    = "littleroom_towerskeleton:jump";
    private static final String SND_DEATH   = "littleroom_towerskeleton:death";
    private static final String SND_AWAKEN  = "littleroom_towerskeleton:awaken_laugh";

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
    private boolean shielded = true;          // fase 1 (escudo) -> toma menos dano
    private boolean phase2 = false;           // fase 2 (espada de alma)
    private int busy = 0, skillCd = 40, chargeCd = 160, flameCd = 220, screamCd = 300, grabCd = 220, summonCd = 240, auraTick = 0;
    private double hp = MAX_HP;
    private TextDisplay holo;
    private ArmorStand throne;                                  // trono cosmético (embaixo dele enquanto dorme)
    private final List<Zombie> minions = new ArrayList<>();     // lacaios invocados (tower_skeleton_minion)
    private double lastHoloHp = -1;

    public TowerSkeleton(PSDK plugin, BossManager manager, Location loc) {
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
            setAttr(z, Attribute.MOVEMENT_SPEED, 0.22);
            setAttr(z, Attribute.KNOCKBACK_RESISTANCE, 1.0);
            setAttr(z, Attribute.FOLLOW_RANGE, 40.0);
            setAttr(z, Attribute.ATTACK_DAMAGE, 0.0);
        });
        // Resiliente ao nome do blueprint: tenta variações comuns até uma existir (vê no console qual pegou).
        this.model = ModelEngineHook.applyModelAny(base,
                MODEL_ID, "littleroom_tower_skeleton", "littleroom_towerskeleton",
                "towerskeleton", "boss_tower_skeleton", "tower_skeleton_boss", "tower");
        this.bossBar = BossBar.bossBar(MM.deserialize(NAME), 1.0f, BossBar.Color.BLUE, BossBar.Overlay.NOTCHED_12);
        this.lastLoc = base.getLocation();
        playLoop("sleep");                    // começa DORMINDO até alguém chegar
        this.throne = spawnThrone();          // trono cosmético embaixo (enquanto dorme)
        this.holo = spawnHolo();
        startController();
    }

    // ───── BossEntity ─────
    @Override public Entity getBase() { return base; }
    @Override public boolean isDead() { return dead; }
    @Override public boolean isShielded() { return shielded; }   // BossManager reduz dano em 60% se true
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
        if (ThreadLocalRandom.current().nextInt(100) < 14)
            sfx(SND_GRUNT, Sound.ENTITY_SKELETON_HURT, 1f, 0.7f);
    }

    // ───── controlador ─────
    private void startController() {
        controller = new BukkitRunnable() {
            @Override public void run() {
                if (dead) return;
                if (base.isDead() || !base.isValid()) { manager.onBossVanished(TowerSkeleton.this); return; }
                pinHealth();
                updateBossBar();
                updateHolo();
                ambientAura();

                if (skillCd > 0) skillCd--;
                if (chargeCd > 0) chargeCd--;
                if (flameCd > 0) flameCd--;
                if (screamCd > 0) screamCd--;
                if (grabCd > 0) grabCd--;
                if (summonCd > 0) summonCd--;

                // Quebra do escudo aos 50% -> fase 2.
                if (shielded && hp / MAX_HP <= 0.5) { breakShield(); return; }

                if (busy > 0) { busy--; return; }

                double moved = base.getLocation().distance(lastLoc);
                lastLoc = base.getLocation();

                LivingEntity t = base.getTarget();
                if (t instanceof Player p && p.isValid() && !p.isDead()
                        && p.getGameMode() != GameMode.SPECTATOR) {
                    double dist = base.getLocation().distance(p.getLocation());
                    if (!engaged) { engaged = true; awaken(); return; }
                    if (phase2) phase2Skills(p, dist);
                    else        phase1Skills(p, dist);
                    return;
                }
                // sem alvo: locomoção / dorme se nunca engajou
                if (!engaged) { playLoop("sleep"); return; }
                if (moved > 0.10) playLoop(phase2 ? "walk_soul_sword" : "walk_shield");
                else playLoop(phase2 ? "idle_soul_sword" : "idle_shield");
            }
        };
        controller.runTaskTimer(plugin, 1L, 1L);
    }

    private void phase1Skills(Player p, double dist) {
        if (summonCd <= 0 && !nearbyPlayers(20).isEmpty()) { summon(); summonCd = 420; return; }
        if (screamCd <= 0 && !nearbyPlayers(8).isEmpty()) { scream(); screamCd = 320; return; }
        if (flameCd <= 0 && dist > 3 && dist < 12) { flamethrower(p); flameCd = 260; return; }
        if (chargeCd <= 0 && dist > 6 && dist < 22) { shieldCharge(p); chargeCd = 200; return; }
        if (dist <= 5 && skillCd <= 0) {
            if (ThreadLocalRandom.current().nextInt(100) < 35) shieldSlam(p);
            else shieldBash(p);
            skillCd = 50;
            return;
        }
        if (base.getLocation().distance(lastLoc) > 0.10) playLoop("walk_shield");
        else playLoop("idle_shield");
    }

    private void phase2Skills(Player p, double dist) {
        if (summonCd <= 0 && !nearbyPlayers(20).isEmpty()) { summon(); summonCd = 340; return; }
        if (grabCd <= 0 && dist > 4 && dist < 14) { grab(p); grabCd = 200; return; }
        if (dist <= 5.5 && skillCd <= 0) {
            int r = ThreadLocalRandom.current().nextInt(100);
            if (r < 30) soulStomp(p);
            else if (r < 65) soulCombo(p);
            else soulTriple(p);
            skillCd = 35;
            return;
        }
        if (base.getLocation().distance(lastLoc) > 0.10) playLoop("walk_soul_sword");
        else playLoop("idle_soul_sword");
    }

    // ───── intro / transição ─────
    private void awaken() {
        busy = 40;
        if (throne != null && throne.isValid()) { throne.remove(); throne = null; }   // levanta do trono
        playOnce("awaken", 1.0, 40);
        sfx(SND_AWAKEN, Sound.ENTITY_WITHER_SPAWN, 1.4f, 0.6f);
        base.getWorld().spawnParticle(Particle.SOUL, base.getLocation().add(0, 1, 0), 60, 0.9, 1.2, 0.9, 0.08);
        for (Player p : nearbyPlayers(BOSSBAR_RANGE))
            p.sendMessage(MM.deserialize("<#9be7ff><bold>Tower Skeleton</bold> <#a4a4a4>desperta da torre..."));
    }

    private void breakShield() {
        shielded = false;
        phase2 = true;
        busy = 60;
        playOnce("shield_break_final", 1.0, 30);
        sfx(SND_BREAK, Sound.ITEM_SHIELD_BREAK, 1.5f, 0.7f);
        vfxModel("tower_skeleton_shield_break", base.getLocation(), 60);
        shockwave(7.0);
        for (Player p : nearbyPlayers(7)) { p.damage(dmg(6, 9), base); knock(p, 0.8, 0.4); }
        setAttr(base, Attribute.MOVEMENT_SPEED, 0.30);   // fica mais rápido sem escudo
        for (Player p : nearbyPlayers(BOSSBAR_RANGE))
            p.sendMessage(MM.deserialize("<#ff5959><bold>O ESCUDO QUEBROU!</bold> <#9be7ff>Tower Skeleton saca a espada de alma!"));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dead || !base.isValid()) return;
            playOnce("imbue_sword", 1.0, 26);
            sfx(SND_IMBUE, Sound.ITEM_TRIDENT_THUNDER, 1.3f, 1.0f);
            base.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, base.getLocation().add(0, 1.5, 0), 50, 0.6, 1, 0.6, 0.06);
        }, 32L);
    }

    // ───── FASE 1 (escudo) ─────
    private void shieldBash(Player target) {
        face(target);
        busy = 26;
        playOnce("shield_bash", 1.0, 0);
        sfx(SND_SHIMP, Sound.ITEM_SHIELD_BLOCK, 1f, 0.9f);
        meleeHit(4.2, dmg(7, 11), 0.8);
    }

    private void shieldSlam(Player target) {
        face(target);
        busy = 34;
        playOnce("shield_slam", 1.0, 0);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dead || !base.isValid()) return;
            sfx(SND_SLAM, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.3f, 0.8f);
            shockwave(5.5);
            groundCrack(6);
            for (Player p : nearbyPlayers(5.5)) { p.damage(dmg(9, 13), base); knock(p, 0.9, 0.45); }
        }, 12L);
    }

    private void shieldCharge(Player target) {
        face(target);
        busy = 40;
        playOnce("shield_charge", 1.0, 8);
        sfx(SND_JUMP, Sound.ENTITY_RAVAGER_ROAR, 1f, 0.8f);
        final Set<UUID> hitSet = new HashSet<>();
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (dead || !base.isValid() || t >= 22) {
                    if (!dead && base.isValid()) playOnce("shield_charge_stop", 1.0, 8);
                    cancel(); return;
                }
                if (t == 0) playLoop("shield_charge_run");
                Vector dir = base.getLocation().getDirection().setY(0).normalize().multiply(0.65);
                base.setVelocity(dir.setY(0.02));
                base.getWorld().spawnParticle(Particle.CRIT, base.getLocation().add(0, 1, 0), 8, 0.4, 0.6, 0.4, 0.05);
                for (Player p : nearbyPlayers(2.6)) {
                    if (hitSet.add(p.getUniqueId())) { p.damage(dmg(8, 12), base); knock(p, 1.0, 0.4); }
                }
                t++;
            }
        }.runTaskTimer(plugin, 2L, 1L);
    }

    private void flamethrower(Player target) {
        face(target);
        busy = 50;
        playOnce("shield_flamethrower", 1.0, 50);
        sfx(SND_FLAME, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.8f);
        final Vector dir = base.getLocation().getDirection().setY(0).normalize();
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (dead || !base.isValid() || t >= 40) { cancel(); return; }
                Location eye = base.getLocation().add(0, 1.3, 0);
                for (double d = 1.5; d <= 8; d += 0.7) {
                    Location at = eye.clone().add(dir.clone().multiply(d));
                    base.getWorld().spawnParticle(Particle.FLAME, at, 3, 0.25, 0.25, 0.25, 0.02);
                    base.getWorld().spawnParticle(Particle.SMOKE, at, 1, 0.2, 0.2, 0.2, 0.01);
                }
                for (Player p : nearbyPlayers(8.5)) {
                    Vector to = p.getLocation().toVector().subtract(base.getLocation().toVector()).setY(0);
                    if (to.lengthSquared() > 0.01 && dir.angle(to) <= Math.toRadians(28)) {
                        p.setFireTicks(40);
                        if (t % 6 == 0) p.damage(dmg(2, 3), base);
                    }
                }
                t++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void scream() {
        busy = 36;
        playOnce("scream", 1.0, 36);
        sfx(SND_SCREAM, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.4f, 0.8f);
        ring(4.0, Particle.SOUL, 30, 1.0);
        base.getWorld().spawnParticle(Particle.SONIC_BOOM, base.getLocation().add(0, 1.2, 0), 1, 0, 0, 0, 0);
        for (Player p : nearbyPlayers(10)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0, false, true));
            p.damage(dmg(4, 6), base);
        }
    }

    // ───── FASE 2 (espada de alma) ─────
    private void soulCombo(Player target) {
        face(target);
        busy = 30;
        playOnce("combo_no_shield", 1.0, 0);
        sfx(SND_SWORD, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.0f);
        slashArc(3.8, Particle.SOUL_FIRE_FLAME, 12);
        vfxModel("tower_skeleton_slash_fx", base.getLocation(), 14);
        meleeHit(4.4, dmg(9, 13), 0.6);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dead || !base.isValid()) return;
            playOnce("combo_2_no_shield", 1.0, 0);
            sfx(SND_SWORD, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 1.1f);
            slashArc(4.0, Particle.SOUL, 12);
            vfxModel("tower_skeleton_slash_fx", base.getLocation(), 14);
            meleeHit(4.6, dmg(10, 14), 0.7);
        }, 14L);
    }

    private void soulTriple(Player target) {
        face(target);
        busy = 44;
        String[] anims = {"soul_sword_attack_1", "soul_sword_attack_2", "soul_sword_attack_3"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (dead || !base.isValid()) return;
                playOnce(anims[idx], 1.1, 0);
                sfx(SND_SWORD, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.95f + idx * 0.05f);
                slashArc(3.8, idx == 2 ? Particle.SOUL_FIRE_FLAME : Particle.SOUL, 12);
                vfxModel("tower_skeleton_slash_fx", base.getLocation(), 12);
                meleeHit(4.4, dmg(8, 12), 0.5);
            }, i * 12L);
        }
    }

    private void soulStomp(Player target) {
        face(target);
        busy = 32;
        playOnce("soul_sword_stomp", 1.0, 0);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dead || !base.isValid()) return;
            sfx(SND_SLAM, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.0f);
            shockwave(6.0);
            base.getWorld().spawnParticle(Particle.SOUL, base.getLocation().add(0, 0.3, 0), 40, 2, 0.3, 2, 0.05);
            for (Player p : nearbyPlayers(6)) { p.damage(dmg(10, 14), base); knock(p, 0.8, 0.5); }
        }, 12L);
    }

    private void grab(Player target) {
        face(target);
        busy = 34;
        playOnce("grab_player", 1.0, 0);
        sfx(SND_SWING, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.9f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dead || !base.isValid() || !target.isValid()) return;
            // puxa o alvo pra perto + dano
            Vector pull = base.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
            if (pull.lengthSquared() > 0.01) {
                pull.normalize().multiply(1.4).setY(0.3);
                target.setVelocity(pull);
            }
            target.damage(dmg(9, 13), base);
            base.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, target.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.03);
        }, 10L);
    }

    // ───── morte ─────
    @Override public void startDeathSequence() {
        if (dead) return;
        dead = true;
        if (controller != null) controller.cancel();
        base.setAI(false);
        base.setInvulnerable(true);
        playOnce("death", 1.0, 0);
        sfx(SND_DEATH, Sound.ENTITY_WITHER_DEATH, 1.4f, 0.7f);
        base.getWorld().spawnParticle(Particle.SOUL, base.getLocation().add(0, 1, 0), 90, 1, 1.6, 1, 0.06);
        base.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, base.getLocation().add(0, 1, 0), 70, 1, 1.6, 1, 0.04);
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
        for (Zombie m : minions) if (m != null && m.isValid()) m.remove();   // limpa os lacaios invocados
        minions.clear();
        if (throne != null && throne.isValid()) throne.remove();
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
        bossBar.color(shielded ? BossBar.Color.BLUE : BossBar.Color.PURPLE);
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
        String tag = shielded ? " <#9be7ff>🛡" : " <#c77dff>⚔";
        return NAME + tag + "\n<" + cor + ">" + String.format(Locale.US, "%,.0f", hp) + " ❤";
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

    private void vfxModel(String modelId, Location loc, long life) { vfxModel(modelId, loc, "animation", life); }

    private void vfxModel(String modelId, Location loc, String anim, long life) {
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
        Object m = ModelEngineHook.applyModel(host, modelId);
        if (m != null) ModelEngineHook.playAnimation(m, anim, 0.0, 0.1, 1.0, true);   // anima o VFX
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if (host.isValid()) host.remove(); }, life);
    }

    // ───── invocação de lacaios (summon_hole + minion) ─────
    private void summon() {
        busy = 46;
        playOnce("scream", 1.0, 0);                 // gesto de invocação
        sfx(SND_SCREAM, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.3f, 0.8f);
        int count = phase2 ? 3 : 2;
        for (int i = 0; i < count; i++) {
            double ang = 2 * Math.PI * i / count + ThreadLocalRandom.current().nextDouble();
            final Location at = base.getLocation().add(Math.cos(ang) * 3.2, 0, Math.sin(ang) * 3.2);
            vfxModel("tower_skeleton_summon_hole", at, 50);   // o buraco abre
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (dead || !base.isValid()) return;
                spawnMinion(at);                              // o lacaio sobe do buraco
            }, 18L);
        }
        for (Player p : nearbyPlayers(BOSSBAR_RANGE))
            p.sendMessage(MM.deserialize("<#c77dff><bold>Tower Skeleton</bold> <#a4a4a4>invoca lacaios da torre!"));
    }

    private void spawnMinion(Location at) {
        if (minions.size() >= 6) return;                      // teto de lacaios
        Zombie m = at.getWorld().spawn(at, Zombie.class, z -> {
            z.setAdult();
            z.setInvisible(true);                             // só o modelo aparece
            z.setSilent(true);
            z.setRemoveWhenFarAway(false);
            z.setShouldBurnInDay(false);
            z.setCustomNameVisible(false);
            setAttr(z, Attribute.MAX_HEALTH, 30.0);
            z.setHealth(30.0);
            setAttr(z, Attribute.MOVEMENT_SPEED, 0.28);
            setAttr(z, Attribute.ATTACK_DAMAGE, 4.0);
            setAttr(z, Attribute.FOLLOW_RANGE, 30.0);
        });
        ModelEngineHook.applyModel(m, "tower_skeleton_minion");
        at.getWorld().spawnParticle(Particle.SOUL, at.clone().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.05);
        minions.add(m);
    }

    private ArmorStand spawnThrone() {
        if (!ModelEngineHook.isAvailable()) return null;
        ArmorStand host = base.getLocation().getWorld().spawn(base.getLocation(), ArmorStand.class, a -> {
            a.setInvisible(true);
            a.setMarker(true);
            a.setGravity(false);
            a.setInvulnerable(true);
            a.setCollidable(false);
            a.setSilent(true);
            a.setPersistent(false);
        });
        ModelEngineHook.applyModel(host, "tower_skeleton_throne");
        return host;
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
        Location eye = base.getLocation().add(0, 1.4, 0);
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

    private void shockwave(double maxR) {
        for (int step = 0; step < 6; step++) {
            final double rr = 1.0 + step * (maxR - 1.0) / 5.0;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!base.isValid()) return;
                ring(rr, Particle.SOUL, (int) Math.max(8, rr * 6), 0.25);
                ring(rr, Particle.CRIT, (int) Math.max(6, rr * 4), 0.3);
            }, step * 2L);
        }
    }

    private void groundCrack(double length) {
        Vector dir = base.getLocation().getDirection().setY(0).normalize();
        for (double d = 1; d <= length; d += 0.5) {
            Location l = base.getLocation().add(dir.clone().multiply(d)).add(0, 0.2, 0);
            base.getWorld().spawnParticle(Particle.CRIT, l, 3, 0.2, 0.1, 0.2, 0.02);
            base.getWorld().spawnParticle(Particle.SOUL, l, 1, 0.15, 0.05, 0.15, 0);
        }
    }

    private void ambientAura() {
        if (++auraTick % 4 != 0) return;
        Location feet = base.getLocation();
        base.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, feet.clone().add(0, 0.1, 0), 2, 0.35, 0.05, 0.35, 0.005);
        if (phase2)
            base.getWorld().spawnParticle(Particle.SOUL, feet.clone().add(0, 1.5, 0), 2, 0.3, 0.6, 0.3, 0.01);
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
