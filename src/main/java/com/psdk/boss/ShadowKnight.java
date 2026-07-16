package com.psdk.boss;

import com.psdk.PSDK;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Zombie;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cavaleiro das Sombras — boss próprio (PSDK) com modelo/animações + VFX 3D do
 * ModelEngine, e TODA a lógica em Java: vida, bossbar, alvo, fases, skills,
 * VFX autênticos do pack, partículas e morte. Sem MythicMobs.
 */
public class ShadowKnight implements BossEntity {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public static final String MODEL_ID = "boss_warrior";
    public static final String NAME_PLAIN = "Cavaleiro das Sombras";   // <-- RENOMEIE O BOSS AQUI
    public static final String NAME = "<gradient:#FF5959:#AE0000:#3A0000><bold>" + NAME_PLAIN + "</bold></gradient>";
    private static final double MAX_HP = 10000.0;   // vida REAL do boss (pool virtual)
    private static final double REAL_MAX = 1024.0;  // teto do atributo de vida do mob (limite do MC)
    private static final double BOSSBAR_RANGE = 50.0;
    private static final double HOLO_Y = 3.2;        // altura do holograma acima do boss (ajuste fino aqui)
    private static final double DMG_MULT = 0.35;     // escala global de dano
    // ── Ciclo de fadiga (descanso) ──
    private static final int REST_INTERVAL = 900;        // 45s de luta até cansar (ticks)
    private static final int REST_DURATION = 400;        // 20s EXAUSTO (descanso, vulnerável)
    private static final int BUFF_DURATION = 240;        // 12s de fúria/buff ao voltar
    private static final double TIRED_DMG_TAKEN = 1.8;   // toma +80% de dano enquanto cansado
    private static final double BUFF_DMG_DEALT  = 1.4;   // dá +40% de dano com o buff
    private static final Color SHADOW_RED = Color.fromRGB(170, 0, 0);

    // Sons custom do resourcepack do samus (com fallback vanilla nas skills).
    private static final String SND_SLASH   = "awakened_warrior_sounds:samus.awakened_warrior.warrior_slash";
    private static final String SND_PIERCE  = "awakened_warrior_sounds:samus.awakened_warrior.warrior_pierce";
    private static final String SND_WHEEL   = "awakened_warrior_sounds:samus.awakened_warrior.warrior_wheel_spin";
    private static final String SND_AIRDASH = "awakened_warrior_sounds:samus.awakened_warrior.warrior_airdash";
    private static final String SND_STOMP   = "awakened_warrior_sounds:samus.awakened_warrior.warrior_stomp";
    private static final String SND_ULTSTMP = "awakened_warrior_sounds:samus.awakened_warrior.ult_stomp";
    private static final String SND_CHARGE  = "awakened_warrior_sounds:samus.awakened_warrior.warrior_charge";
    private static final String SND_SLASHULT= "awakened_warrior_sounds:samus.awakened_warrior.warrior_slash_ult";
    private static final String SND_THUD    = "universal_sounds:samus.universal.hit_impact_thud";
    private static final String SND_RUPTURE = "universal_sounds:samus.universal.rupture_big";
    private static final String SND_STOMPCHG   = "awakened_warrior_sounds:samus.awakened_warrior.warrior_stomp_charge";
    private static final String SND_SLASHULTSL = "awakened_warrior_sounds:samus.awakened_warrior.warrior_slash_ult_slash";
    private static final String SND_MOVE       = "universal_sounds:samus.universal.move";
    private static final String SND_RUPTUREQ   = "universal_sounds:samus.universal.rupture_quick";

    // VFX models do pack (blueprints importados no ModelEngine).
    private static final String VFX_COMBO   = "brutal_combo_1";          // anims: slash_left/right, pierce, strike...
    private static final String VFX_VICIOUS = "vicious_strike_1";        // anim: animation
    private static final String VFX_FURY    = "strike_of_fury_1";        // anims: sword_slash1/2, floor_crack
    private static final String VFX_WHIRL   = "relentless_whirlwind_1";  // anims: spin, dash_pierce
    private static final String VFX_LEAP    = "berserker_leap_1";        // anim: animation
    private static final String VFX_RUPTURE = "vfx_earthquake_rupture_1";// anim: skill
    private static final String VFX_RUBBLES = "vfx_rubbles";             // anim: skill

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
    private boolean phase2 = false;
    private boolean dead = false;

    private int busy = 0;
    private int skillCd = 40;
    private int leapCd = 80;
    private int dashCd = 60;
    private int waveCd = 100;
    private int eruptCd = 240;
    private int barrierCd = 300;
    private int strafeCd = 0;
    private int bulwarkCd = 160;     // Bulwark Instinct (grito + Resistência — skill 1 da demo)
    private int stuckTicks = 0;
    private int auraTick = 0;
    private boolean shielded = false;
    private boolean engaged = false;
    private boolean tired = false;        // EXAUSTO (descanso)
    private int tiredTicks = 0;           // contagem regressiva do descanso
    private int restCd = REST_INTERVAL;   // até o próximo descanso
    private int buffTicks = 0;            // duração do buff pós-descanso
    private double hp = MAX_HP;   // vida virtual (10k)
    private TextDisplay holo;     // holograma (nome + vida) que segue a cabeça
    private double lastHoloHp = -1;

    public ShadowKnight(PSDK plugin, BossManager manager, Location loc) {
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
            setAttr(z, Attribute.MOVEMENT_SPEED, 0.25);
            setAttr(z, Attribute.KNOCKBACK_RESISTANCE, 1.0);
            setAttr(z, Attribute.FOLLOW_RANGE, 40.0);
            setAttr(z, Attribute.ATTACK_DAMAGE, 0.0);
        });

        this.model = ModelEngineHook.applyModel(base, MODEL_ID);
        this.bossBar = BossBar.bossBar(MM.deserialize(NAME), 1.0f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_12);
        this.lastLoc = base.getLocation();

        playLoop("idle_prebattle");   // pose de espera; vira start_combat ao engajar
        sfx(SND_CHARGE, Sound.ENTITY_RAVAGER_ROAR, 1.3f, 0.9f);
        spawnBurst();
        this.holo = spawnHolo();
        startController();
    }

    public Zombie getBase() { return base; }
    public String getNamePlain() { return NAME_PLAIN; }
    public boolean isDead() { return dead; }
    public boolean isShielded() { return shielded; }
    public double getHp() { return hp; }
    public double getMaxHp() { return MAX_HP; }
    public Map<UUID, Double> getDamageMap() { return damage; }
    public Map<UUID, String> getDamagerNames() { return damagerNames; }
    public void damageBoss(double amount) {
        if (tired) amount *= TIRED_DMG_TAKEN;   // exausto = janela de punição (toma mais dano)
        hp = Math.max(0, hp - amount);
    }

    public void recordDamage(UUID uuid, String name, double amount) {
        if (dead) return;
        damage.merge(uuid, amount, Double::sum);
        damagerNames.put(uuid, name);
    }

    public void onHurt() {
        if (dead) return;
        if (shielded) vfxBoss("bloodbound_barrier", "hit", 12);   // reação da barreira ao tomar dano
        if (busy > 0) return;
        base.getWorld().spawnParticle(Particle.DUST, base.getLocation().add(0, 1.2, 0), 6, 0.4, 0.6, 0.4, dust(1.4f));
        int r = ThreadLocalRandom.current().nextInt(100);
        if (phase2 && r < 10) {                 // recuo evasivo
            busy = 14;
            playOnce("dash_back", 1.2, 0);
            vfxBoss(VFX_WHIRL, "back_step", 14);
            sfx(SND_AIRDASH, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8f, 1.3f);
            base.setVelocity(base.getLocation().getDirection().setY(0).normalize().multiply(-0.9).setY(0.2));
        } else if (r < 18) {
            playOnce("flinch", 1.0, 8);
        }
    }

    // ───────────────────────── controlador ─────────────────────────
    private void startController() {
        controller = new BukkitRunnable() {
            @Override public void run() {
                if (dead) return;
                if (base.isDead() || !base.isValid()) { manager.onBossVanished(ShadowKnight.this); return; }

                pinHealth();   // mantém a vida real do mob no teto; quem manda é a vida virtual
                updateBossBar();
                updateHolo();  // holograma segue a cabeça em tempo real
                ambientAura();

                // ── Ciclo de fadiga: descanso de 20s + buff ao voltar ──
                if (buffTicks > 0) buffTicks--;
                if (tired) {
                    tiredEffects();
                    if (--tiredTicks <= 0) endRest();
                    return;                       // EXAUSTO: não ataca nem se move
                }

                if (!phase2 && hp / MAX_HP < 0.60) enterPhase2();

                if (skillCd > 0) skillCd--;
                if (leapCd > 0) leapCd--;
                if (dashCd > 0) dashCd--;
                if (waveCd > 0) waveCd--;
                if (eruptCd > 0) eruptCd--;
                if (barrierCd > 0) barrierCd--;
                if (strafeCd > 0) strafeCd--;
                if (bulwarkCd > 0) bulwarkCd--;
                if (engaged && restCd > 0) restCd--;
                if (busy > 0) { busy--; return; }

                // Cansou? (só descansa fora de combo, depois de lutar um tempo)
                if (engaged && restCd <= 0) { startRest(); return; }

                double moved = base.getLocation().distance(lastLoc);
                lastLoc = base.getLocation();

                LivingEntity t = base.getTarget();
                if (t instanceof Player p && p.isValid() && !p.isDead()
                        && p.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                    double dist = base.getLocation().distance(p.getLocation());
                    if (!engaged) {                 // primeira vez que vê um alvo: desperta
                        engaged = true;
                        playOnce("start_combat", 1.0, 18);
                        return;
                    }
                    if (phase2 && barrierCd <= 0 && hp / MAX_HP < 0.5) {
                        bloodboundBarrier();
                        barrierCd = 600;
                        return;
                    }
                    // Bulwark Instinct: grito de fúria + Resistência (skill 1 da demo).
                    if (bulwarkCd <= 0 && hp / MAX_HP < 0.85) {
                        bulwarkInstinct();
                        bulwarkCd = phase2 ? 360 : 500;
                        return;
                    }
                    // ULT: Erupção Congelante (cooldown próprio, longo).
                    if (eruptCd <= 0 && dist <= 10 && !nearbyPlayers(8.5).isEmpty()) {
                        frozenEruption();
                        eruptCd = phase2 ? 360 : 520;
                        return;
                    }
                    // Em alcance de combate (perto o suficiente p/ conectar): sorteia entre TODAS as skills.
                    if (dist <= 5 && skillCd <= 0) {
                        useSkill(p, dist);
                        skillCd = phase2 ? 35 : 50;
                        return;
                    }
                    // Player fugiu (alcance médio): fecha a distância com salto ou dash.
                    if (dist > 5 && dist < 18) {
                        if (leapCd <= 0) { leap(p); leapCd = phase2 ? 100 : 160; return; }
                        if (dashCd <= 0) { dash(p); dashCd = phase2 ? 80 : 140; return; }
                    }
                    // Anti-travamento: tem alvo longe mas parou de andar -> pula pra cima.
                    if (dist > 4.2) {
                        if (moved < 0.02) {
                            if (++stuckTicks >= 20) { unstickJump(p); stuckTicks = 0; }
                        } else stuckTicks = 0;
                    }
                } else {
                    stuckTicks = 0;
                }
                if (moved > 0.10) playLoop("run");
                else if (moved > 0.03) playLoop("walk");
                else playLoop(engaged ? "idle" : "idle_prebattle");
            }
        };
        controller.runTaskTimer(plugin, 1L, 1L);
    }

    // ───────────────────────── ciclo de fadiga (descanso) ─────────────────────────
    private void startRest() {
        tired = true;
        tiredTicks = REST_DURATION;
        busy = 0;
        base.setAI(false);
        currentLoop = "";
        // Toca a animação pré-batalha em câmera lenta — parece que o boss está se curvando, exausto.
        playOnce("idle_prebattle", 0.4, 0);
        base.getWorld().playSound(base.getLocation(), Sound.ENTITY_RAVAGER_AMBIENT, 1.2f, 0.5f);
        base.getWorld().playSound(base.getLocation(), Sound.ENTITY_WARDEN_AMBIENT, 1.0f, 0.6f);
        for (Player p : nearbyPlayers(BOSSBAR_RANGE))
            p.sendMessage(MM.deserialize("<#6817ff>O " + NAME_PLAIN + " <#a4a4a4>está EXAUSTO — aproveitem a janela!"));
    }

    private void tiredEffects() {
        // Mantém a postura de cansaço; usa idle_prebattle em loop lento em vez de idle normal.
        if (!currentLoop.equals("idle_prebattle")) {
            currentLoop = "idle_prebattle";
            ModelEngineHook.playAnimation(model, "idle_prebattle", 0.15, 0.15, 0.4, false);
        }
        if (tiredTicks % 6 == 0) {
            base.getWorld().spawnParticle(Particle.FALLING_WATER, base.getLocation().add(0, 2.2, 0), 4, 0.35, 0.2, 0.35, 0);
            base.getWorld().spawnParticle(Particle.SMOKE, base.getLocation().add(0, 1.4, 0), 3, 0.3, 0.3, 0.3, 0.01);
        }
    }

    private void endRest() {
        tired = false;
        base.setAI(true);
        buffTicks = BUFF_DURATION;
        restCd = REST_INTERVAL;
        currentLoop = "";
        playOnce("bulwark_instinct", 1.0, 22);   // recupera o fôlego -> BUFFADO (fúria)
        sfx(SND_CHARGE, Sound.ENTITY_WITHER_AMBIENT, 1.3f, 0.8f);
        base.getWorld().spawnParticle(Particle.FLAME, base.getLocation().add(0, 1, 0), 60, 0.9, 1.1, 0.9, 0.05);
        base.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, base.getLocation().add(0, 1, 0), 40, 0.8, 1.0, 0.8, 0.03);
        shockwave(6.0);
    }

    /** Sorteia 1 ataque de um pool com TODAS as skills disponíveis (melee + especiais por cooldown). */
    private void useSkill(Player target, double dist) {
        face(target);
        java.util.List<Runnable> pool = new java.util.ArrayList<>();
        pool.add(this::combo3);
        pool.add(this::viciousStrike);
        pool.add(this::crossSlash);
        if (phase2) { pool.add(this::combo5); pool.add(this::strikeOfFury); }
        if (waveCd <= 0) pool.add(() -> { magmaWave(target); waveCd = phase2 ? 120 : 200; });
        if (dashCd <= 0) pool.add(() -> { dash(target);      dashCd = phase2 ? 80 : 140; });
        if (leapCd <= 0) pool.add(() -> { leap(target);      leapCd = phase2 ? 100 : 160; });
        if (strafeCd <= 0) pool.add(() -> { strafe(target);  strafeCd = phase2 ? 50 : 80; });
        pool.get(ThreadLocalRandom.current().nextInt(pool.size())).run();
    }

    // ───────────────────────── skills ─────────────────────────
    private void combo3() {
        busy = 28;
        swing("c1", SND_SLASH, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 3.9, dmg(6, 8), "slash_right", 0);
        swing("c2", SND_SLASH, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.12f, 3.9, dmg(6, 8), "slash_left", 8);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dead || !base.isValid()) return;
            playOnce("c3", 1.0, 0);
            sfx(SND_PIERCE, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.0f);
            sfx(SND_THUD, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.9f, 1.0f);
            vfxBoss(VFX_COMBO, "pierce", 18);
            slashArc(2.6, Particle.SWEEP_ATTACK, 7);
            meleeHit(4.3, dmg(8, 11), 0.6);
        }, 16L);
    }

    private void combo5() {
        busy = 46;
        sfx(SND_CHARGE, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.4f);
        String[] vanims = {"slash_left_diag", "slash_right_diag", "slash_up", "slash_up2", "strike"};
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (dead || !base.isValid()) return;
                playOnce("c" + (idx + 1), 1.1, 0);
                sfx(idx >= 3 ? SND_PIERCE : SND_SLASH,
                        idx >= 3 ? Sound.ENTITY_PLAYER_ATTACK_CRIT : Sound.ENTITY_PLAYER_ATTACK_SWEEP,
                        1f, 0.95f + idx * 0.05f);
                if (idx == 4) sfx(SND_SLASHULTSL, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 0.9f);
                vfxBoss(VFX_COMBO, vanims[idx], 16);
                slashArc(2.6, idx >= 3 ? Particle.FLAME : Particle.CRIT, 12);
                meleeHit(4.0, dmg(6, 9), 0.45);
            }, i * 8L);
        }
    }

    private void viciousStrike() {
        busy = 22;
        playOnce("vicious_strike", 1.0, 0);
        sfx(SND_SLASHULT, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 1.0f);
        vfxBoss(VFX_VICIOUS, "animation", 20);
        base.setVelocity(base.getLocation().getDirection().setY(0).normalize().multiply(0.55));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dead || !base.isValid()) return;
            sfx(SND_THUD, Sound.BLOCK_ANVIL_LAND, 1f, 0.9f);
            slashArc(2.8, Particle.ENCHANTED_HIT, 16);
            meleeHit(4.6, dmg(11, 15), 0.9);
        }, 9L);
    }

    // Blade of Fury: carrega a aura (espada cresce) e desfere 4 cortes em sequência (igual à demo).
    private void strikeOfFury() {
        busy = 58;
        playOnce("strike_of_fury_1", 1.0, 0);
        sfx(SND_SLASHULT, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 0.95f);
        vfxBoss(VFX_FURY, "charge_sword", 14);
        final String[] clips = {"strike_of_fury_1", "strike_of_fury_2", "strike_of_fury_1", "strike_of_fury_2"};
        final String[] svfx  = {"sword_slash1", "sword_slash2", "sword_slash1", "sword_slash2"};
        for (int i = 0; i < 4; i++) {                          // 4 cortes seguidos
            final int idx = i;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (dead || !base.isValid()) return;
                playOnce(clips[idx], 1.0, 0);
                sfx(SND_SLASH, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.85f + idx * 0.08f);
                vfxBoss(VFX_FURY, svfx[idx], 18);
                slashArc(3.4, Particle.ENCHANTED_HIT, 10);
                cleave(5.5, 110, dmg(7, 11));
            }, 12L + i * 9L);
        }
        // Remate: rachadura no chão com explosão.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dead || !base.isValid()) return;
            sfx(SND_PIERCE, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.1f, 0.9f);
            sfx(SND_RUPTURE, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
            vfxBoss(VFX_FURY, "floor_crack", 30);
            vfxAt(VFX_RUPTURE, base.getLocation(), "skill", 50);
            groundCrack(6.0);
            for (Player p : nearbyPlayers(5.5)) {
                if (inFront(p, 90)) { p.damage(dmg(12, 16), base); knock(p, 1.0, 0.4); }
            }
        }, 54L);
    }

    // Leque de cortes (substitui o giro): golpes largos com os rastros do pack, SEM rodar.
    private void crossSlash() {
        busy = 36;
        // 1) corte esquerdo
        playOnce("strike_of_fury_1", 1.0, 0);
        sfx(SND_SLASH, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.95f);
        vfxBoss(VFX_COMBO, "slash_left", 18);
        slashArc(3.0, Particle.SWEEP_ATTACK, 8);
        cleave(5.0, 120, dmg(7, 10));
        // 2) corte direito
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dead || !base.isValid()) return;
            playOnce("strike_of_fury_2", 1.0, 0);
            sfx(SND_SLASH, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.1f);
            vfxBoss(VFX_COMBO, "slash_right", 18);
            slashArc(3.0, Particle.CRIT, 12);
            cleave(5.0, 120, dmg(7, 10));
        }, 10L);
        // 3) estocada pra frente (pierce do relentless, sem girar)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dead || !base.isValid()) return;
            playOnce("relentless_whirlwind_pierce", 1.0, 0);
            sfx(SND_PIERCE, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.0f);
            sfx(SND_WHEEL, Sound.ITEM_TRIDENT_RIPTIDE_3, 0.8f, 1.0f);
            vfxBoss(VFX_COMBO, "pierce", 18);
            vfxBoss(VFX_WHIRL, "dash_pierce", 18);
            base.setVelocity(base.getLocation().getDirection().setY(0).normalize().multiply(0.5));
            cleave(5.5, 70, dmg(9, 13));
        }, 22L);
    }

    // Bulwark Instinct: grito de fúria, ganha Resistência por alguns segundos (skill 1 da demo).
    private void bulwarkInstinct() {
        busy = 30;
        playOnce("bulwark_instinct", 1.0, 0);
        sfx(SND_CHARGE, Sound.ENTITY_RAVAGER_ROAR, 1.3f, 0.8f);
        base.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.RESISTANCE, 140, 1, false, false));
        shockwave(4.0);
        base.getWorld().spawnParticle(Particle.ENCHANTED_HIT, base.getLocation().add(0, 1, 0), 30, 0.6, 0.8, 0.6, 0.2);
    }

    /** Dano em meia-lua na frente (cone), sem girar. */
    private void cleave(double reach, double angleDeg, double damage) {
        for (Player p : nearbyPlayers(reach)) {
            if (inFront(p, angleDeg)) { p.damage(damage, base); knock(p, 0.7, 0.25); }
        }
    }

    private void leap(Player target) {
        busy = 26;
        playOnce("berserker_leap", 1.0, 0);
        sfx(SND_AIRDASH, Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1.0f);
        vfxBoss(VFX_LEAP, "animation", 26);
        vfxBoss(VFX_FURY, "sword_jump", 22);
        face(target);
        Vector dir = target.getLocation().toVector().subtract(base.getLocation().toVector()).setY(0);
        if (dir.lengthSquared() < 0.001) dir = base.getLocation().getDirection().setY(0);   // evita NaN se colado
        dir.normalize().multiply(1.0).setY(0.55);
        base.setVelocity(dir);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dead || !base.isValid()) return;
            sfx(SND_ULTSTMP, Sound.ENTITY_GENERIC_EXPLODE, 1.3f, 1.0f);
            sfx(SND_STOMP, Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.8f);
            vfxAt(VFX_RUPTURE, base.getLocation(), "skill", 50);
            vfxAt(VFX_RUBBLES, base.getLocation(), "skill", 50);
            vfxBoss(VFX_FURY, "sword_stomp", 16);
            base.getWorld().spawnParticle(Particle.EXPLOSION, base.getLocation(), 3, 1, 0.5, 1, 0);
            shockwave(6.0);
            for (Player p : nearbyPlayers(5.5)) { p.damage(dmg(12, 16), base); knock(p, 1.15, 0.55); }
        }, 16L);
    }

    // Erupção Congelante: pula, cai abrindo os braços, ergue magma em volta e
    // CONGELA (enraíza) os jogadores próximos por 7s, atacando-os enquanto isso.
    private void frozenEruption() {
        busy = 54;
        playOnce("berserker_leap", 1.0, 0);
        sfx(SND_AIRDASH, Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1.0f);
        sfx(SND_STOMPCHG, Sound.BLOCK_BEACON_POWER_SELECT, 0.9f, 0.8f);
        base.setVelocity(new Vector(0, 0.75, 0));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dead || !base.isValid()) return;
            playOnce("bulwark_instinct", 1.0, 32);   // abre os braços / se estica
            sfx(SND_ULTSTMP, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.8f);
            sfx(SND_RUPTURE, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.7f);   // estrondo extra (custom)
            base.getWorld().playSound(base.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.8f);
            base.getWorld().playSound(base.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.3f, 0.7f);
            base.getWorld().playSound(base.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 0.8f);
            vfxAt(VFX_RUPTURE, base.getLocation(), "skill", 60);
            vfxAt(VFX_RUPTURE, base.getLocation(), "skill2", 60);
            vfxAt(VFX_RUPTURE, base.getLocation(), "skill3", 60);
            vfxAt(VFX_RUBBLES, base.getLocation(), "skill", 60);
            vfxAt(VFX_RUBBLES, base.getLocation(), "skill2", 60);
            // rastros de corte do pack ao abrir os braços
            vfxBoss(VFX_COMBO, "strike", 26);
            vfxBoss(VFX_COMBO, "slash_up2", 26);
            slashArc(3.6, Particle.FLAME, 14);
            magmaEruptVisual();
            shockwave(9.0);
            for (Player p : nearbyPlayers(8.5)) {
                boolean blocking = p.isBlocking();   // escudo levantado = NÃO trava
                p.damage(dmg(8, 12), base);
                if (!blocking) manager.freezePlayer(p, base, 140); // trava no lugar por 7s
            }
        }, 18L);
    }

    private void magmaEruptVisual() {
        final var magma = org.bukkit.Material.MAGMA_BLOCK.createBlockData();
        Location c = base.getLocation();
        base.getWorld().spawnParticle(Particle.LAVA, c.clone().add(0, 0.5, 0), 60, 1.5, 0.3, 1.5, 0);
        base.getWorld().spawnParticle(Particle.FLAME, c.clone().add(0, 0.5, 0), 80, 1.8, 0.4, 1.8, 0.06);
        for (int r = 1; r <= 8; r++) {
            final int rr = r;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!base.isValid()) return;
                Location cc = base.getLocation();
                int pts = Math.max(10, rr * 6);
                for (int i = 0; i < pts; i++) {
                    double a = 2 * Math.PI * i / pts;
                    Location pl = cc.clone().add(Math.cos(a) * rr, 0.2, Math.sin(a) * rr);
                    base.getWorld().spawnParticle(Particle.BLOCK, pl, 4, 0.2, 0.2, 0.2, 0, magma);
                    base.getWorld().spawnParticle(Particle.LAVA, pl, 1, 0.1, 0.1, 0.1, 0);
                    base.getWorld().spawnParticle(Particle.FLAME, pl, 2, 0.15, 0.3, 0.15, 0.02);
                }
            }, rr * 2L);
        }
    }

    private void dash(Player target) {
        busy = 18;
        playOnce("dash_front", 1.2, 0);
        sfx(SND_AIRDASH, Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1.1f);
        sfx(SND_RUPTUREQ, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 1.2f);
        face(target);
        Vector dir = target.getLocation().toVector().subtract(base.getLocation().toVector()).setY(0);
        if (dir.lengthSquared() < 0.001) dir = base.getLocation().getDirection().setY(0);
        base.setVelocity(dir.normalize().multiply(1.5).setY(0.12));
        final Set<UUID> hitSet = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (dead || !base.isValid()) return;
                base.getWorld().spawnParticle(Particle.FLAME, base.getLocation().add(0, 1, 0), 12, 0.4, 0.6, 0.4, 0.03);
                base.getWorld().spawnParticle(Particle.CRIT, base.getLocation().add(0, 1, 0), 10, 0.4, 0.6, 0.4, 0.1);
                for (Player p : nearbyPlayers(2.7)) {
                    if (hitSet.add(p.getUniqueId())) { p.damage(dmg(7, 10), base); knock(p, 0.8, 0.3); }
                }
            }, i * 2L);
        }
    }

    private void magmaWave(Player target) {
        busy = 26;
        face(target);
        playOnce("strike_of_fury_1", 1.0, 0);
        sfx(SND_SLASHULT, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 0.9f);
        vfxBoss(VFX_FURY, "sword_stomp", 18);
        final Vector dir = base.getLocation().getDirection().setY(0).normalize();   // já virado pro alvo (face), evita NaN
        final Location start = base.getLocation().clone();
        final var magma = org.bukkit.Material.MAGMA_BLOCK.createBlockData();
        final Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            double d = 1.5;
            @Override public void run() {
                if (dead || !base.isValid() || d > 15) { cancel(); return; }
                Location cpos = start.clone().add(dir.clone().multiply(d));
                cpos.setY(base.getLocation().getY() + 0.2);
                base.getWorld().spawnParticle(Particle.LAVA, cpos, 4, 0.4, 0.2, 0.4, 0);
                base.getWorld().spawnParticle(Particle.FLAME, cpos, 12, 0.4, 0.5, 0.4, 0.03);
                base.getWorld().spawnParticle(Particle.BLOCK, cpos, 8, 0.4, 0.3, 0.4, 0, magma);
                for (Player p : base.getWorld().getPlayers()) {
                    if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                    if (p.getLocation().distance(cpos) <= 2.2 && hit.add(p.getUniqueId())) {
                        p.damage(dmg(8, 11), base);
                        knock(p, 0.6, 0.5);
                    }
                }
                d += 1.0;
            }
        }.runTaskTimer(plugin, 2L, 1L);
    }

    // Barreira de Sangue: cura um pouco e reduz o dano recebido por 4s (fase 2).
    private void bloodboundBarrier() {
        busy = 40;
        playOnce("bloodbound_barrier", 1.0, 40);
        vfxBoss("bloodbound_barrier", "animation", 50);
        vfxBoss("bloodbound_barrier", "idle_bloodbound_barrier", 80);   // barreira fica ativa durante o escudo
        base.getWorld().playSound(base.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 1.2f, 0.7f);
        base.getWorld().playSound(base.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1f, 0.6f);
        this.hp = Math.min(MAX_HP, this.hp + 500);   // cura na vida virtual
        shielded = true;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> shielded = false, 80L);
        base.getWorld().spawnParticle(Particle.HEART, base.getLocation().add(0, 1.6, 0), 8, 0.5, 0.5, 0.5, 0);
        base.getWorld().spawnParticle(Particle.DUST, base.getLocation().add(0, 1, 0), 50, 0.9, 1.1, 0.9, dust(2.0f));
        base.getWorld().spawnParticle(Particle.SOUL, base.getLocation().add(0, 1, 0), 25, 0.7, 1.0, 0.7, 0.02);
    }

    /** Anti-travamento: pula pra cima (e um pouco em direção ao alvo) pra sair de obstáculo. */
    private void unstickJump(Player target) {
        Vector dir = target.getLocation().toVector().subtract(base.getLocation().toVector()).setY(0);
        if (dir.lengthSquared() < 0.001) dir = base.getLocation().getDirection().setY(0);
        base.setVelocity(dir.normalize().multiply(0.35).setY(0.6));
        playOnce("berserker_leap", 1.0, 10);
        sfx(SND_AIRDASH, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6f, 1.4f);
        base.getWorld().spawnParticle(Particle.CLOUD, base.getLocation(), 8, 0.3, 0.1, 0.3, 0.02);
    }

    // Reposicionamento lateral (usa dash_left / dash_right).
    private void strafe(Player target) {
        busy = 16;
        boolean left = ThreadLocalRandom.current().nextBoolean();
        playOnce(left ? "dash_left" : "dash_right", 1.2, 0);
        sfx(SND_MOVE, Sound.ENTITY_PHANTOM_FLAP, 0.8f, 1.2f);
        vfxBoss(VFX_COMBO, left ? "slash_left" : "slash_right", 14);
        face(target);
        Vector fwd = base.getLocation().getDirection().setY(0).normalize();
        Vector side = new Vector(-fwd.getZ(), 0, fwd.getX()).multiply(left ? 0.85 : -0.85);
        base.setVelocity(side.setY(0.12));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dead || !base.isValid()) return;
            meleeHit(4.2, dmg(5, 8), 0.4);
        }, 7L);
    }

    private void swing(String anim, String snd, Sound vanilla, float pitch, double reach, double damage, String vfxAnim, long delay) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dead || !base.isValid()) return;
            playOnce(anim, 1.0, 0);
            sfx(snd, vanilla, 1f, pitch);
            vfxBoss(VFX_COMBO, vfxAnim, 14);
            slashArc(reach, Particle.SWEEP_ATTACK, 6);
            meleeHit(reach, damage, 0.45);
        }, delay);
    }

    private void enterPhase2() {
        phase2 = true;
        busy = 24;
        playOnce("bulwark_instinct", 1.0, 24);
        setAttr(base, Attribute.MOVEMENT_SPEED, 0.32);
        sfx(SND_CHARGE, Sound.ENTITY_WITHER_AMBIENT, 1.3f, 0.7f);
        vfxAt(VFX_RUPTURE, base.getLocation(), "skill", 50);
        base.getWorld().spawnParticle(Particle.LAVA, base.getLocation().add(0, 1, 0), 50, 0.9, 1.2, 0.9, 0.1);
        base.getWorld().spawnParticle(Particle.FLAME, base.getLocation().add(0, 1, 0), 70, 1.0, 1.2, 1.0, 0.05);
        base.getWorld().spawnParticle(Particle.DUST, base.getLocation().add(0, 1, 0), 60, 1.0, 1.2, 1.0, dust(2.0f));
        shockwave(7.0);
        for (Player p : nearbyPlayers(BOSSBAR_RANGE)) {
            p.sendMessage(MM.deserialize("<#FF5959><bold>O Cavaleiro das Sombras entrou em FÚRIA!"));
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.3f);
        }
    }

    private void meleeHit(double reach, double dmg, double kb) {
        for (Player p : nearbyPlayers(reach)) {
            if (inFront(p, 75)) { p.damage(dmg, base); knock(p, kb, 0.25); }
        }
    }

    // ───────────────────────── VFX (modelos do pack) ─────────────────────────
    private void vfxBoss(String modelId, String anim, long life) {
        vfxAt(modelId, base.getLocation(), anim, life);
    }

    /** Spawna um modelo de VFX do pack numa posição, toca a animação e remove. */
    private void vfxAt(String modelId, Location loc, String anim, long life) {
        if (!ModelEngineHook.isAvailable()) return;
        Location at = loc.clone();
        at.setYaw(base.getLocation().getYaw());
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

    // ───────────────────────── VFX (partículas) ─────────────────────────
    private void ambientAura() {
        if (++auraTick % 4 != 0) return;
        Location feet = base.getLocation();
        base.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, feet.clone().add(0, 0.1, 0), 2, 0.35, 0.05, 0.35, 0.005);
        base.getWorld().spawnParticle(Particle.SMOKE, feet.clone().add(0, 0.1, 0), 2, 0.3, 0.05, 0.3, 0.01);
        base.getWorld().spawnParticle(Particle.DUST, feet.clone().add(0, 1.0, 0), 2, 0.35, 0.7, 0.35, dust(1.2f));
        if (phase2) base.getWorld().spawnParticle(Particle.FLAME, feet.clone().add(0, 0.1, 0), 2, 0.35, 0.05, 0.35, 0.01);
        if (auraTick % 72 == 0) {   // rugido grave ambiente (~3.6s)
            base.getWorld().playSound(base.getLocation(), Sound.ENTITY_RAVAGER_AMBIENT, 1.3f, 0.5f);
            base.getWorld().playSound(base.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.7f, 0.6f);
        }
    }

    private void spawnBurst() {
        base.getWorld().spawnParticle(Particle.SOUL, base.getLocation().add(0, 1, 0), 60, 0.6, 1.0, 0.6, 0.1);
        base.getWorld().spawnParticle(Particle.LAVA, base.getLocation().add(0, 1, 0), 30, 0.6, 1.0, 0.6, 0);
        shockwave(5.0);
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

    private void ring(double radius, Particle p, int points, double yOff, Particle.DustOptions data) {
        Location c = base.getLocation().add(0, yOff, 0);
        for (int i = 0; i < points; i++) {
            double t = 2 * Math.PI * i / points;
            base.getWorld().spawnParticle(p, c.clone().add(Math.cos(t) * radius, 0, Math.sin(t) * radius), 1, 0, 0, 0, 0, data);
        }
    }

    private void shockwave(double maxR) {
        for (int step = 0; step < 6; step++) {
            final double rr = 1.0 + step * (maxR - 1.0) / 5.0;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!base.isValid()) return;
                ring(rr, Particle.FLAME, (int) Math.max(8, rr * 7), 0.25);
                ring(rr, Particle.LAVA, (int) Math.max(6, rr * 3), 0.3);
                ring(rr, Particle.DUST, (int) Math.max(8, rr * 6), 0.25, dust(1.6f));
            }, step * 2L);
        }
    }

    private void groundCrack(double length) {
        Vector dir = base.getLocation().getDirection().setY(0).normalize();
        for (double d = 1; d <= length; d += 0.5) {
            Location l = base.getLocation().add(dir.clone().multiply(d)).add(0, 0.2, 0);
            base.getWorld().spawnParticle(Particle.LAVA, l, 1, 0.15, 0.05, 0.15, 0);
            base.getWorld().spawnParticle(Particle.FLAME, l, 3, 0.2, 0.1, 0.2, 0.01);
        }
    }

    // ───────────────────────── morte ─────────────────────────
    public void startDeathSequence() {
        if (dead) return;
        dead = true;
        if (controller != null) controller.cancel();
        base.setAI(false);
        base.setInvulnerable(true);
        playOnce("death", 1.0, 0);
        vfxAt(VFX_RUPTURE, base.getLocation(), "death", 60);   // variante de morte do terremoto
        sfx(SND_SLASHULT, Sound.ENTITY_WITHER_DEATH, 1.4f, 0.7f);   // morte épica (som custom)
        sfx(SND_RUPTURE, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
        base.getWorld().playSound(base.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.2f, 0.8f);
        base.getWorld().spawnParticle(Particle.LAVA, base.getLocation().add(0, 1, 0), 60, 0.8, 1.2, 0.8, 0.1);
        base.getWorld().spawnParticle(Particle.SOUL, base.getLocation().add(0, 1, 0), 50, 0.8, 1.2, 0.8, 0.05);
        base.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, base.getLocation().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            manager.onBossDefeated(this, damage, damagerNames);
            hardRemove();
        }, 60L);
    }

    public void remove() {
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

    private void updateBossBar() {
        float prog = (float) Math.max(0.0, Math.min(1.0, hp / MAX_HP));
        bossBar.progress(prog);
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
            t.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            t.setAlignment(TextDisplay.TextAlignment.CENTER);
            t.setBrightness(new Display.Brightness(15, 15));
            t.text(MM.deserialize(holoText()));
        });
    }

    /** Faz o holograma seguir a cabeça do boss em tempo real e atualizar a vida. */
    private void updateHolo() {
        if (holo == null || !holo.isValid()) return;
        holo.teleport(base.getLocation().add(0, HOLO_Y, 0));   // segue todo tick
        if (hp != lastHoloHp) {                                // re-renderiza o texto só quando a vida muda
            lastHoloHp = hp;
            holo.text(MM.deserialize(holoText()));
        }
    }

    /** ★ TEXTO DO HOLOGRAMA — edite aqui (cada "\n" é uma linha nova). ★ */
    private String holoText() {
        return NAME + "\n" + healthBar();
    }

    private String healthBar() {
        double pct = Math.max(0, Math.min(1, hp / MAX_HP));
        String cor = pct > 0.5 ? "#10fc46" : pct > 0.25 ? "#fcc850" : "#e22c27";
        return "<" + cor + ">" + String.format(java.util.Locale.US, "%,.0f", hp) + " ❤";   // só a vida, sem barra
    }

    // ───────────────────────── util ─────────────────────────
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

    /** Toca o som CUSTOM do pack (via API Adventure — confiável p/ sons de resourcepack) + fallback vanilla. */
    private void sfx(String customKey, Sound vanilla, float vol, float pitch) {
        base.getWorld().playSound(
                net.kyori.adventure.sound.Sound.sound(net.kyori.adventure.key.Key.key(customKey),
                        net.kyori.adventure.sound.Sound.Source.MASTER, vol, pitch),
                base.getX(), base.getY(), base.getZ());
        base.getWorld().playSound(base.getLocation(), vanilla, vol, pitch);   // fallback vanilla
    }

    private double dmg(double min, double max) {
        double d = (min + ThreadLocalRandom.current().nextDouble() * (max - min)) * DMG_MULT;
        if (buffTicks > 0) d *= BUFF_DMG_DEALT;   // buffado pós-descanso = mais dano
        return d;
    }

    private void knock(Player p, double horizontal, double vertical) {
        Vector kb = p.getLocation().toVector().subtract(base.getLocation().toVector()).setY(0);
        if (kb.lengthSquared() < 0.001) kb = base.getLocation().getDirection().setY(0);
        // Suavizado: menos arremesso. Horizontal reduzido e vertical limitado
        // (evita lançar o jogador alto demais e o kick de "flying").
        kb.normalize().multiply(horizontal * 0.7).setY(Math.min(vertical, 0.33));
        p.setVelocity(p.getVelocity().add(kb));
    }

    private boolean inFront(Player p, double maxAngleDeg) {
        Vector facing = base.getLocation().getDirection().setY(0).normalize();
        Vector to = p.getLocation().toVector().subtract(base.getLocation().toVector()).setY(0);
        return to.lengthSquared() < 0.001 || facing.angle(to) <= Math.toRadians(maxAngleDeg);
    }

    private void face(LivingEntity t) {
        if (t == null) return;
        Vector dir = t.getLocation().toVector().subtract(base.getLocation().toVector());
        if (dir.getX() == 0 && dir.getZ() == 0) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
        base.setRotation(yaw, 0f);   // gira no lugar (sem teleport -> sem tranco/jitter)
    }

    private java.util.List<Player> nearbyPlayers(double r) {
        java.util.List<Player> out = new java.util.ArrayList<>();
        for (Player p : base.getWorld().getPlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            if (p.getLocation().distance(base.getLocation()) <= r) out.add(p);
        }
        return out;
    }

    private static Vector rotY(Vector v, double rad) {
        double cos = Math.cos(rad), sin = Math.sin(rad);
        return new Vector(v.getX() * cos - v.getZ() * sin, v.getY(), v.getX() * sin + v.getZ() * cos);
    }

    private static Particle.DustOptions dust(float size) {
        return new Particle.DustOptions(SHADOW_RED, size);
    }

    private static void setAttr(LivingEntity e, Attribute a, double v) {
        var inst = e.getAttribute(a);
        if (inst != null) inst.setBaseValue(v);
    }

    /** Mantém a vida real do mob no máximo (a vida que conta é a virtual {@link #hp}). */
    private void pinHealth() {
        var mh = base.getAttribute(Attribute.MAX_HEALTH);
        double m = (mh != null) ? mh.getValue() : 20.0;
        if (base.getHealth() < m) base.setHealth(m);
    }
}
