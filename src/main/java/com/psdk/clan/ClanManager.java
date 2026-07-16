package com.psdk.clan;

import com.psdk.PSDK;
import org.bukkit.Bukkit;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ClanManager {

    private final PSDK plugin;
    private final Map<UUID, Clan> playerClanCache = new ConcurrentHashMap<>();
    // Cache negativo: jogadores que já sabemos NÃO ter clan (evita query repetida).
    private final Set<UUID> clanlessCache = ConcurrentHashMap.newKeySet();
    // Guarda contra agendar múltiplos warm-ups simultâneos para o mesmo jogador.
    private final Set<UUID> warmingClan = ConcurrentHashMap.newKeySet();

    public ClanManager(PSDK plugin) {
        this.plugin = plugin;
        ensureTables();
    }

    /**
     * O DDL das tabelas de clan vive em schema.sql (SchemaManager cria tabelas e
     * colunas faltantes na migração). Aqui só resta o reparo legado do clan_chest,
     * cuja mudança de PRIMARY KEY o SQLite não permite via ALTER TABLE.
     */
    private void ensureTables() {
        try {
            migrateClanChest(plugin.getDatabaseManager().getConnection());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao migrar clan_chest", e);
        }
    }

    private void migrateClanChest(Connection conn) throws SQLException {
        // Verifica se a tabela existe e se tem a coluna page como parte da PK
        boolean tableExists = false;
        boolean hasPage = false;
        boolean pageIsPK = false;

        try (Statement s = conn.createStatement();
             ResultSet tables = s.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='clan_chest'")) {
            tableExists = tables.next();
        }

        if (!tableExists) {
            // Cria com o schema correto
            try (Statement s = conn.createStatement()) {
                s.execute("CREATE TABLE clan_chest (clan_id INTEGER NOT NULL, page INTEGER NOT NULL DEFAULT 0, inventory TEXT NOT NULL DEFAULT '', PRIMARY KEY (clan_id, page))");
            }
            return;
        }

        try (Statement s = conn.createStatement();
             ResultSet pragma = s.executeQuery("PRAGMA table_info(clan_chest)")) {
            while (pragma.next()) {
                if ("page".equals(pragma.getString("name"))) {
                    hasPage = true;
                    pageIsPK = pragma.getInt("pk") > 0;
                }
            }
        }

        if (!hasPage || !pageIsPK) {
            // Recria a tabela com schema correto, preservando dados da página 0
            plugin.getLogger().info("[ClanManager] Migrando clan_chest para schema multi-página...");
            try (Statement s = conn.createStatement()) {
                s.execute("ALTER TABLE clan_chest RENAME TO clan_chest_old");
                s.execute("CREATE TABLE clan_chest (clan_id INTEGER NOT NULL, page INTEGER NOT NULL DEFAULT 0, inventory TEXT NOT NULL DEFAULT '', PRIMARY KEY (clan_id, page))");
                s.execute("INSERT OR IGNORE INTO clan_chest (clan_id, page, inventory) SELECT clan_id, 0, inventory FROM clan_chest_old");
                s.execute("DROP TABLE clan_chest_old");
            }
            plugin.getLogger().info("[ClanManager] Migração clan_chest concluída.");
        }
    }

    public Clan createClan(UUID leader, String tag, String name, boolean bypassTagLimit) {
        if (!bypassTagLimit) tag = tag.toUpperCase();
        if (!bypassTagLimit && !tag.matches("[A-Z0-9]+")) return null;
        if (!bypassTagLimit && (tag.length() < 3 || tag.length() > 4)) return null;
        if (bypassTagLimit && tag.length() < 2) return null;
        if (name.length() < 3 || name.length() > 16) return null;
        if (getClanByTag(tag) != null) return null;
        if (getClanByPlayer(leader) != null) return null;

        long now = System.currentTimeMillis();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "INSERT INTO clans (tag, name, leader, color, created) VALUES (?, ?, ?, '#FFFFFF', ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tag);
            ps.setString(2, name);
            ps.setString(3, leader.toString());
            ps.setLong(4, now);
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (!keys.next()) return null;
            int clanId = keys.getInt(1);

            String leaderName = Bukkit.getOfflinePlayer(leader).getName();
            if (leaderName == null) leaderName = "???";

            try (PreparedStatement ps2 = plugin.getDatabaseManager().getConnection().prepareStatement(
                    "INSERT INTO clan_members (clan_id, player_uuid, player_name, role, joined_at) VALUES (?, ?, ?, 'lider', ?)")) {
                ps2.setInt(1, clanId);
                ps2.setString(2, leader.toString());
                ps2.setString(3, leaderName);
                ps2.setLong(4, now);
                ps2.executeUpdate();
            }

            Clan clan = new Clan(clanId, tag, name, leader, "#FFFFFF", now, false);
            clan.setMembers(List.of(new ClanMember(leader, leaderName, "lider", now)));
            invalidateCache();
            getRoles(clanId); // semeia os cargos padrão (Vice/Membro) já na criação
            return clan;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao criar clan", e);
            return null;
        }
    }

    /** Apaga o clan e TODOS os dados relacionados em uma transação (sem lixo no DB). */
    public boolean deleteClan(int clanId) {
        Connection conn;
        try {
            conn = plugin.getDatabaseManager().getConnection();
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao deletar clan", e);
            return false;
        }
        try {
            String[] byClanId = {
                    "DELETE FROM clan_invites WHERE clan_id = ?",
                    "DELETE FROM clan_requests WHERE clan_id = ?",
                    "DELETE FROM clan_permissions WHERE clan_id = ?",
                    "DELETE FROM clan_roles WHERE clan_id = ?",
                    "DELETE FROM clan_logs WHERE clan_id = ?",
                    "DELETE FROM clan_chest WHERE clan_id = ?",
                    "DELETE FROM clan_market_items WHERE clan_id = ?",
                    "DELETE FROM clan_activated_colors WHERE clan_id = ?",
                    "DELETE FROM clan_treasury WHERE clan_id = ?",
                    "DELETE FROM clan_allies WHERE clan_id = ? OR ally_clan_id = ?",
                    "DELETE FROM clan_rivals WHERE clan_id = ? OR rival_clan_id = ?",
                    "DELETE FROM clan_ally_requests WHERE clan_id = ? OR target_clan_id = ?",
                    "DELETE FROM clan_members WHERE clan_id = ?",
                    "DELETE FROM clans WHERE id = ?"
            };
            for (String sql : byClanId) {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, clanId);
                    if (sql.contains("?") && sql.indexOf('?') != sql.lastIndexOf('?')) {
                        ps.setInt(2, clanId);
                    }
                    ps.executeUpdate();
                }
            }
            conn.commit();
            invalidateCache();
            return true;
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            plugin.getLogger().log(Level.WARNING, "Erro ao deletar clan", e);
            return false;
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /** Sincroniza o nome atual do jogador em clan_members (nick pode mudar). */
    public void syncMemberName(UUID player, String name) {
        if (player == null || name == null || name.isBlank()) return;
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "UPDATE clan_members SET player_name = ? WHERE player_uuid = ? AND player_name != ?")) {
            ps.setString(1, name);
            ps.setString(2, player.toString());
            ps.setString(3, name);
            if (ps.executeUpdate() > 0) invalidateCache();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao sincronizar nome do membro", e);
        }
    }

    public Clan getClanByPlayer(UUID player) {
        Clan cached = playerClanCache.get(player);
        if (cached != null) return cached;
        if (clanlessCache.contains(player)) return null;

        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT c.id, c.tag, c.name, c.leader, c.color, c.public, c.created, c.friendly_fire, c.ally_ff, c.description FROM clans c " +
                "JOIN clan_members m ON c.id = m.clan_id WHERE m.player_uuid = ?")) {
            ps.setString(1, player.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Clan clan = buildClanFromResultSet(rs);
                clan.setMembers(getMembers(clan.getId()));
                playerClanCache.put(player, clan);
                return clan;
            }
            clanlessCache.add(player);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao buscar clan do jogador", e);
        }
        return null;
    }

    /**
     * Leitura APENAS do cache em memória — nunca toca no banco, portanto é seguro
     * chamar de threads async (ex.: PlaceholderAPI via UnlimitedNametags/TAB).
     * Retorna null se o jogador não tem clan OU se o clan ainda não foi carregado.
     */
    public Clan getCachedClanByPlayer(UUID player) {
        if (player == null) return null;
        return playerClanCache.get(player);
    }

    /** True se já sabemos o estado de clan do jogador (com ou sem clan). */
    public boolean isClanStateKnown(UUID player) {
        return player != null && (playerClanCache.containsKey(player) || clanlessCache.contains(player));
    }

    /**
     * Garante (de forma assíncrona-segura) que o clan do jogador seja carregado no
     * cache, agendando a consulta ao banco para o main thread. Não bloqueia o chamador
     * e faz debounce para não floodar o scheduler.
     */
    public void warmPlayerClan(UUID player) {
        if (player == null || isClanStateKnown(player)) return;
        if (!warmingClan.add(player)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                getClanByPlayer(player);
            } finally {
                warmingClan.remove(player);
            }
        });
    }

    public Clan getClanByTag(String tag) {
        Clan clan = fetchClanByTagExact(tag);
        if (clan == null && !tag.equals(tag.toUpperCase())) {
            clan = fetchClanByTagExact(tag.toUpperCase());
        }
        return clan;
    }

    private Clan fetchClanByTagExact(String tag) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT id, tag, name, leader, color, public, created, friendly_fire, ally_ff, description FROM clans WHERE tag = ?")) {
            ps.setString(1, tag);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Clan clan = buildClanFromResultSet(rs);
                clan.setMembers(getMembers(clan.getId()));
                return clan;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao buscar clan por tag", e);
        }
        return null;
    }

    public List<Clan> getAllClans() {
        List<Clan> clans = new ArrayList<>();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT id, tag, name, leader, color, public, created, friendly_fire, ally_ff, description FROM clans")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Clan clan = buildClanFromResultSet(rs);
                clan.setMembers(getMembers(clan.getId()));
                clans.add(clan);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao listar clans", e);
        }
        return clans;
    }

    /**
     * Todos os clans com pelo menos um membro online (públicos E privados).
     * O navegador de clans usa esta lista: clan público → entra direto;
     * clan privado → envia solicitação de entrada (tratado em ClanGUIListener).
     */
    public List<Clan> getOnlineClans() {
        List<Clan> all = getAllClans();
        List<Clan> online = new ArrayList<>();
        for (Clan clan : all) {
            for (ClanMember member : clan.getMembers()) {
                if (Bukkit.getPlayer(member.uuid()) != null) {
                    online.add(clan);
                    break;
                }
            }
        }
        return online;
    }

    public List<Clan> getPublicOnlineClans() {
        List<Clan> online = new ArrayList<>();
        for (Clan clan : getOnlineClans()) {
            if (clan.isPublic()) online.add(clan);
        }
        return online;
    }

    public boolean setClanPublic(int clanId, boolean isPublic) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "UPDATE clans SET public = ? WHERE id = ?")) {
            ps.setInt(1, isPublic ? 1 : 0);
            ps.setInt(2, clanId);
            ps.executeUpdate();
            invalidateCache();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao alterar visibilidade do clan", e);
            return false;
        }
    }

    public boolean addAlly(int clanId, int allyClanId) {
        if (clanId == allyClanId) return false;
        // Transação: aliar remove rivalidade mútua no mesmo commit (nunca ficam os dois).
        Connection conn;
        try {
            conn = plugin.getDatabaseManager().getConnection();
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao adicionar aliado", e);
            return false;
        }
        try {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM clan_rivals WHERE (clan_id = ? AND rival_clan_id = ?) OR (clan_id = ? AND rival_clan_id = ?)")) {
                ps.setInt(1, clanId); ps.setInt(2, allyClanId);
                ps.setInt(3, allyClanId); ps.setInt(4, clanId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO clan_allies (clan_id, ally_clan_id) VALUES (?, ?), (?, ?)")) {
                ps.setInt(1, clanId); ps.setInt(2, allyClanId);
                ps.setInt(3, allyClanId); ps.setInt(4, clanId);
                ps.executeUpdate();
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            plugin.getLogger().log(Level.WARNING, "Erro ao adicionar aliado", e);
            return false;
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public boolean removeAlly(int clanId, int allyClanId) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM clan_allies WHERE clan_id = ? AND ally_clan_id = ?")) {
                ps.setInt(1, clanId);
                ps.setInt(2, allyClanId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM clan_allies WHERE clan_id = ? AND ally_clan_id = ?")) {
                ps.setInt(1, allyClanId);
                ps.setInt(2, clanId);
                ps.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao remover aliado", e);
            return false;
        }
    }

    public List<Clan> getAllies(int clanId) {
        List<Clan> allies = new ArrayList<>();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT c.id, c.tag, c.name, c.leader, c.color, c.public, c.created, c.friendly_fire, c.ally_ff, c.description FROM clan_allies a JOIN clans c ON c.id = a.ally_clan_id WHERE a.clan_id = ?")) {
            ps.setInt(1, clanId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                allies.add(buildClanFromResultSet(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao buscar aliados", e);
        }
        return allies;
    }

    public boolean areAllied(int clanId, int otherClanId) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT 1 FROM clan_allies WHERE clan_id = ? AND ally_clan_id = ?")) {
            ps.setInt(1, clanId);
            ps.setInt(2, otherClanId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            return false;
        }
    }

    // ===== RIVAIS =====

    public boolean addRival(int clanId, int rivalClanId) {
        if (clanId == rivalClanId) return false;
        // Transação: rivalizar remove aliança mútua no mesmo commit.
        Connection conn;
        try {
            conn = plugin.getDatabaseManager().getConnection();
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao adicionar rival", e);
            return false;
        }
        try {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM clan_allies WHERE (clan_id = ? AND ally_clan_id = ?) OR (clan_id = ? AND ally_clan_id = ?)")) {
                ps.setInt(1, clanId); ps.setInt(2, rivalClanId);
                ps.setInt(3, rivalClanId); ps.setInt(4, clanId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO clan_rivals (clan_id, rival_clan_id) VALUES (?, ?), (?, ?)")) {
                ps.setInt(1, clanId); ps.setInt(2, rivalClanId);
                ps.setInt(3, rivalClanId); ps.setInt(4, clanId);
                ps.executeUpdate();
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            plugin.getLogger().log(Level.WARNING, "Erro ao adicionar rival", e);
            return false;
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public boolean removeRival(int clanId, int rivalClanId) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM clan_rivals WHERE clan_id = ? AND rival_clan_id = ?")) {
                ps.setInt(1, clanId); ps.setInt(2, rivalClanId); ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM clan_rivals WHERE clan_id = ? AND rival_clan_id = ?")) {
                ps.setInt(1, rivalClanId); ps.setInt(2, clanId); ps.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao remover rival", e);
            return false;
        }
    }

    public List<Clan> getRivals(int clanId) {
        List<Clan> rivals = new ArrayList<>();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT c.id, c.tag, c.name, c.leader, c.color, c.public, c.created, c.friendly_fire, c.ally_ff, c.description FROM clan_rivals r JOIN clans c ON c.id = r.rival_clan_id WHERE r.clan_id = ?")) {
            ps.setInt(1, clanId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) rivals.add(buildClanFromResultSet(rs));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao buscar rivais", e);
        }
        return rivals;
    }

    public boolean areRivals(int clanId, int otherClanId) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT 1 FROM clan_rivals WHERE clan_id = ? AND rival_clan_id = ?")) {
            ps.setInt(1, clanId);
            ps.setInt(2, otherClanId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            return false;
        }
    }

    // ===== TESOURO =====

    /** Saldo em coins do tesouro do clan. */
    public double getTreasuryCoins(int clanId) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT coins FROM clan_treasury WHERE clan_id = ?")) {
            ps.setInt(1, clanId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("coins");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao ler tesouro do clan", e);
        }
        return 0;
    }

    /**
     * Depósito atômico: debita o cache de economia (fonte da verdade em runtime) e,
     * na MESMA chamada síncrona, persiste saldo do jogador + crédito do tesouro em
     * uma transação. Falhou o commit → o débito do cache é desfeito. Assim não há
     * janela em que o flush write-behind grave um débito sem o crédito correspondente.
     */
    public boolean depositToTreasury(int clanId, UUID player, String playerName, double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return false;
        var eco = plugin.getEconomyManager();
        if (!eco.removeCoins(player, amount)) return false; // saldo insuficiente

        Connection conn;
        try {
            conn = plugin.getDatabaseManager().getConnection();
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            eco.addCoinsNoStat(player, playerName, amount); // devolução: não conta no Top Coins
            plugin.getLogger().log(Level.WARNING, "Erro ao depositar no tesouro", e);
            return false;
        }
        try {
            persistPlayerBalance(conn, player, playerName);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO clan_treasury (clan_id, coins) VALUES (?, ?) "
                            + "ON CONFLICT(clan_id) DO UPDATE SET coins = coins + excluded.coins")) {
                ps.setInt(1, clanId);
                ps.setDouble(2, amount);
                ps.executeUpdate();
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            eco.addCoinsNoStat(player, playerName, amount); // desfaz o débito do cache (não conta no Top Coins)
            plugin.getLogger().log(Level.WARNING, "Erro ao depositar no tesouro", e);
            return false;
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /**
     * Saque atômico: debita o tesouro com guarda {@code coins >= ?} (0 rows → saldo
     * insuficiente, rollback) e credita o jogador via cache + persistência na mesma
     * transação.
     */
    public boolean withdrawFromTreasury(int clanId, UUID player, String playerName, double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return false;
        var eco = plugin.getEconomyManager();
        eco.addCoinsNoStat(player, playerName, amount); // saque do tesouro: movimentação, não conta no Top Coins

        Connection conn;
        try {
            conn = plugin.getDatabaseManager().getConnection();
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            eco.removeCoins(player, amount);
            plugin.getLogger().log(Level.WARNING, "Erro ao sacar do tesouro", e);
            return false;
        }
        try {
            int updated;
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE clan_treasury SET coins = coins - ? WHERE clan_id = ? AND coins >= ?")) {
                ps.setDouble(1, amount);
                ps.setInt(2, clanId);
                ps.setDouble(3, amount);
                updated = ps.executeUpdate();
            }
            if (updated == 0) {
                conn.rollback();
                eco.removeCoins(player, amount);
                return false; // tesouro insuficiente
            }
            persistPlayerBalance(conn, player, playerName);
            conn.commit();
            return true;
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            eco.removeCoins(player, amount);
            plugin.getLogger().log(Level.WARNING, "Erro ao sacar do tesouro", e);
            return false;
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /** Grava o saldo ATUAL do cache de economia do jogador dentro da transação do chamador. */
    private void persistPlayerBalance(Connection conn, UUID player, String playerName) throws SQLException {
        var eco = plugin.getEconomyManager();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO player_economy (uuid, name, tokens, coins) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT(uuid) DO UPDATE SET coins = excluded.coins, "
                        + "name = COALESCE(excluded.name, player_economy.name)")) {
            ps.setString(1, player.toString());
            ps.setString(2, playerName);
            ps.setDouble(3, eco.getTokens(player));
            ps.setDouble(4, eco.getCoins(player));
            ps.executeUpdate();
        }
    }

    // ===== SOLICITAÇÕES DE ENTRADA (clans privados) =====

    /** Cria uma solicitação de entrada do jogador para um clan privado. */
    public boolean requestJoin(int clanId, UUID player) {
        if (getClanByPlayer(player) != null) return false;
        String name = Bukkit.getOfflinePlayer(player).getName();
        if (name == null) name = "???";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "INSERT OR IGNORE INTO clan_requests (clan_id, player_uuid, player_name, requested_at) VALUES (?, ?, ?, ?)")) {
            ps.setInt(1, clanId);
            ps.setString(2, player.toString());
            ps.setString(3, name);
            ps.setLong(4, System.currentTimeMillis());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao solicitar entrada", e);
            return false;
        }
    }

    /** Solicitações pendentes de entrada (modeladas como ClanMember com role 'pendente'). */
    public List<ClanMember> getPendingRequests(int clanId) {
        List<ClanMember> pending = new ArrayList<>();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT player_uuid, player_name, requested_at FROM clan_requests WHERE clan_id = ? ORDER BY requested_at ASC")) {
            ps.setInt(1, clanId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                pending.add(new ClanMember(
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("player_name"),
                        "pendente",
                        rs.getLong("requested_at")));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao buscar solicitações", e);
        }
        return pending;
    }

    public void denyRequest(int clanId, UUID player) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "DELETE FROM clan_requests WHERE clan_id = ? AND player_uuid = ?")) {
            ps.setInt(1, clanId);
            ps.setString(2, player.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao recusar solicitação", e);
        }
    }

    /** Aceita uma solicitação: adiciona o jogador ao clan (se houver espaço) e remove o pedido. */
    public boolean acceptRequest(int clanId, UUID player) {
        if (getClanByPlayer(player) != null) { denyRequest(clanId, player); return false; }
        Clan clan = getClanById(clanId);
        if (clan == null || clan.getMembers().size() >= ClanGUI.getClanMemberLimit(clan)) return false;

        String name = Bukkit.getOfflinePlayer(player).getName();
        if (name == null) name = "???";
        ClanRole defaultRole = getDefaultRole(clanId);
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "INSERT OR IGNORE INTO clan_members (clan_id, player_uuid, player_name, role, role_id, joined_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setInt(1, clanId);
            ps.setString(2, player.toString());
            ps.setString(3, name);
            ps.setString(4, defaultRole != null ? defaultRole.name() : "Membro");
            ps.setInt(5, defaultRole != null ? defaultRole.id() : 0);
            ps.setLong(6, System.currentTimeMillis());
            if (ps.executeUpdate() == 0) return false;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao aceitar solicitação", e);
            return false;
        }
        denyRequest(clanId, player);
        invalidateCache();
        return true;
    }

    public boolean invitePlayer(int clanId, UUID target) {
        if (getClanByPlayer(target) != null) return false;
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "INSERT OR REPLACE INTO clan_invites (clan_id, player_uuid, invited_at) VALUES (?, ?, ?)")) {
            ps.setInt(1, clanId);
            ps.setString(2, target.toString());
            ps.setLong(3, now);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao convidar jogador", e);
            return false;
        }
    }

    public List<Clan> getInvitesFor(UUID player) {
        List<Clan> invites = new ArrayList<>();
        long cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000L);
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT c.id, c.tag, c.name, c.leader, c.color, c.public, c.created FROM clan_invites i " +
                "JOIN clans c ON i.clan_id = c.id WHERE i.player_uuid = ? AND i.invited_at > ?")) {
            ps.setString(1, player.toString());
            ps.setLong(2, cutoff);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                invites.add(buildClanFromResultSet(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao buscar convites", e);
        }
        cleanExpiredInvites();
        return invites;
    }

    /** Entrada direta em clan público (sem convite necessário). */
    public boolean joinPublicClan(UUID player, int clanId) {
        Clan clan = getClanById(clanId);
        if (clan == null || !clan.isPublic()) return false;
        if (getClanByPlayer(player) != null) return false;
        if (clan.getMembers().size() >= ClanGUI.getClanMemberLimit(clan)) return false;

        String playerName = Bukkit.getOfflinePlayer(player).getName();
        if (playerName == null) playerName = "???";
        long now = System.currentTimeMillis();
        ClanRole defaultRole = getDefaultRole(clanId);

        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "INSERT OR IGNORE INTO clan_members (clan_id, player_uuid, player_name, role, role_id, joined_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setInt(1, clanId);
            ps.setString(2, player.toString());
            ps.setString(3, playerName);
            ps.setString(4, defaultRole != null ? defaultRole.name() : "Membro");
            ps.setInt(5, defaultRole != null ? defaultRole.id() : 0);
            ps.setLong(6, now);
            int rows = ps.executeUpdate();
            if (rows == 0) return false; // já era membro
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao entrar em clan público", e);
            return false;
        }

        invalidateCache();
        return true;
    }

    public boolean acceptInvite(UUID player, String tag) {
        Clan clan = getClanByTag(tag);
        if (clan == null) return false;
        if (getClanByPlayer(player) != null) return false;
        if (clan.getMembers().size() >= ClanGUI.getClanMemberLimit(clan)) return false;

        try (PreparedStatement check = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT 1 FROM clan_invites WHERE clan_id = ? AND player_uuid = ?")) {
            check.setInt(1, clan.getId());
            check.setString(2, player.toString());
            if (!check.executeQuery().next()) return false;
        } catch (SQLException e) {
            return false;
        }

        String playerName = Bukkit.getOfflinePlayer(player).getName();
        if (playerName == null) playerName = "???";
        long now = System.currentTimeMillis();
        ClanRole defaultRole = getDefaultRole(clan.getId());

        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "INSERT OR IGNORE INTO clan_members (clan_id, player_uuid, player_name, role, role_id, joined_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setInt(1, clan.getId());
            ps.setString(2, player.toString());
            ps.setString(3, playerName);
            ps.setString(4, defaultRole != null ? defaultRole.name() : "Membro");
            ps.setInt(5, defaultRole != null ? defaultRole.id() : 0);
            ps.setLong(6, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao aceitar convite", e);
            return false;
        }

        removeInvite(player, tag);
        invalidateCache();

        // Transferir cores que o player desbloqueou para o novo clan
        try {
            ColorManager colorMgr = plugin.getColorManager();
            java.util.List<String> playerColors = colorMgr.getUnlockedColors(player);
            for (String colorName : playerColors) {
                if (!colorMgr.hasColorActivated(clan.getId(), colorName)) {
                    colorMgr.activateColorForClan(clan.getId(), colorName);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao transferir cores para novo clan", e);
        }

        return true;
    }

    public void removeInvite(UUID player, String tag) {
        Clan clan = getClanByTag(tag);
        if (clan == null) return;
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "DELETE FROM clan_invites WHERE clan_id = ? AND player_uuid = ?")) {
            ps.setInt(1, clan.getId());
            ps.setString(2, player.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao remover convite", e);
        }
    }

    public boolean removeMember(int clanId, UUID player) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "DELETE FROM clan_members WHERE clan_id = ? AND player_uuid = ?")) {
            ps.setInt(1, clanId);
            ps.setString(2, player.toString());
            ps.executeUpdate();
            invalidateCache();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao remover membro", e);
            return false;
        }

        // Remover as cores que este player desbloqueou para este clan
        // e resetar a cor ativa do clan para o padrao caso a cor atual tenha sido desbloqueada por ele
        try {
            ColorManager colorMgr = plugin.getColorManager();
            java.util.List<String> playerColors = colorMgr.getUnlockedColors(player);

            if (!playerColors.isEmpty()) {
                // Descobrir qual e a cor ativa atual do clan (pelo objeto em cache)
                Clan clan = getClanById(clanId);
                String currentColorHex = clan != null ? clan.getColorHex() : null;

                boolean activeColorRemoved = false;

                for (String colorName : playerColors) {
                    // Remover esta cor do clan
                    colorMgr.deactivateColorForClan(clanId, colorName);

                    // Verificar se a cor ativa do clan era uma das removidas
                    if (!activeColorRemoved && currentColorHex != null) {
                        ClanColor color = colorMgr.getColor(colorName);
                        if (color != null && currentColorHex.equals(color.getColorHex())) {
                            activeColorRemoved = true;
                        }
                    }
                }

                // Se a cor ativa foi removida, resetar para o padrao
                if (activeColorRemoved) {
                    setClanColor(clanId, "#FFFFFF");
                    if (clan != null) clan.setColorHex("#FFFFFF");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao limpar cores do membro removido", e);
        }

        return true;
    }

    private Clan getClanById(int clanId) {
        for (Clan c : getAllClans()) {
            if (c.getId() == clanId) return c;
        }
        return null;
    }

    public List<ClanMember> getMembers(int clanId) {
        List<ClanMember> members = new ArrayList<>();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT player_uuid, player_name, role, joined_at FROM clan_members WHERE clan_id = ?")) {
            ps.setInt(1, clanId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                members.add(new ClanMember(
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("player_name"),
                        rs.getString("role"),
                        rs.getLong("joined_at")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao buscar membros", e);
        }
        return members;
    }

    public void invalidateCache() {
        playerClanCache.clear();
        clanlessCache.clear();
    }

    public void setClanColor(int clanId, String hex) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "UPDATE clans SET color = ? WHERE id = ?")) {
            ps.setString(1, hex);
            ps.setInt(2, clanId);
            ps.executeUpdate();
            invalidateCache();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao alterar cor do clan", e);
        }
    }

    private void cleanExpiredInvites() {
        long cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000L);
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "DELETE FROM clan_invites WHERE invited_at < ?")) {
            ps.setLong(1, cutoff);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private Clan buildClanFromResultSet(ResultSet rs) throws SQLException {
        boolean isPublic = false;
        boolean friendlyFire = false;
        boolean allyFf = false;
        String description = "";
        try { isPublic = rs.getInt("public") == 1; } catch (SQLException ignored) {}
        try { friendlyFire = rs.getInt("friendly_fire") == 1; } catch (SQLException ignored) {}
        try { allyFf = rs.getInt("ally_ff") == 1; } catch (SQLException ignored) {}
        try { String d = rs.getString("description"); if (d != null) description = d; } catch (SQLException ignored) {}
        Clan clan = new Clan(
                rs.getInt("id"),
                rs.getString("tag"),
                rs.getString("name"),
                UUID.fromString(rs.getString("leader")),
                rs.getString("color"),
                rs.getLong("created"),
                isPublic
        );
        clan.setFriendlyFire(friendlyFire);
        clan.setAllyFriendlyFire(allyFf);
        clan.setDescription(description);
        return clan;
    }

    // ===== PERMISSIONS =====

    public record ClanPerm(boolean invite, boolean kick, boolean chest, boolean market, boolean pvpToggle, boolean treasury) {}

    public ClanPerm getPermissions(int clanId, UUID player) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT perm_invite, perm_kick, perm_chest, perm_market, perm_pvp, perm_treasury FROM clan_permissions WHERE clan_id = ? AND player_uuid = ?")) {
            ps.setInt(1, clanId);
            ps.setString(2, player.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new ClanPerm(
                        rs.getInt("perm_invite") == 1,
                        rs.getInt("perm_kick") == 1,
                        rs.getInt("perm_chest") == 1,
                        rs.getInt("perm_market") == 1,
                        rs.getInt("perm_pvp") == 1,
                        rs.getInt("perm_treasury") == 1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao buscar permissões", e);
        }
        return new ClanPerm(false, false, true, true, false, false);
    }

    public void setPermission(int clanId, UUID player, String perm, boolean value) {
        String col = switch (perm) {
            case "invite"      -> "perm_invite";
            case "kick"        -> "perm_kick";
            case "chest"       -> "perm_chest";
            case "market"      -> "perm_market";
            case "pvp_toggle"  -> "perm_pvp";
            case "treasury"    -> "perm_treasury";
            default -> null;
        };
        if (col == null) return;

        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "INSERT INTO clan_permissions (clan_id, player_uuid, " + col + ") VALUES (?, ?, ?) " +
                "ON CONFLICT(clan_id, player_uuid) DO UPDATE SET " + col + " = ?")) {
            ps.setInt(1, clanId);
            ps.setString(2, player.toString());
            ps.setInt(3, value ? 1 : 0);
            ps.setInt(4, value ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao alterar permissão", e);
        }
    }

    public boolean isLeader(int clanId, UUID player) {
        // Usa o cache por jogador (getClanByPlayer) em vez de varrer todos os clans.
        Clan clan = getClanByPlayer(player);
        return clan != null && clan.getId() == clanId && clan.getLeader().equals(player);
    }

    public boolean isLeader(Clan clan, UUID player) {
        return clan != null && clan.getLeader().equals(player);
    }

    /**
     * Líder tem todas as permissões. Demais membros: permissão do CARGO (clan_roles)
     * OU override individual (clan_permissions) — basta uma das duas.
     */
    public boolean hasPermission(int clanId, UUID player, String perm) {
        if (isLeader(clanId, player)) return true;

        ClanRole role = getMemberRoleObj(clanId, player);
        if (role != null) {
            boolean byRole = switch (perm) {
                case "invite"     -> role.invite();
                case "kick"       -> role.kick();
                case "chest"      -> role.chest();
                case "market"     -> role.market();
                case "pvp_toggle" -> role.pvpToggle();
                case "treasury"   -> role.treasury();
                default -> false;
            };
            if (byRole) return true;
        }

        ClanPerm perms = getPermissions(clanId, player);
        return switch (perm) {
            case "invite"     -> perms.invite();
            case "kick"       -> perms.kick();
            case "chest"      -> perms.chest();
            case "market"     -> perms.market();
            case "pvp_toggle" -> perms.pvpToggle();
            case "treasury"   -> perms.treasury();
            default -> false;
        };
    }

    public boolean setFriendlyFire(int clanId, boolean enabled) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "UPDATE clans SET friendly_fire = ? WHERE id = ?")) {
            ps.setInt(1, enabled ? 1 : 0);
            ps.setInt(2, clanId);
            ps.executeUpdate();
            invalidateCache();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao alterar friendly fire", e);
            return false;
        }
    }

    // ===== CLAN CHEST =====

    public String getChestData(int clanId, int page) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT inventory FROM clan_chest WHERE clan_id = ? AND page = ?")) {
            ps.setInt(1, clanId);
            ps.setInt(2, page);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("inventory");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao buscar baú do clan", e);
        }
        return "";
    }

    /** @deprecated Use {@link #getChestData(int, int)} instead */
    @Deprecated
    public String getChestData(int clanId) {
        return getChestData(clanId, 0);
    }

    public void saveChestData(int clanId, int page, String data) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "INSERT OR REPLACE INTO clan_chest (clan_id, page, inventory) VALUES (?, ?, ?)")) {
            ps.setInt(1, clanId);
            ps.setInt(2, page);
            ps.setString(3, data);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao salvar baú do clan", e);
        }
    }

    /** @deprecated Use {@link #saveChestData(int, int, String)} instead */
    @Deprecated
    public void saveChestData(int clanId, String data) {
        saveChestData(clanId, 0, data);
    }

    // ===== CLAN MARKET =====

    public record MarketItem(int id, int clanId, UUID seller, String sellerName, String itemData, double price, long listedAt) {}

    public boolean listMarketItem(int clanId, UUID seller, String itemData, double price) {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "INSERT INTO clan_market_items (clan_id, seller, item_data, price, listed_at) VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, clanId);
            ps.setString(2, seller.toString());
            ps.setString(3, itemData);
            ps.setDouble(4, price);
            ps.setLong(5, now);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao listar item no mercado", e);
            return false;
        }
    }

    public List<MarketItem> getMarketItems(int clanId) {
        List<MarketItem> items = new ArrayList<>();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT id, clan_id, seller, item_data, price, listed_at FROM clan_market_items WHERE clan_id = ? ORDER BY listed_at DESC")) {
            ps.setInt(1, clanId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String sellerUuid = rs.getString("seller");
                String sellerName = Bukkit.getOfflinePlayer(UUID.fromString(sellerUuid)).getName();
                if (sellerName == null) sellerName = "???";
                items.add(new MarketItem(rs.getInt("id"), rs.getInt("clan_id"),
                        UUID.fromString(sellerUuid), sellerName, rs.getString("item_data"),
                        rs.getDouble("price"), rs.getLong("listed_at")));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao buscar itens do mercado", e);
        }
        return items;
    }

    public MarketItem getMarketItem(int itemId) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT id, clan_id, seller, item_data, price, listed_at FROM clan_market_items WHERE id = ?")) {
            ps.setInt(1, itemId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String sellerUuid = rs.getString("seller");
                String sellerName = Bukkit.getOfflinePlayer(UUID.fromString(sellerUuid)).getName();
                if (sellerName == null) sellerName = "???";
                return new MarketItem(rs.getInt("id"), rs.getInt("clan_id"),
                        UUID.fromString(sellerUuid), sellerName, rs.getString("item_data"),
                        rs.getDouble("price"), rs.getLong("listed_at"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao buscar item do mercado", e);
        }
        return null;
    }

    public boolean removeMarketItem(int itemId) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "DELETE FROM clan_market_items WHERE id = ?")) {
            ps.setInt(1, itemId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao remover item do mercado", e);
            return false;
        }
    }

    /** Recoloca um anúncio no mercado (usado quando uma compra é desfeita por falha de pagamento). */
    public void relistMarketItem(MarketItem mi) {
        listMarketItem(mi.clanId(), mi.seller(), mi.itemData(), mi.price());
    }

    // ===== CLAN LEADERSHIP =====

    /**
     * Transfere a liderança em UMA transação: {@code clans.leader} + swap de roles
     * em {@code clan_members}. Se o novo líder não for membro, nada é alterado.
     */
    public boolean updateClanLeader(int clanId, UUID newLeader) {
        ClanRole defaultRole = getDefaultRole(clanId); // fora da transação (getRoles pode semear)
        Connection conn;
        try {
            conn = plugin.getDatabaseManager().getConnection();
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao atualizar lider do clan", e);
            return false;
        }
        try {
            int promoted;
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE clan_members SET role = 'lider', role_id = 0 WHERE clan_id = ? AND player_uuid = ?")) {
                ps.setInt(1, clanId);
                ps.setString(2, newLeader.toString());
                promoted = ps.executeUpdate();
            }
            if (promoted == 0) {
                conn.rollback();
                return false; // novo líder não é membro do clan
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE clan_members SET role = ?, role_id = ? WHERE clan_id = ? AND role = 'lider' AND player_uuid != ?")) {
                ps.setString(1, defaultRole != null ? defaultRole.name() : "Membro");
                ps.setInt(2, defaultRole != null ? defaultRole.id() : 0);
                ps.setInt(3, clanId);
                ps.setString(4, newLeader.toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE clans SET leader = ? WHERE id = ?")) {
                ps.setString(1, newLeader.toString());
                ps.setInt(2, clanId);
                ps.executeUpdate();
            }
            conn.commit();
            invalidateCache();
            return true;
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            plugin.getLogger().log(Level.WARNING, "Erro ao atualizar lider do clan", e);
            return false;
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    // ===== CARGOS PERSONALIZADOS =====

    /** Máximo de cargos personalizados por clan (o líder é à parte). */
    public static final int MAX_ROLES = 9;

    public record ClanRole(int id, int clanId, String name, int position,
                           boolean invite, boolean kick, boolean chest,
                           boolean market, boolean pvpToggle, boolean treasury) {}

    /**
     * Cargos do clan ordenados do mais alto para o mais baixo (position DESC).
     * Se o clan ainda não tem cargos, semeia os padrões "Vice" e "Membro" e
     * migra os membros legados ('vice'/'membro') para eles.
     */
    public List<ClanRole> getRoles(int clanId) {
        List<ClanRole> roles = queryRoles(clanId);
        if (roles.isEmpty()) {
            seedDefaultRoles(clanId);
            roles = queryRoles(clanId);
        }
        return roles;
    }

    private List<ClanRole> queryRoles(int clanId) {
        List<ClanRole> roles = new ArrayList<>();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT id, clan_id, name, position, perm_invite, perm_kick, perm_chest, perm_market, perm_pvp, perm_treasury "
                        + "FROM clan_roles WHERE clan_id = ? ORDER BY position DESC, id ASC")) {
            ps.setInt(1, clanId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) roles.add(buildRole(rs));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao buscar cargos do clan", e);
        }
        return roles;
    }

    private ClanRole buildRole(ResultSet rs) throws SQLException {
        return new ClanRole(
                rs.getInt("id"), rs.getInt("clan_id"), rs.getString("name"), rs.getInt("position"),
                rs.getInt("perm_invite") == 1, rs.getInt("perm_kick") == 1,
                rs.getInt("perm_chest") == 1, rs.getInt("perm_market") == 1,
                rs.getInt("perm_pvp") == 1, rs.getInt("perm_treasury") == 1);
    }

    /** Cria os cargos padrão e migra membros legados ('vice'/'membro') em uma transação. */
    private void seedDefaultRoles(int clanId) {
        Connection conn;
        try {
            conn = plugin.getDatabaseManager().getConnection();
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao criar cargos padrão", e);
            return;
        }
        try {
            int viceId = insertRole(conn, clanId, "Vice", 10, true, true, true, true, true, true);
            int membroId = insertRole(conn, clanId, "Membro", 0, false, false, true, true, false, false);

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE clan_members SET role = 'Vice', role_id = ? WHERE clan_id = ? AND role_id = 0 AND LOWER(role) = 'vice'")) {
                ps.setInt(1, viceId);
                ps.setInt(2, clanId);
                ps.executeUpdate();
            }
            // Catch-all: qualquer membro sem cargo (que não seja o líder) vira Membro.
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE clan_members SET role = 'Membro', role_id = ? WHERE clan_id = ? AND role_id = 0 AND role != 'lider'")) {
                ps.setInt(1, membroId);
                ps.setInt(2, clanId);
                ps.executeUpdate();
            }
            conn.commit();
            invalidateCache();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            plugin.getLogger().log(Level.WARNING, "Erro ao criar cargos padrão", e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    private int insertRole(Connection conn, int clanId, String name, int position,
                           boolean invite, boolean kick, boolean chest,
                           boolean market, boolean pvp, boolean treasury) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO clan_roles (clan_id, name, position, perm_invite, perm_kick, perm_chest, perm_market, perm_pvp, perm_treasury) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, clanId);
            ps.setString(2, name);
            ps.setInt(3, position);
            ps.setInt(4, invite ? 1 : 0);
            ps.setInt(5, kick ? 1 : 0);
            ps.setInt(6, chest ? 1 : 0);
            ps.setInt(7, market ? 1 : 0);
            ps.setInt(8, pvp ? 1 : 0);
            ps.setInt(9, treasury ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        }
    }

    public ClanRole getRole(int roleId) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT id, clan_id, name, position, perm_invite, perm_kick, perm_chest, perm_market, perm_pvp, perm_treasury "
                        + "FROM clan_roles WHERE id = ?")) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return buildRole(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao buscar cargo", e);
        }
        return null;
    }

    /** Cargo padrão do clan (o de menor position) — usado para novos membros. */
    public ClanRole getDefaultRole(int clanId) {
        List<ClanRole> roles = getRoles(clanId);
        return roles.isEmpty() ? null : roles.get(roles.size() - 1);
    }

    /** Cria um cargo novo no topo da hierarquia (abaixo do líder). Retorna o cargo ou null. */
    public ClanRole createRole(int clanId, String name) {
        String clean = sanitizeRoleName(name);
        if (clean == null) return null;
        List<ClanRole> roles = getRoles(clanId);
        if (roles.size() >= MAX_ROLES) return null;
        for (ClanRole r : roles) {
            if (r.name().equalsIgnoreCase(clean)) return null; // nome duplicado
        }
        int position = roles.isEmpty() ? 0 : roles.get(0).position() + 1;
        try {
            int id = insertRole(plugin.getDatabaseManager().getConnection(), clanId, clean, position,
                    false, false, true, true, false, false);
            return id > 0 ? getRole(id) : null;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao criar cargo", e);
            return null;
        }
    }

    /** Renomeia o cargo e sincroniza o nome exibido nos membros que o possuem. */
    public boolean renameRole(int roleId, String newName) {
        String clean = sanitizeRoleName(newName);
        if (clean == null) return false;
        ClanRole role = getRole(roleId);
        if (role == null) return false;
        for (ClanRole r : getRoles(role.clanId())) {
            if (r.id() != roleId && r.name().equalsIgnoreCase(clean)) return false;
        }
        Connection conn;
        try {
            conn = plugin.getDatabaseManager().getConnection();
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao renomear cargo", e);
            return false;
        }
        try {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE clan_roles SET name = ? WHERE id = ?")) {
                ps.setString(1, clean);
                ps.setInt(2, roleId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE clan_members SET role = ? WHERE role_id = ?")) {
                ps.setString(1, clean);
                ps.setInt(2, roleId);
                ps.executeUpdate();
            }
            conn.commit();
            invalidateCache();
            return true;
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            plugin.getLogger().log(Level.WARNING, "Erro ao renomear cargo", e);
            return false;
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /** Nome válido: 2-16 caracteres, sem < > (proteção MiniMessage). */
    private String sanitizeRoleName(String name) {
        if (name == null) return null;
        String clean = name.replace("<", "").replace(">", "").trim();
        if (clean.length() < 2 || clean.length() > 16) return null;
        if (clean.equalsIgnoreCase("lider") || clean.equalsIgnoreCase("líder")) return null;
        return clean;
    }

    /** Liga/desliga uma permissão do cargo (invite, kick, chest, market, pvp_toggle, treasury). */
    public boolean setRolePerm(int roleId, String perm, boolean value) {
        String col = switch (perm) {
            case "invite"      -> "perm_invite";
            case "kick"        -> "perm_kick";
            case "chest"       -> "perm_chest";
            case "market"      -> "perm_market";
            case "pvp_toggle"  -> "perm_pvp";
            case "treasury"    -> "perm_treasury";
            default -> null;
        };
        if (col == null) return false;
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "UPDATE clan_roles SET " + col + " = ? WHERE id = ?")) {
            ps.setInt(1, value ? 1 : 0);
            ps.setInt(2, roleId);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao alterar permissão do cargo", e);
            return false;
        }
    }

    /**
     * Exclui o cargo, movendo os membros que o possuíam para o cargo padrão.
     * Não permite excluir o último cargo do clan.
     */
    public boolean deleteRole(int clanId, int roleId) {
        List<ClanRole> roles = getRoles(clanId);
        if (roles.size() <= 1) return false;
        ClanRole target = null;
        for (ClanRole r : roles) if (r.id() == roleId) { target = r; break; }
        if (target == null) return false;

        // Cargo de fallback = padrão (menor position); se estamos excluindo ele, usa o próximo de baixo.
        ClanRole fallback = roles.get(roles.size() - 1);
        if (fallback.id() == roleId) fallback = roles.get(roles.size() - 2);

        Connection conn;
        try {
            conn = plugin.getDatabaseManager().getConnection();
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao excluir cargo", e);
            return false;
        }
        try {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE clan_members SET role = ?, role_id = ? WHERE clan_id = ? AND role_id = ?")) {
                ps.setString(1, fallback.name());
                ps.setInt(2, fallback.id());
                ps.setInt(3, clanId);
                ps.setInt(4, roleId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM clan_roles WHERE id = ? AND clan_id = ?")) {
                ps.setInt(1, roleId);
                ps.setInt(2, clanId);
                ps.executeUpdate();
            }
            conn.commit();
            invalidateCache();
            return true;
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            plugin.getLogger().log(Level.WARNING, "Erro ao excluir cargo", e);
            return false;
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /** Nome do cargo do membro ('lider' para o líder) ou null se não é membro. */
    public String getMemberRole(int clanId, UUID player) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT role FROM clan_members WHERE clan_id = ? AND player_uuid = ?")) {
            ps.setInt(1, clanId);
            ps.setString(2, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("role");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao buscar cargo do membro", e);
        }
        return null;
    }

    /** Cargo (objeto) do membro; null para o líder ou não-membro. Semeia/repara role_id se preciso. */
    public ClanRole getMemberRoleObj(int clanId, UUID player) {
        getRoles(clanId); // garante seed + migração legada
        int roleId = 0;
        String roleName = null;
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT role, role_id FROM clan_members WHERE clan_id = ? AND player_uuid = ?")) {
            ps.setInt(1, clanId);
            ps.setString(2, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                roleName = rs.getString("role");
                roleId = rs.getInt("role_id");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao buscar cargo do membro", e);
            return null;
        }
        if ("lider".equals(roleName)) return null;
        ClanRole role = roleId > 0 ? getRole(roleId) : null;
        if (role == null) {
            role = getDefaultRole(clanId);
            if (role != null) setMemberRole(clanId, player, role); // repara referência quebrada
        }
        return role;
    }

    /** Atribui um cargo a um membro. Não mexe no líder. */
    public boolean setMemberRole(int clanId, UUID player, ClanRole role) {
        if (role == null || role.clanId() != clanId) return false;
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "UPDATE clan_members SET role = ?, role_id = ? WHERE clan_id = ? AND player_uuid = ? AND role != 'lider'")) {
            ps.setString(1, role.name());
            ps.setInt(2, role.id());
            ps.setInt(3, clanId);
            ps.setString(4, player.toString());
            if (ps.executeUpdate() == 0) return false;
            invalidateCache();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao alterar cargo do membro", e);
            return false;
        }
    }

    // ===== LOGS =====

    public record ClanLog(String actor, String action, String detail, long at) {}

    /** Registra uma ação do clan de forma assíncrona (nunca bloqueia a main thread). */
    public void log(int clanId, String actor, String action, String detail) {
        long now = System.currentTimeMillis();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().newConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO clan_logs (clan_id, actor, action, detail, at) VALUES (?, ?, ?, ?, ?)")) {
                    ps.setInt(1, clanId);
                    ps.setString(2, actor != null ? actor : "");
                    ps.setString(3, action);
                    ps.setString(4, detail != null ? detail : "");
                    ps.setLong(5, now);
                    ps.executeUpdate();
                }
                // Prune: mantém apenas os 100 registros mais recentes por clan.
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM clan_logs WHERE clan_id = ? AND id NOT IN "
                                + "(SELECT id FROM clan_logs WHERE clan_id = ? ORDER BY id DESC LIMIT 100)")) {
                    ps.setInt(1, clanId);
                    ps.setInt(2, clanId);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Erro ao registrar log do clan", e);
            }
        });
    }

    /** Últimos logs do clan (mais recentes primeiro). */
    public List<ClanLog> getLogs(int clanId, int limit) {
        List<ClanLog> logs = new ArrayList<>();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT actor, action, detail, at FROM clan_logs WHERE clan_id = ? ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, clanId);
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                logs.add(new ClanLog(rs.getString("actor"), rs.getString("action"),
                        rs.getString("detail"), rs.getLong("at")));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao buscar logs do clan", e);
        }
        return logs;
    }

    // ===== PEDIDOS DE ALIANÇA =====

    /**
     * Cria um pedido de aliança de {@code fromClanId} para {@code toClanId}.
     * Se o outro clan já tinha pedido pendente para nós, a aliança é fechada
     * automaticamente (aceite mútuo).
     *
     * @return "allied" se a aliança foi formada, "requested" se o pedido ficou
     *         pendente, null em erro/inválido.
     */
    public String requestAlly(int fromClanId, int toClanId) {
        if (fromClanId == toClanId) return null;
        if (areAllied(fromClanId, toClanId)) return null;
        if (hasAllyRequest(toClanId, fromClanId)) {
            removeAllyRequest(toClanId, fromClanId);
            return addAlly(fromClanId, toClanId) ? "allied" : null;
        }
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "INSERT OR IGNORE INTO clan_ally_requests (clan_id, target_clan_id, requested_at) VALUES (?, ?, ?)")) {
            ps.setInt(1, fromClanId);
            ps.setInt(2, toClanId);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
            return "requested";
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao solicitar aliança", e);
            return null;
        }
    }

    public boolean hasAllyRequest(int fromClanId, int toClanId) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT 1 FROM clan_ally_requests WHERE clan_id = ? AND target_clan_id = ?")) {
            ps.setInt(1, fromClanId);
            ps.setInt(2, toClanId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            return false;
        }
    }

    public void removeAllyRequest(int fromClanId, int toClanId) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "DELETE FROM clan_ally_requests WHERE clan_id = ? AND target_clan_id = ?")) {
            ps.setInt(1, fromClanId);
            ps.setInt(2, toClanId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao remover pedido de aliança", e);
        }
    }

    /** Clans que enviaram pedido de aliança para este clan. */
    public List<Clan> getIncomingAllyRequests(int clanId) {
        List<Clan> requests = new ArrayList<>();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT c.id, c.tag, c.name, c.leader, c.color, c.public, c.created, c.friendly_fire, c.ally_ff, c.description "
                        + "FROM clan_ally_requests r JOIN clans c ON c.id = r.clan_id WHERE r.target_clan_id = ? ORDER BY r.requested_at ASC")) {
            ps.setInt(1, clanId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) requests.add(buildClanFromResultSet(rs));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao buscar pedidos de aliança", e);
        }
        return requests;
    }

    // ===== ALLY FF / DESCRIÇÃO =====

    /** Liga/desliga PvP contra clans aliados (ally_ff). */
    public boolean setAllyFriendlyFire(int clanId, boolean enabled) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "UPDATE clans SET ally_ff = ? WHERE id = ?")) {
            ps.setInt(1, enabled ? 1 : 0);
            ps.setInt(2, clanId);
            ps.executeUpdate();
            invalidateCache();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao alterar ally friendly fire", e);
            return false;
        }
    }

    public boolean setClanDescription(int clanId, String description) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "UPDATE clans SET description = ? WHERE id = ?")) {
            ps.setString(1, description != null ? description : "");
            ps.setInt(2, clanId);
            ps.executeUpdate();
            invalidateCache();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao alterar descrição do clan", e);
            return false;
        }
    }
}