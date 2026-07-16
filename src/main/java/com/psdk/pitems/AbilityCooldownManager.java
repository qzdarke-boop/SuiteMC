package com.psdk.pitems;

import com.psdk.PSDK;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Serviço central de cooldowns de habilidades/itens especiais do Skill Pit.
 *
 * <p><b>Padrão visual do Escudo:</b> a recarga é mostrada no próprio item da hotbar
 * via {@link Player#setCooldown(Material, int)} — exatamente como a TNT da loja e o
 * escudo vanilla. Sem ActionBar, BossBar, títulos ou contadores paralelos.
 *
 * <p><b>Por habilidade, não por material:</b> cada habilidade tem um ID próprio
 * ({@link Ability}). O controle autoritativo é feito por (jogador, abilityId), então
 * usar a TNT nunca libera/bloqueia a Ender Pearl, e duas unidades do mesmo item
 * compartilham o mesmo cooldown. O {@code setCooldown(Material,...)} é apenas o
 * indicador visual (as habilidades atuais usam materiais distintos entre si).
 *
 * <p><b>Individual e à prova de burla:</b> o cooldown é por jogador e cobre qualquer
 * unidade em qualquer slot/mão/stack. É persistido no banco para que relogar (ou um
 * reload/restart) não zere o tempo restante.
 *
 * <p><b>Limpeza:</b> entradas expiram de forma preguiçosa (sem tasks presas). Na saída
 * do jogador o estado ativo é gravado e removido da memória; no join é recarregado e o
 * visual reaplicado com o tempo restante.
 */
public class AbilityCooldownManager {

    /**
     * Catálogo de habilidades com cooldown. O tempo padrão fica centralizado aqui
     * (item 12 do pedido). Ajuste os valores neste enum.
     */
    public enum Ability {
        ENDER_PEARL("ender_pearl", Material.ENDER_PEARL, 6_000L),
        TROCA_POSICAO("troca_posicao", Material.EGG, 5_000L),
        JAULA("jaula", Material.RED_STAINED_GLASS, 300_000L),
        CADEIA("cadeia", Material.SNOWBALL, 7_000L),
        SAFE_TNT("safe_tnt", Material.TNT, 4_000L);

        private final String id;
        private final Material material;
        private final long defaultMs;

        Ability(String id, Material material, long defaultMs) {
            this.id = id;
            this.material = material;
            this.defaultMs = defaultMs;
        }

        public String getId()          { return id; }
        public Material getMaterial()  { return material; }
        public long getDefaultMs()     { return defaultMs; }

        public static Ability byId(String id) {
            for (Ability a : values()) if (a.id.equals(id)) return a;
            return null;
        }
    }

    private final PSDK plugin;
    // uuid -> (abilityId -> expireAt epoch ms)
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    public AbilityCooldownManager(PSDK plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────────────── API ──────────────────────────────────────

    /**
     * True se a habilidade está pronta (sem cooldown ativo) para o jogador.
     *
     * <p><b>Bypass de OP centralizado:</b> operadores nunca ficam em cooldown de
     * habilidade (testes consecutivos). Todas as demais validações (região, arena,
     * área segura, estrutura) continuam sendo feitas normalmente pelos listeners.
     */
    public boolean isReady(Player player, Ability ability) {
        if (player.isOp()) return true;
        return remainingMs(player.getUniqueId(), ability) <= 0;
    }

    /** Tempo restante (ms) do cooldown; 0 se pronto. Limpa entradas expiradas. */
    public long remainingMs(UUID id, Ability ability) {
        Map<String, Long> m = cooldowns.get(id);
        if (m == null) return 0;
        Long exp = m.get(ability.getId());
        if (exp == null) return 0;
        long rem = exp - System.currentTimeMillis();
        if (rem <= 0) {
            m.remove(ability.getId());
            if (m.isEmpty()) cooldowns.remove(id);
            return 0;
        }
        return rem;
    }

    /** Inicia o cooldown com a duração padrão da habilidade. */
    public void start(Player player, Ability ability) {
        start(player, ability, ability.getDefaultMs());
    }

    /** Inicia o cooldown com uma duração específica (ms) e aplica o visual na hotbar. */
    public void start(Player player, Ability ability, long durationMs) {
        // OP não inicia cooldown (nem o indicador visual): pode usar em sequência.
        if (player.isOp()) return;
        if (durationMs <= 0) return;
        long expireAt = System.currentTimeMillis() + durationMs;
        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(ability.getId(), expireAt);
        applyVisual(player, ability, durationMs);
    }

    /** Cancela o cooldown (ex.: uso revalidado como inválido no impacto). */
    public void clear(Player player, Ability ability) {
        Map<String, Long> m = cooldowns.get(player.getUniqueId());
        if (m != null) {
            m.remove(ability.getId());
            if (m.isEmpty()) cooldowns.remove(player.getUniqueId());
        }
        if (player.isOnline()) player.setCooldown(ability.getMaterial(), 0);
    }

    private void applyVisual(Player player, Ability ability, long durationMs) {
        if (!player.isOnline()) return;
        int ticks = (int) Math.max(0, durationMs / 50L);
        player.setCooldown(ability.getMaterial(), ticks);

        // Reaplica no PRÓXIMO tick para SOBRESCREVER cooldowns vanilla que o servidor
        // aplica DEPOIS do evento de lançamento. Ex.: a Ender Pearl seta ~1s (20 ticks)
        // de cooldown vanilla logo após o arremesso — isso engolia o nosso setCooldown
        // imediato e só aparecia o cooldown "padrão" curto em vez dos 6s. Itens sem
        // cooldown vanilla (TNT/Ovo/Jaula) não são afetados por essa reaplicação.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) player.setCooldown(ability.getMaterial(), ticks);
        });
    }

    // ─────────────────────── Ciclo de vida / persistência ─────────────────────

    /** No join: recarrega cooldowns persistidos e reaplica o visual do tempo restante. */
    public void handleJoin(Player player) {
        UUID id = player.getUniqueId();
        Map<String, Long> loaded = loadPersist(id);
        deletePersist(id);
        if (loaded.isEmpty()) return;

        long now = System.currentTimeMillis();
        Map<String, Long> live = new ConcurrentHashMap<>();
        for (Map.Entry<String, Long> e : loaded.entrySet()) {
            if (e.getValue() <= now) continue;
            Ability a = Ability.byId(e.getKey());
            if (a == null) continue;
            live.put(e.getKey(), e.getValue());
            applyVisual(player, a, e.getValue() - now);
        }
        if (!live.isEmpty()) cooldowns.put(id, live);
    }

    /** Na saída: grava os cooldowns ativos e libera a memória do jogador. */
    public void handleQuit(Player player) {
        UUID id = player.getUniqueId();
        Map<String, Long> m = cooldowns.remove(id);
        if (m == null || m.isEmpty()) { deletePersist(id); return; }
        long now = System.currentTimeMillis();
        Map<String, Long> active = new HashMap<>();
        for (Map.Entry<String, Long> e : m.entrySet()) {
            if (e.getValue() > now) active.put(e.getKey(), e.getValue());
        }
        if (active.isEmpty()) deletePersist(id);
        else persist(id, active);
    }

    /** No desligamento/reload: persiste todos os cooldowns ativos e limpa a memória. */
    public void shutdown() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<String, Long>> entry : cooldowns.entrySet()) {
            Map<String, Long> active = new HashMap<>();
            for (Map.Entry<String, Long> e : entry.getValue().entrySet()) {
                if (e.getValue() > now) active.put(e.getKey(), e.getValue());
            }
            if (!active.isEmpty()) persist(entry.getKey(), active);
        }
        cooldowns.clear();
    }

    // ─────────────────────────────── DB helpers ───────────────────────────────

    private Map<String, Long> loadPersist(UUID id) {
        Map<String, Long> out = new HashMap<>();
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT ability, expire_at FROM ability_cooldowns WHERE uuid = ?")) {
                ps.setString(1, id.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    out.put(rs.getString("ability"), rs.getLong("expire_at"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao carregar cooldowns de " + id, e);
        }
        return out;
    }

    private void persist(UUID id, Map<String, Long> active) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            try (PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM ability_cooldowns WHERE uuid = ?")) {
                del.setString(1, id.toString());
                del.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO ability_cooldowns (uuid, ability, expire_at) VALUES (?,?,?)")) {
                for (Map.Entry<String, Long> e : active.entrySet()) {
                    ps.setString(1, id.toString());
                    ps.setString(2, e.getKey());
                    ps.setLong(3, e.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao persistir cooldowns de " + id, e);
        }
    }

    private void deletePersist(UUID id) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM ability_cooldowns WHERE uuid = ?")) {
                ps.setString(1, id.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao limpar cooldowns de " + id, e);
        }
    }
}
