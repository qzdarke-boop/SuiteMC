package com.psdk.thepit;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fonte ÚNICA e confiável do estado de Combat Log do Skill Pit.
 *
 * <p><b>Regra oficial (inalterada):</b> ao receber/causar dano VÁLIDO entre jogadores, a
 * sessão de combate expira {@link #COMBAT_DURATION_MS} ms depois. Enquanto a sessão estiver
 * ATIVA (timestamp de expiração ainda no futuro), desconectar mata o jogador
 * (drop de itens). Depois de expirada — esteja o jogador na arena ou na área segura — sair
 * NÃO gera nenhuma punição.
 *
 * <p><b>Decisão baseada em timestamp, nunca em presença em mapa.</b> Todas as consultas
 * ({@link #isInActiveCombat}, {@link #shouldKillOnQuit}, {@link #remainingMs}) usam o mesmo
 * {@link Session#expiresAt} comparado ao horário atual. A UI (ActionBar) lê exatamente o
 * mesmo valor: o jogador só é informado de que "saiu de combate" quando ele realmente não
 * pode mais ser punido (não há contador visual dessincronizado do contador lógico).
 *
 * <p><b>Sem estado residual.</b> {@link #endCombat} é idempotente e remove TODA referência
 * da sessão. Não há dados de combate persistidos (DB/PDC/metadata), logo reinício/reload
 * nunca deixam um jogador marcado como "em combate". A task visual é SOMENTE-LEITURA: nunca
 * altera a fonte da verdade (isso evita o antigo bug de a exibição apagar/segurar estado).
 *
 * <p><b>Sem tarefas atrasadas por jogador.</b> A expiração é decidida por comparação de
 * timestamp na hora da consulta (não por uma task de encerramento agendada). Assim, uma
 * renovação de combate nunca pode ser encerrada por uma "task antiga" de uma sessão anterior.
 */
public class CombatManager {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    /** Duração da sessão de combate. */
    public static final long COMBAT_DURATION_MS       = 15_000L;
    /** Janela de kill-no-quit — UNIFICADA com a duração: quit pune sse, e somente se, a
     *  sessão ainda estiver ativa no instante exato da saída. */
    public static final long COMBAT_QUIT_KILL_MS      = COMBAT_DURATION_MS;
    /** Por quanto tempo a mensagem "Você saiu de combate!" fica visível (só exibição). */
    public static final long FREE_DISPLAY_DURATION_MS = 3_000L;

    /** Liga logs de diagnóstico do Combat Log no console (nunca no chat). Deixe {@code false}
     *  em produção; ligue temporariamente para confirmar decisões de punição por quit. */
    public static final boolean DEBUG = false;

    /** Motivo do encerramento de uma sessão (para diagnóstico e clareza). */
    public enum CombatEndReason { EXPIRED, DEATH, QUIT, KILL_ON_QUIT, MANUAL }

    /** Estado de uma sessão de combate. {@code expiresAt}/{@code lastRefreshAt} são voláteis
     *  porque a task periódica (main thread) e chamadas de dano leem/escrevem. */
    private static final class Session {
        final long startedAt;
        volatile long expiresAt;
        volatile long lastRefreshAt;
        Session(long now, long expiresAt) {
            this.startedAt = now;
            this.expiresAt = expiresAt;
            this.lastRefreshAt = now;
        }
    }

    private final PSDK plugin;
    /** Fonte única da verdade: UUID → sessão ativa. */
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    /** SOMENTE exibição: até quando mostrar "saiu de combate". Não afeta punição. */
    private final Map<UUID, Long> freeDisplayUntil = new ConcurrentHashMap<>();

    public CombatManager(PSDK plugin) {
        this.plugin = plugin;
        startActionBarTask();
    }

    // ─────────────────────────── API CENTRAL ───────────────────────────────

    /**
     * Inicia ou renova o combate de atacante e vítima. Único ponto que estende a sessão.
     * Deve ser chamado apenas por interação de combate VÁLIDA entre jogadores (dano real).
     */
    public void startOrRefreshCombat(Player attacker, Player victim) {
        long now = System.currentTimeMillis();
        long expiresAt = now + COMBAT_DURATION_MS;
        if (victim != null)   refreshOne(victim.getUniqueId(), now, expiresAt);
        if (attacker != null) refreshOne(attacker.getUniqueId(), now, expiresAt);
    }

    private void refreshOne(UUID id, long now, long expiresAt) {
        sessions.compute(id, (k, s) -> {
            if (s == null) {
                if (DEBUG) log(id, "START (expira em " + COMBAT_DURATION_MS + "ms)");
                return new Session(now, expiresAt);
            }
            s.expiresAt = expiresAt;
            s.lastRefreshAt = now;
            if (DEBUG) log(id, "REFRESH (nova expiração em " + COMBAT_DURATION_MS + "ms)");
            return s;
        });
    }

    /**
     * ÚNICA verificação autoritativa de "está em combate agora?". Baseada no timestamp de
     * expiração real; se a sessão já expirou, encerra-a (auto-limpeza) e retorna {@code false}.
     * Assim, presença no mapa passa a significar sempre "sessão viva".
     */
    public boolean isInActiveCombat(UUID id) {
        if (id == null) return false;
        Session s = sessions.get(id);
        if (s == null) return false;
        if (System.currentTimeMillis() >= s.expiresAt) {
            endCombat(id, CombatEndReason.EXPIRED);
            return false;
        }
        return true;
    }

    public boolean isInActiveCombat(Player p) {
        return p != null && isInActiveCombat(p.getUniqueId());
    }

    /**
     * Encerramento central e IDEMPOTENTE: remove toda referência da sessão. Encerrar uma
     * sessão já encerrada não causa erro nem efeitos colaterais. Não mexe na exibição de
     * "saiu de combate" (essa é tratada pela task visual, independente da punição).
     */
    public void endCombat(UUID id, CombatEndReason reason) {
        if (id == null) return;
        Session removed = sessions.remove(id);
        if (DEBUG && removed != null) {
            log(id, "END (" + reason + ")");
        }
    }

    // ─────────────────────── COMPATIBILIDADE (API antiga) ──────────────────

    /** @deprecated use {@link #startOrRefreshCombat(Player, Player)}. Mantido p/ chamadores. */
    public void registerHit(Player victim, Player attacker) {
        startOrRefreshCombat(attacker, victim);
    }

    public boolean isInCombat(UUID uuid) { return isInActiveCombat(uuid); }
    public boolean isInCombat(Player p)  { return isInActiveCombat(p); }

    /**
     * Decide, no INSTANTE EXATO da consulta, se sair agora deve punir. É idêntico a
     * {@link #isInActiveCombat}: a punição só ocorre se a sessão ainda estiver viva agora
     * (nunca com base num resultado em cache de ticks atrás).
     */
    public boolean shouldKillOnQuit(Player p) {
        return p != null && isInActiveCombat(p.getUniqueId());
    }

    public long remainingMs(UUID uuid) {
        Session s = sessions.get(uuid);
        if (s == null) return 0;
        return Math.max(0, s.expiresAt - System.currentTimeMillis());
    }

    /** Limpeza total do jogador (morte/quit): encerra a sessão e a exibição. Idempotente. */
    public void clear(UUID uuid) {
        endCombat(uuid, CombatEndReason.MANUAL);
        freeDisplayUntil.remove(uuid);
    }

    // ─────────────────────────── DIAGNÓSTICO ───────────────────────────────

    public boolean isDebug() { return DEBUG; }

    /** Snapshot legível da sessão (para logs de quit). Nunca vai ao chat público. */
    public String describeSession(UUID id) {
        Session s = sessions.get(id);
        if (s == null) return "sem sessão (fora de combate)";
        long now = System.currentTimeMillis();
        return "iniciada=" + s.startedAt
                + ", ultimaRenovacao=" + s.lastRefreshAt
                + ", expiraEm=" + s.expiresAt
                + ", restanteMs=" + Math.max(0, s.expiresAt - now)
                + ", ativa=" + (now < s.expiresAt);
    }

    private void log(UUID id, String msg) {
        Player p = Bukkit.getPlayer(id);
        String name = p != null ? p.getName() : id.toString();
        plugin.getLogger().info("[CombatLog] " + name + " (" + id + ") " + msg);
    }

    // ─────────────────────────── EXIBIÇÃO (READ-ONLY) ──────────────────────

    /**
     * Task de ActionBar: SOMENTE lê o estado e mostra o contador. Nunca altera a fonte da
     * verdade. Enquanto a sessão está ativa mostra o tempo restante (arredondado p/ cima) e
     * mantém a janela de "saiu de combate"; ao expirar, exibe "saiu de combate" por
     * {@link #FREE_DISPLAY_DURATION_MS}. Como o gatilho de "saiu" é o MESMO expiresAt usado
     * pela punição, o jogador só vê "saiu" quando já não pode mais ser punido.
     */
    private void startActionBarTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    UUID id = p.getUniqueId();
                    long remaining = remainingMs(id);

                    if (remaining > 0) {
                        long seg = (long) Math.ceil(remaining / 1000.0);
                        p.sendActionBar(mm.deserialize(
                                "<#e22c27>Você está em combate! Aguarde <bold>" + seg + "</bold>s"));
                        freeDisplayUntil.put(id, now + FREE_DISPLAY_DURATION_MS);
                    } else {
                        // Garante a auto-limpeza de sessões expiradas mesmo sem outra consulta.
                        if (sessions.containsKey(id)) isInActiveCombat(id);
                        Long until = freeDisplayUntil.get(id);
                        if (until != null) {
                            if (now <= until) {
                                p.sendActionBar(mm.deserialize("<#10fc46>Você saiu de combate!"));
                            } else {
                                freeDisplayUntil.remove(id);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }
}
