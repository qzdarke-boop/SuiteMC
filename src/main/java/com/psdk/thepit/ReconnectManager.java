package com.psdk.thepit;

import com.psdk.PSDK;
import com.psdk.region.RegionFlag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Guarda e restaura a localização de reconexão do Skill Pit.
 *
 * <p>Regra (somente quando o jogador sai SEM Combat Log):
 * <ul>
 *   <li>Saiu na <b>arena ativa de PvP</b> → volta EXATAMENTE para a posição salva
 *       (se ainda for válida; senão, spawn).</li>
 *   <li>Saiu na <b>área segura</b> → volta para o spawn do Skill Pit.</li>
 * </ul>
 * A verificação de área usa o sistema oficial de regiões/arena, não coordenadas fixas.
 * A posição é persistida (tabela {@code pit_reconnect}) para sobreviver a reload/restart.
 */
public class ReconnectManager {

    /** Snapshot da localização de saída. */
    public record Saved(String world, double x, double y, double z,
                        float yaw, float pitch, boolean inPvp) {}

    private final PSDK plugin;

    public ReconnectManager(PSDK plugin) {
        this.plugin = plugin;
    }

    /** True se o local é a área ATIVA de PvP (dentro da arena e com PvP liberado). */
    public boolean isPvpArea(Location loc) {
        if (loc == null) return false;
        return plugin.getArenaManager().isInsideArena(loc)
                && plugin.getRegionManager().isAllowed(loc, RegionFlag.PVP);
    }

    /** Salva a posição de saída do jogador junto com a informação de região. */
    public void save(Player player) {
        Location loc = player.getLocation();
        if (loc == null || loc.getWorld() == null) return;
        boolean inPvp = isPvpArea(loc);
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO pit_reconnect (uuid, world, x, y, z, yaw, pitch, in_pvp) "
                            + "VALUES (?,?,?,?,?,?,?,?)")) {
                ps.setString(1, player.getUniqueId().toString());
                ps.setString(2, loc.getWorld().getName());
                ps.setDouble(3, loc.getX());
                ps.setDouble(4, loc.getY());
                ps.setDouble(5, loc.getZ());
                ps.setFloat(6, loc.getYaw());
                ps.setFloat(7, loc.getPitch());
                ps.setInt(8, inPvp ? 1 : 0);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao salvar reconexão de " + player.getName(), e);
        }
    }

    /** Lê e apaga (one-shot) a posição salva. */
    public Saved consume(UUID id) {
        Saved saved = null;
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT world, x, y, z, yaw, pitch, in_pvp FROM pit_reconnect WHERE uuid = ?")) {
                ps.setString(1, id.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    saved = new Saved(rs.getString("world"), rs.getDouble("x"), rs.getDouble("y"),
                            rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"),
                            rs.getInt("in_pvp") == 1);
                }
            }
            clear(id);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao ler reconexão de " + id, e);
        }
        return saved;
    }

    public void clear(UUID id) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM pit_reconnect WHERE uuid = ?")) {
                ps.setString(1, id.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao limpar reconexão de " + id, e);
        }
    }

    /**
     * Destino de reconexão do jogador segundo as regras. Nunca retorna posição
     * inválida: usa o spawn como fallback.
     */
    public Location resolveTarget(UUID id) {
        Location spawn = plugin.getSpawnLocation();
        Saved s = consume(id);
        if (s == null) return spawn;          // sem registro → spawn (padrão)
        if (!s.inPvp()) return spawn;         // área segura → spawn

        World w = Bukkit.getWorld(s.world());
        if (w == null) return spawn;
        Location loc = new Location(w, s.x(), s.y(), s.z(), s.yaw(), s.pitch());
        return isValidPvpRestore(loc) ? loc : spawn;
    }

    /** Valida se a posição salva na arena de PvP ainda é segura para restaurar. */
    private boolean isValidPvpRestore(Location loc) {
        World w = loc.getWorld();
        if (w == null) return false;
        // A arena precisa continuar existindo e a posição continuar sendo área de PvP.
        if (!plugin.getArenaManager().isInsideArena(loc)) return false;
        if (!plugin.getRegionManager().isAllowed(loc, RegionFlag.PVP)) return false;
        // Não pode estar no vazio nem fora dos limites do mundo.
        if (loc.getBlockY() <= w.getMinHeight() || loc.getBlockY() >= w.getMaxHeight()) return false;
        // Não pode sufocar (pés e cabeça precisam estar livres).
        Block feet = loc.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        if (feet.getType().isSolid() || head.getType().isSolid()) return false;
        return true;
    }
}
