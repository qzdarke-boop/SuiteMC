package com.psdk.boss;

import org.bukkit.entity.Entity;

import java.util.Map;
import java.util.UUID;

/** Contrato comum dos bosses (ShadowKnight, PumpkinKing, ...) usado pelo BossManager. */
public interface BossEntity {
    boolean isDead();
    void remove();
    double getHp();
    /** Vida máxima (pool virtual) do boss — usada para calcular percentuais. */
    double getMaxHp();
    Entity getBase();
    boolean isShielded();
    void recordDamage(UUID uuid, String name, double amount);
    void damageBoss(double amount);
    void startDeathSequence();
    void onHurt();
    String getNamePlain();

    /** Dano acumulado por jogador (usado para premiar no kill e no timeout). */
    Map<UUID, Double> getDamageMap();
    /** Nome conhecido de cada jogador que causou dano. */
    Map<UUID, String> getDamagerNames();
}
