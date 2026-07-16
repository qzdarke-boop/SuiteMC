package com.psdk.economy;

import com.psdk.PSDK;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class EconomyManager {

    public record TopEntry(String name, double value) {}

    private enum Currency {
        TOKENS("tokens", 0), COINS("coins", 1);
        final String col; final int idx;
        Currency(String col, int idx) { this.col = col; this.idx = idx; }
    }

    private final PSDK plugin;
    private volatile List<TopEntry> topTokensCache = List.of();
    private volatile List<TopEntry> topCoinsCache = List.of();
    private volatile List<TopEntry> topKillsCache = List.of();
    private volatile List<TopEntry> topDeathsCache = List.of();

    /**
     * Cache de saldo em memória [tokens, coins, coins_earned] — evita SELECT no banco a
     * cada leitura. {@code coins_earned} é o total CONQUISTADO por gameplay (base do Top
     * Coins); só sobe via {@link #addCoins} (fontes reais), nunca por /pay ou /eco.
     */
    private final Map<UUID, double[]> balances = new ConcurrentHashMap<>();
    private final Set<UUID> balanceWarming = ConcurrentHashMap.newKeySet();

    /** Índice do total conquistado no array de saldo (tokens=0, coins=1, coins_earned=2). */
    private static final int EARNED_IDX = 2;

    /**
     * Write-behind: o cache acima é a FONTE DA VERDADE em runtime. As operações
     * (add/remove/set) atualizam o cache na hora e marcam o UUID como "sujo"; um flush
     * assíncrono persiste em lote — tirando 100% das escritas de economia da main thread
     * (antes era 1 UPDATE síncrono por bloco minerado, o que derrubava TPS no Pit).
     */
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> nameCache = new ConcurrentHashMap<>();
    private final Object flushLock = new Object();

    private static final String FLUSH_SQL =
            "INSERT INTO player_economy (uuid, name, tokens, coins, coins_earned) VALUES (?, ?, ?, ?, ?) " +
            "ON CONFLICT(uuid) DO UPDATE SET tokens = excluded.tokens, coins = excluded.coins, " +
            "coins_earned = excluded.coins_earned, name = COALESCE(excluded.name, player_economy.name)";

    public EconomyManager(PSDK plugin) {
        this.plugin = plugin;
        migrateEarnedFromBalance();
        startTopCacheTask();
        startFlushTask();
    }

    /**
     * Migração única: semeia {@code coins_earned} com o saldo atual (uma vez), para que o
     * Top Coins não fique vazio logo após a atualização. A partir daí só a gameplay soma.
     * O saldo NÃO é alterado. Guardada por flag em {@code settings} (roda só uma vez).
     */
    private void migrateEarnedFromBalance() {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            String flag = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT value FROM settings WHERE key = 'coins_earned_migrated'");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) flag = rs.getString("value");
            }
            if ("1".equals(flag)) return;
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("UPDATE player_economy SET coins_earned = coins WHERE coins_earned = 0 AND coins > 0");
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO settings (key, value) VALUES ('coins_earned_migrated', '1') " +
                    "ON CONFLICT(key) DO UPDATE SET value = '1'")) {
                ps.executeUpdate();
            }
            plugin.getLogger().info("[Economia] coins_earned inicializado a partir do saldo atual (migração única).");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro na migração de coins_earned", e);
        }
    }

    private void startFlushTask() {
        new BukkitRunnable() {
            @Override public void run() { flushDirty(); }
        }.runTaskTimerAsynchronously(plugin, 60L, 60L); // a cada 3s
    }

    private void startTopCacheTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                refreshTopCaches();
            }
        }.runTaskTimerAsynchronously(plugin, 100L, 6000L);
    }

    private void refreshTopCaches() {
        // Executa em thread assíncrona: usa uma conexão própria para NÃO compartilhar
        // a conexão da thread principal (evita corrupção de leitura/escrita).
        try (Connection conn = plugin.getDatabaseManager().newConnection()) {
            topTokensCache = queryTop(conn, "SELECT name, tokens AS value FROM player_economy ORDER BY tokens DESC LIMIT 10");
            // Top Coins = coins CONQUISTADOS por gameplay (não o saldo, que /pay e /eco movem).
            topCoinsCache = queryTop(conn, "SELECT name, coins_earned AS value FROM player_economy ORDER BY coins_earned DESC LIMIT 10");
            topKillsCache = queryTop(conn, "SELECT name, kills AS value FROM player_data ORDER BY kills DESC LIMIT 10");
            topDeathsCache = queryTop(conn, "SELECT name, deaths AS value FROM player_data ORDER BY deaths DESC LIMIT 10");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao atualizar caches de top", e);
        }
    }

    private List<TopEntry> queryTop(Connection conn, String sql) {
        List<TopEntry> list = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new TopEntry(rs.getString("name"), rs.getDouble("value")));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao consultar top", e);
        }
        return list;
    }

    // ==================== TOKENS (moeda paga) ====================

    public double getTokens(UUID uuid) {
        return getCurrency(uuid, Currency.TOKENS);
    }

    public void setTokens(UUID uuid, String name, double amount) {
        setCurrency(uuid, name, Currency.TOKENS, amount);
    }

    public void addTokens(UUID uuid, String name, double amount) {
        addCurrency(uuid, name, Currency.TOKENS, amount, false);
    }

    public boolean removeTokens(UUID uuid, double amount) {
        return removeCurrency(uuid, Currency.TOKENS, amount);
    }

    public boolean hasTokens(UUID uuid, double amount) {
        return getTokens(uuid) >= amount;
    }

    // ==================== COINS (moeda in-game) ====================

    public double getCoins(UUID uuid) {
        return getCurrency(uuid, Currency.COINS);
    }

    public void setCoins(UUID uuid, String name, double amount) {
        setCurrency(uuid, name, Currency.COINS, amount);
    }

    /**
     * Adiciona coins por uma fonte de GAMEPLAY (mineração, kills, Boss, eventos...).
     * Soma ao saldo E ao total conquistado (Top Coins) e às estatísticas de período.
     */
    public void addCoins(UUID uuid, String name, double amount) {
        addCurrency(uuid, name, Currency.COINS, amount, true);
    }

    /**
     * Adiciona coins SEM contar para o Top Coins nem para os stats de período. Use para
     * transferências e ajustes administrativos (/pay recebido, /eco give) — apenas o saldo
     * muda. Isso impede a falsificação do ranking movimentando coins já existentes.
     */
    public void addCoinsNoStat(UUID uuid, String name, double amount) {
        addCurrency(uuid, name, Currency.COINS, amount, false);
    }

    public boolean removeCoins(UUID uuid, double amount) {
        return removeCurrency(uuid, Currency.COINS, amount);
    }

    public boolean hasCoins(UUID uuid, double amount) {
        return getCoins(uuid) >= amount;
    }

    /** Total de coins conquistados por gameplay (base do Top Coins). */
    public double getCoinsEarned(UUID uuid) {
        double[] b = balances.get(uuid);
        if (b == null) {
            if (!Bukkit.isPrimaryThread()) { warmBalance(uuid); return 0; }
            b = loadFromDb(uuid);
            balances.put(uuid, b);
        }
        return b[EARNED_IDX];
    }

    // ==================== Generic helpers ====================

    /** Carrega [tokens, coins, coins_earned] do banco (1 query). Só no primeiro acesso de cada jogador. */
    private double[] loadFromDb(UUID uuid) {
        double[] b = {0, 0, 0};
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection()
                .prepareStatement("SELECT tokens, coins, coins_earned FROM player_economy WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    b[0] = rs.getDouble("tokens");
                    b[1] = rs.getDouble("coins");
                    b[EARNED_IDX] = rs.getDouble("coins_earned");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao carregar saldo", e);
        }
        return b;
    }

    private double getCurrency(UUID uuid, Currency currency) {
        double[] b = balances.get(uuid);
        if (b == null) {
            // NUNCA tocar no banco fora da thread principal (placeholders rodam async):
            // a conexão SQLite é compartilhada e dá "stmt pointer is closed".
            if (!Bukkit.isPrimaryThread()) {
                warmBalance(uuid);
                return 0;
            }
            b = loadFromDb(uuid);
            balances.put(uuid, b);   // 1 query e nunca mais
        }
        return b[currency.idx];
    }

    /** Carrega o saldo do jogador para o cache na thread principal (dispara 1x). */
    private void warmBalance(UUID uuid) {
        if (!balanceWarming.add(uuid)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                balances.computeIfAbsent(uuid, this::loadFromDb);
            } finally {
                balanceWarming.remove(uuid);
            }
        });
    }

    private void setCurrency(UUID uuid, String name, Currency currency, double amount) {
        if (!Double.isFinite(amount)) return;            // rejeita NaN/Infinity (corromperiam o saldo)
        double value = Math.max(0, amount);
        double[] b = balances.computeIfAbsent(uuid, k -> loadFromDb(uuid));
        b[currency.idx] = value;
        if (name != null) nameCache.put(uuid, name);
        markDirty(uuid);
    }

    /**
     * @param countEarned quando {@code true} (e a moeda é COINS), soma também ao total
     *        conquistado (Top Coins) e às estatísticas de período. Transferências/ajustes
     *        (/pay, /eco) passam {@code false}: mexem só no saldo, nunca no ranking.
     */
    private void addCurrency(UUID uuid, String name, Currency currency, double amount, boolean countEarned) {
        if (!Double.isFinite(amount) || amount <= 0) return;  // só adiciona valores finitos e positivos
        double[] b = balances.computeIfAbsent(uuid, k -> loadFromDb(uuid));
        b[currency.idx] += amount;
        if (name != null) nameCache.put(uuid, name);
        if (currency == Currency.COINS && countEarned) {
            b[EARNED_IDX] += amount;                          // total conquistado (Top Coins)
            plugin.getTopStatsTracker().addCoins(uuid, name, amount);
        }
        markDirty(uuid);
    }

    private boolean removeCurrency(UUID uuid, Currency currency, double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return false;  // valor inválido nunca debita
        // Tudo roda na thread principal (single-thread), então checar+debitar no cache
        // é atômico (sem corrida/dupe). A persistência é feita pelo flush assíncrono.
        double[] b = balances.computeIfAbsent(uuid, k -> loadFromDb(uuid));
        if (b[currency.idx] < amount) return false;     // saldo insuficiente
        b[currency.idx] -= amount;
        markDirty(uuid);
        return true;
    }

    private void markDirty(UUID uuid) {
        dirty.add(uuid);
    }

    /**
     * Persiste no banco todos os saldos "sujos" (write-behind). Lê o valor atual do cache
     * (fonte da verdade) no momento da escrita. Roda em thread async (conexão dedicada);
     * também é chamado de forma síncrona no desligamento.
     */
    public void flushDirty() {
        if (dirty.isEmpty()) return;
        synchronized (flushLock) {
            List<UUID> batch = new ArrayList<>(dirty);
            // Remove ANTES de gravar: uma alteração que chegue durante o flush re-marca o
            // UUID e é capturada no próximo ciclo (não se perde).
            dirty.removeAll(batch);
            try (Connection conn = plugin.getDatabaseManager().newConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(FLUSH_SQL)) {
                    for (UUID id : batch) {
                        double[] b = balances.get(id);
                        if (b == null) continue;
                        ps.setString(1, id.toString());
                        ps.setString(2, nameCache.get(id)); // null -> COALESCE preserva o nome atual
                        ps.setDouble(3, b[0]);
                        ps.setDouble(4, b[1]);
                        ps.setDouble(5, b.length > EARNED_IDX ? b[EARNED_IDX] : 0);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                conn.commit();
            } catch (SQLException e) {
                dirty.addAll(batch); // falhou: tenta de novo no próximo ciclo
                plugin.getLogger().log(Level.WARNING, "Erro ao gravar economia (write-behind)", e);
            }
        }
    }

    /** Flush síncrono final — chamar no onDisable antes de fechar o banco. */
    public void flushAll() { flushDirty(); }

    /**
     * Limpa o cache de saldos E a fila de gravação pendente. DEVE ser chamado ANTES de
     * mexer no banco POR FORA (ex.: /thepit reset/resetall): com a fila zerada e o cache
     * limpo, nenhum saldo antigo é regravado por cima do wipe pelo flush assíncrono.
     */
    public void clearCache() {
        synchronized (flushLock) {
            dirty.clear();
            balances.clear();
            nameCache.clear();
        }
    }

    public void ensureAccount(UUID uuid, String name) {
        if (name != null) nameCache.put(uuid, name);
        String sql = """
                INSERT INTO player_economy (uuid, name, tokens, coins, coins_earned) VALUES (?, ?, 0, 0, 0)
                ON CONFLICT(uuid) DO UPDATE SET name = excluded.name""";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao criar conta de economia", e);
        }
    }

    public List<TopEntry> getTopTokens() { return topTokensCache; }
    public List<TopEntry> getTopCoins() { return topCoinsCache; }
    public List<TopEntry> getTopKills() { return topKillsCache; }
    public List<TopEntry> getTopDeaths() { return topDeathsCache; }
}
