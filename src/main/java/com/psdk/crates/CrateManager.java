package com.psdk.crates;

import com.psdk.PSDK;
import com.psdk.util.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class CrateManager {

    private final PSDK plugin;
    private final Map<String, Crate> crates = new ConcurrentHashMap<>();

    // Cache de saldo de chaves (uuid -> (crate -> saldo)) para leitura SEM banco em
    // threads assíncronas (placeholders do FancyHolograms). O banco continua sendo a
    // fonte da verdade; o cache é atualizado a cada mutação e carregado sob demanda.
    private final Map<UUID, Map<String, Integer>> saldoCache = new ConcurrentHashMap<>();
    private final Set<UUID> saldoWarming = ConcurrentHashMap.newKeySet();

    public CrateManager(PSDK plugin) {
        this.plugin = plugin;
    }

    /** Limpa o cache de saldo de chaves em memória (usado no reset de lançamento). */
    public void clearSaldoCache() {
        saldoCache.clear();
        saldoWarming.clear();
    }

    public void loadAll() {
        crates.clear();
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();

            // Migração: adiciona coluna nexo_key_id se não existir
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE crates ADD COLUMN nexo_key_id TEXT NOT NULL DEFAULT ''");
            } catch (SQLException ignored) {}

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM crates")) {
                while (rs.next()) {
                    try {
                        Crate crate = fromResultSet(rs);
                        loadCrateItems(conn, crate);
                        crates.put(crate.getNome(), crate);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Erro ao carregar crate: " + rs.getString("name"), e);
                    }
                }
            }

            plugin.getLogger().info("[Crates] Carregadas " + crates.size() + " crate(s).");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao carregar crates do banco de dados!", e);
        }
    }

    private Crate fromResultSet(ResultSet rs) throws SQLException {
        String name = rs.getString("name");
        Crate crate = new Crate(name);
        crate.setTipo(Crate.Tipo.valueOf(rs.getString("tipo")));
        crate.setVisual(parseVisual(rs.getString("visual")));
        crate.setTituloMenu(rs.getString("titulo_menu"));
        crate.setLimiteGlobal(rs.getInt("limite_global"));

        String customItemB64 = rs.getString("custom_hologram_item");
        if (customItemB64 != null && !customItemB64.isEmpty()) {
            crate.setCustomHologramItem(ItemSerializer.fromBase64(customItemB64));
        }

        String cor = rs.getString("cor");
        if (cor != null && !cor.isEmpty()) {
            crate.setCor(cor);
        }

        String mundo = rs.getString("mundo");
        if (mundo != null && !mundo.isEmpty()) {
            World world = Bukkit.getWorld(mundo);
            if (world != null) {
                crate.setLocal(new Location(world,
                        rs.getDouble("loc_x"),
                        rs.getDouble("loc_y"),
                        rs.getDouble("loc_z")));
            }
        }

        crate.setBlockDisplayUUID(rs.getString("block_display_uuid"));
        crate.setInteractionUUID(rs.getString("interaction_uuid"));

        String nexoKeyId = rs.getString("nexo_key_id");
        if (nexoKeyId != null && !nexoKeyId.isEmpty()) {
            crate.setNexoKeyId(nexoKeyId);
        }

        try { crate.setPrecoToken(rs.getDouble("preco_token")); } catch (Exception ignored) {}

        CrateKey key = new CrateKey();
        String matStr = rs.getString("key_material");
        Material mat = matStr != null ? Material.matchMaterial(matStr) : null;
        key.setMaterial(mat != null ? mat : Material.TRIPWIRE_HOOK);
        key.setDisplayName(rs.getString("key_display_name"));
        String loreStr = rs.getString("key_lore");
        key.setLore(loreStr != null && !loreStr.isEmpty()
                ? new ArrayList<>(List.of(loreStr.split("\n")))
                : new ArrayList<>());
        key.setNbtKey(rs.getString("key_nbt_key"));
        crate.setItemChave(key);

        return crate;
    }

    private Crate.Visual parseVisual(String value) {
        if (value == null) return Crate.Visual.BAU;
        return switch (value.toUpperCase()) {
            case "BLOCO" -> Crate.Visual.BAU;
            case "HOLOGRAMA" -> Crate.Visual.ENDERCHEST;
            default -> {
                try { yield Crate.Visual.valueOf(value.toUpperCase()); }
                catch (IllegalArgumentException e) { yield Crate.Visual.BAU; }
            }
        };
    }

    private void loadCrateItems(Connection conn, Crate crate) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT slot, item_data FROM crate_items WHERE crate_name = ? ORDER BY slot")) {
            ps.setString(1, crate.getNome());
            ResultSet rs = ps.executeQuery();
            // Reconstrói posicionalmente: índice = slot armazenado, preservando lacunas.
            List<ItemStack> items = new ArrayList<>(Collections.nCopies(Crate.MAX_ITENS, null));
            while (rs.next()) {
                int slot = rs.getInt("slot");
                if (slot < 0 || slot >= Crate.MAX_ITENS) continue;
                ItemStack item = ItemSerializer.fromBase64(rs.getString("item_data"));
                if (item != null) items.set(slot, item);
            }
            crate.setItens(items);
        }
    }

    public void saveCrate(Crate crate) {
        Connection conn = null;
        try {
            conn = plugin.getDatabaseManager().getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT OR REPLACE INTO crates
                    (name, tipo, visual, custom_hologram_item, cor, titulo_menu, limite_global, mundo, loc_x, loc_y, loc_z,
                     block_display_uuid, interaction_uuid, key_material, key_display_name, key_lore, key_nbt_key, nexo_key_id, preco_token)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")) {
                ps.setString(1, crate.getNome());
                ps.setString(2, crate.getTipo().name());
                ps.setString(3, crate.getVisual().name());

                ItemStack customItem = crate.getCustomHologramItem();
                ps.setString(4, customItem != null ? ItemSerializer.toBase64(customItem) : "");

                ps.setString(5, crate.getCor());
                ps.setString(6, crate.getTituloMenu());
                ps.setInt(7, crate.getLimiteGlobal());

                Location loc = crate.getLocal();
                ps.setString(8, loc != null && loc.getWorld() != null ? loc.getWorld().getName() : "");
                ps.setDouble(9, loc != null ? loc.getX() : 0);
                ps.setDouble(10, loc != null ? loc.getY() : 0);
                ps.setDouble(11, loc != null ? loc.getZ() : 0);

                ps.setString(12, crate.getBlockDisplayUUID() != null ? crate.getBlockDisplayUUID() : "");
                ps.setString(13, crate.getInteractionUUID() != null ? crate.getInteractionUUID() : "");

                CrateKey key = crate.getItemChave();
                ps.setString(14, key != null ? key.getMaterial().name() : "TRIPWIRE_HOOK");
                ps.setString(15, key != null ? key.getDisplayName() : "");
                ps.setString(16, key != null && key.getLore() != null ? String.join("\n", key.getLore()) : "");
                ps.setString(17, key != null ? key.getNbtKey() : "psdk_key_" + crate.getNome());
                ps.setString(18, crate.getNexoKeyId() != null ? crate.getNexoKeyId() : "");
                ps.setDouble(19, crate.getPrecoToken());

                ps.executeUpdate();
            }

            try (PreparedStatement del = conn.prepareStatement("DELETE FROM crate_items WHERE crate_name = ?")) {
                del.setString(1, crate.getNome());
                del.executeUpdate();
            }

            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO crate_items (crate_name, slot, item_data) VALUES (?, ?, ?)")) {
                List<ItemStack> items = crate.getItens();
                for (int i = 0; i < items.size(); i++) {
                    String b64 = ItemSerializer.toBase64(items.get(i));
                    if (b64 != null) {
                        ins.setString(1, crate.getNome());
                        ins.setInt(2, i);
                        ins.setString(3, b64);
                        ins.addBatch();
                    }
                }
                ins.executeBatch();
            }

            conn.commit();
            conn.setAutoCommit(true);
            crates.put(crate.getNome(), crate);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao salvar crate: " + crate.getNome(), e);
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
            }
        }
    }
    public void updateNexoKeyId(Crate crate) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "UPDATE crates SET nexo_key_id = ? WHERE name = ?")) {
            ps.setString(1, crate.getNexoKeyId() != null ? crate.getNexoKeyId() : "");
            ps.setString(2, crate.getNome());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao atualizar nexo_key_id", e);
        }
    }

    public void updateLimiteGlobal(Crate crate) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "UPDATE crates SET limite_global = ? WHERE name = ?")) {
            ps.setInt(1, crate.getLimiteGlobal());
            ps.setString(2, crate.getNome());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao atualizar limite global", e);
        }
    }

    // --- Key balance operations ---

    public int getSaldo(UUID playerUUID, String crateName) {
        String sql = "SELECT saldo FROM crate_keys WHERE player_uuid = ? AND crate_name = ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, crateName);
            try (ResultSet rs = ps.executeQuery()) {
                int val = rs.next() ? rs.getInt("saldo") : 0;
                cacheSet(playerUUID, crateName, val);   // mantém o cache aquecido
                return val;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao obter saldo", e);
        }
        return 0;
    }

    /**
     * Saldo de chaves SEM acessar o banco — seguro para threads assíncronas
     * (placeholders/FancyHolograms). Se ainda não estiver em cache, dispara um
     * carregamento na thread principal e retorna o último valor conhecido (ou 0).
     */
    public int getSaldoCached(UUID playerUUID, String crateName) {
        Map<String, Integer> m = saldoCache.get(playerUUID);
        if (m != null) {
            Integer v = m.get(crateName);
            if (v != null) return v;
        }
        warmSaldoCache(playerUUID);
        m = saldoCache.get(playerUUID);
        return (m != null && m.get(crateName) != null) ? m.get(crateName) : 0;
    }

    /** Carrega todos os saldos do jogador para o cache (na thread principal, sem corrida com o banco). */
    private void warmSaldoCache(UUID uuid) {
        if (!saldoWarming.add(uuid)) return;   // já existe um carregamento em andamento
        Runnable load = () -> {
            try {
                Map<String, Integer> m = new ConcurrentHashMap<>();
                String sql = "SELECT crate_name, saldo FROM crate_keys WHERE player_uuid = ?";
                try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) m.put(rs.getString("crate_name"), rs.getInt("saldo"));
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Erro ao carregar saldos (cache)", e);
                }
                saldoCache.put(uuid, m);
            } finally {
                saldoWarming.remove(uuid);
            }
        };
        if (Bukkit.isPrimaryThread()) load.run();
        else Bukkit.getScheduler().runTask(plugin, load);
    }

    private void cacheSet(UUID uuid, String crateName, int val) {
        saldoCache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(crateName, Math.max(0, val));
    }

    /** Ajusta o saldo em cache por um delta (só se já houver valor em cache; senão será recarregado sob demanda). */
    private void cacheBump(UUID uuid, String crateName, int delta) {
        Map<String, Integer> m = saldoCache.get(uuid);
        if (m != null) {
            Integer cur = m.get(crateName);
            if (cur != null) m.put(crateName, Math.max(0, cur + delta));
        }
    }

    public void setSaldo(UUID playerUUID, String crateName, int saldo) {
        String sql = """
                INSERT INTO crate_keys (player_uuid, crate_name, saldo)
                VALUES (?, ?, ?)
                ON CONFLICT(player_uuid, crate_name) DO UPDATE SET saldo = excluded.saldo""";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, crateName);
            ps.setInt(3, Math.max(0, saldo));
            ps.executeUpdate();
            cacheSet(playerUUID, crateName, saldo);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao salvar saldo", e);
        }
    }

    public void addSaldo(UUID playerUUID, String crateName, int amount) {
        // ATÔMICO: soma direto no banco (sem ler-depois-escrever) -> sem dupe em clique duplo.
        String sql = """
                INSERT INTO crate_keys (player_uuid, crate_name, saldo) VALUES (?, ?, ?)
                ON CONFLICT(player_uuid, crate_name) DO UPDATE SET saldo = saldo + ?""";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, crateName);
            ps.setInt(3, Math.max(0, amount));
            ps.setInt(4, amount);
            ps.executeUpdate();
            cacheBump(playerUUID, crateName, amount);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao adicionar saldo", e);
        }
    }

    public boolean consumeSaldo(UUID playerUUID, String crateName) {
        // ATÔMICO: debita 1 só se saldo > 0, numa única operação -> sem dupe de recompensa.
        String sql = "UPDATE crate_keys SET saldo = saldo - 1 WHERE player_uuid = ? AND crate_name = ? AND saldo > 0";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, crateName);
            boolean ok = ps.executeUpdate() == 1;
            if (ok) cacheBump(playerUUID, crateName, -1);
            return ok;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao consumir saldo", e);
            return false;
        }
    }

    public void deleteCrate(String nome) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM crates WHERE name = ?")) {
                ps.setString(1, nome); ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM crate_items WHERE crate_name = ?")) {
                ps.setString(1, nome); ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM crate_keys WHERE crate_name = ?")) {
                ps.setString(1, nome); ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao deletar crate: " + nome, e);
        }
        crates.remove(nome);
    }

    // --- Lookups ---

    public Collection<Crate> getAllCrates() { return crates.values(); }
    public Crate getCrate(String nome) { return crates.get(nome); }
    public boolean hasCrate(String nome) { return crates.containsKey(nome); }
    public Map<String, Crate> getCratesMap() { return crates; }

    public Crate getCrateByLocation(Location loc) {
        for (Crate crate : crates.values()) {
            if (crate.getLocal() == null) continue;
            Location cl = crate.getLocal();
            if (cl.getWorld() != null
                    && cl.getWorld().equals(loc.getWorld())
                    && cl.getBlockX() == loc.getBlockX()
                    && cl.getBlockY() == loc.getBlockY()
                    && cl.getBlockZ() == loc.getBlockZ()) {
                return crate;
            }
        }
        return null;
    }

    public Crate getCrateByInteractionUUID(String uuid) {
        for (Crate crate : crates.values()) {
            if (uuid.equals(crate.getInteractionUUID())) return crate;
        }
        return null;
    }
}