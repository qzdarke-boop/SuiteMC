package com.psdk.bounty;

import com.psdk.PSDK;
import com.psdk.util.NumberUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Sistema de recompensas por abate (bounty).
 * Cada jogador-alvo possui um valor acumulado em coins; quem o eliminar leva o total.
 * Persistência síncrona em SQLite (mesma thread do servidor) + cache em memória.
 */
public class BountyManager {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    public record Bounty(UUID target, String name, double amount) {}

    public record ResolvedPlayer(UUID uuid, String name) {}

    private final PSDK plugin;
    private final java.util.Map<UUID, Bounty> bounties = new ConcurrentHashMap<>();

    public BountyManager(PSDK plugin) {
        this.plugin = plugin;
        loadAll();
    }

    /** Formata um valor de recompensa: $1, $0, $38.5M. */
    public static String fmt(double amount) {
        return "$" + NumberUtil.abbrev(amount);
    }

    private void loadAll() {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection()
                .prepareStatement("SELECT target_uuid, target_name, amount FROM bounties");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("target_uuid"));
                    double amount = rs.getDouble("amount");
                    if (amount <= 0) continue;
                    bounties.put(uuid, new Bounty(uuid, rs.getString("target_name"), amount));
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Bounty] Erro ao carregar recompensas", e);
        }
    }

    public double getAmount(UUID target) {
        Bounty b = bounties.get(target);
        return b == null ? 0 : b.amount();
    }

    public Bounty get(UUID target) {
        return bounties.get(target);
    }

    /** Lista de recompensas ativas ordenada da MAIOR para a MENOR. */
    public List<Bounty> getSortedDescending() {
        List<Bounty> list = new ArrayList<>(bounties.values());
        list.sort(Comparator.comparingDouble(Bounty::amount).reversed());
        return list;
    }

    /**
     * Adiciona (acumula) recompensa ao alvo e persiste.
     * Retorna {@code false} se o valor for inválido ou a persistência falhar
     * (nesse caso o cache é revertido, para o chamador poder reembolsar coins).
     */
    public boolean addBounty(UUID target, String name, double amount) {
        if (amount <= 0 || !Double.isFinite(amount)) return false;
        double anterior = getAmount(target);
        double novo = anterior + amount;
        bounties.put(target, new Bounty(target, name, novo));
        if (!saveToDB(target, name, novo)) {
            // Reverte o cache ao estado anterior para não divergir do banco.
            if (anterior <= 0) {
                bounties.remove(target);
            } else {
                bounties.put(target, new Bounty(target, name, anterior));
            }
            return false;
        }
        notifyTargetClan(target, name, novo);
        return true;
    }

    /** Avisa os membros online do clan do alvo que há recompensa pela cabeça dele. */
    private void notifyTargetClan(UUID target, String name, double total) {
        com.psdk.clan.Clan clan = plugin.getClanManager().getClanByPlayer(target);
        if (clan == null) return;
        for (com.psdk.clan.ClanMember member : clan.getMembers()) {
            if (member.uuid().equals(target)) continue;
            Player online = Bukkit.getPlayer(member.uuid());
            if (online != null) {
                online.sendMessage(mm.deserialize(
                        "<#fcc850><bold>☠ <reset><#cbd1d7>A cabeça de <#fcc850>" + name
                        + " <#cbd1d7>(seu clan) agora vale <#fcc850>" + fmt(total) + "<#cbd1d7>!"));
            }
        }
    }

    /** Remove totalmente a recompensa do alvo (cache + banco). */
    public void removeBounty(UUID target) {
        bounties.remove(target);
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection()
                .prepareStatement("DELETE FROM bounties WHERE target_uuid = ?")) {
            ps.setString(1, target.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Bounty] Erro ao remover recompensa", e);
        }
    }

    private boolean saveToDB(UUID target, String name, double amount) {
        String sql = "INSERT INTO bounties (target_uuid, target_name, amount) VALUES (?, ?, ?) " +
                "ON CONFLICT(target_uuid) DO UPDATE SET amount = excluded.amount, target_name = excluded.target_name";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, target.toString());
            ps.setString(2, name);
            ps.setDouble(3, amount);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Bounty] Erro ao salvar recompensa", e);
            return false;
        }
    }

    /** Resolve um jogador por nome: primeiro online, depois no banco de economia (offline). */
    public ResolvedPlayer resolveByName(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return new ResolvedPlayer(online.getUniqueId(), online.getName());

        ResolvedPlayer found = queryName("player_economy", name);
        if (found == null) found = queryName("player_data", name);
        return found;
    }

    private ResolvedPlayer queryName(String table, String name) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection()
                .prepareStatement("SELECT uuid, name FROM " + table + " WHERE name = ? COLLATE NOCASE LIMIT 1")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new ResolvedPlayer(UUID.fromString(rs.getString("uuid")), rs.getString("name"));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Bounty] Erro ao resolver jogador em " + table, e);
        }
        return null;
    }

    /**
     * Chamado quando {@code killer} elimina {@code victim}.
     * Se a vítima tinha recompensa, o killer recebe os coins e a recompensa é zerada.
     */
    public void handleKill(Player killer, Player victim) {
        Bounty b = bounties.get(victim.getUniqueId());
        if (b == null || b.amount() <= 0) return;

        double amount = b.amount();
        removeBounty(victim.getUniqueId());
        plugin.getEconomyManager().addCoins(killer.getUniqueId(), killer.getName(), amount);

        killer.sendMessage(mm.deserialize("<#10fc46>Você abateu <#fcc850>" + victim.getName()
                + " <#10fc46>e recebeu a recompensa de <#fcc850>" + fmt(amount) + "<#10fc46>!"));
        killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);

        plugin.getServer().sendMessage(mm.deserialize(
                "<#fcc850><bold>☠ <reset><#cbd1d7>O jogador <#fcc850>" + killer.getName()
                + " <#cbd1d7>coletou a recompensa de <#fcc850>" + fmt(amount)
                + " <#cbd1d7>pela cabeça de <#fcc850>" + victim.getName() + "<#cbd1d7>!"));
    }
}
