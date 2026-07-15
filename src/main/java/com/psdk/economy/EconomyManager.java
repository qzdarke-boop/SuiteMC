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

    /** Cache de saldo em memória [tokens, coins] — evita SELECT no banco a cada leitura. */
    private final Map<UUID, double[]> balances = new ConcurrentHashMap<>();
    private final Set<UUID> balanceWarming = ConcurrentHashMap.newKeySet();

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
            "INSERT INTO player_economy (uuid, name, tokens, coins) VALUES (?, ?, ?, ?) " +
            "ON CONFLICT(uuid) DO UPDATE SET tokens = excluded.tokens, coins = excluded.coins, " +
            "name = COALESCE(excluded.name, player_economy.name)";

    public EconomyManager(PSDK plugin) {
        this.plugin = plugin;
        startTopCacheTask();
        startFlushTask();
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
            topCoinsCache = queryTop(conn, "SELECT name, coins AS value FROM player_economy ORDER BY coins DESC LIMIT 10");
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
        addCurrency(uuid, name, Currency.TOKENS, amount);
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

    public void addCoins(UUID uuid, String name, double amount) {
        addCurrency(uuid, name, Currency.COINS, amount);
    }

    public boolean removeCoins(UUID uuid, double amount) {
        return removeCurrency(uuid, Currency.COINS, amount);
    }

    public boolean hasCoins(UUID uuid, double amount) {
        return getCoins(uuid) >= amount;
    }

    // ==================== Generic helpers ====================

    /** Carrega [tokens, coins] do banco (1 query). Usado só no primeiro acesso de cada jogador. */
    private double[] loadFromDb(UUID uuid) {
        double[] b = {0, 0};
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection()
                .prepareStatement("SELECT tokens, coins FROM player_economy WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) { b[0] = rs.getDouble("tokens"); b[1] = rs.getDouble("coins"); }
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

    private void addCurrency(UUID uuid, String name, Currency currency, double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return;  // só adiciona valores finitos e positivos
        double[] b = balances.computeIfAbsent(uuid, k -> loadFromDb(uuid));
        b[currency.idx] += amount;
        if (name != null) nameCache.put(uuid, name);
        if (currency == Currency.COINS) {
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
                INSERT INTO player_economy (uuid, name, tokens, coins) VALUES (?, ?, 0, 0)
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
