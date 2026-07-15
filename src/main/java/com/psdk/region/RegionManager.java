package com.psdk.region;

import com.psdk.PSDK;
import org.bukkit.Location;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class RegionManager {

    private final PSDK plugin;
    private final Map<String, Region> regions = new LinkedHashMap<>();
    private final Map<UUID, Location[]> selections = new HashMap<>();

    public RegionManager(PSDK plugin) {
        this.plugin = plugin;
        loadAll();
    }

    private void loadAll() {
        regions.clear();
        try (Statement stmt = plugin.getDatabaseManager().getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM regions")) {
            while (rs.next()) {
                Region r = new Region(
                        rs.getString("name"),
                        rs.getString("world"),
                        rs.getInt("x1"), rs.getInt("y1"), rs.getInt("z1"),
                        rs.getInt("x2"), rs.getInt("y2"), rs.getInt("z2")
                );
                r.setPriority(rs.getInt("priority"));
                for (Map.Entry<RegionFlag, Boolean> e : Region.deserializeFlags(rs.getString("flags")).entrySet()) {
                    r.setFlag(e.getKey(), e.getValue());
                }

                String entryWorld = rs.getString("entry_tp_world");
                if (entryWorld != null && !entryWorld.isEmpty()) {
                    r.setEntryTpWorld(entryWorld);
                    r.setEntryTpX(rs.getDouble("entry_tp_x"));
                    r.setEntryTpY(rs.getDouble("entry_tp_y"));
                    r.setEntryTpZ(rs.getDouble("entry_tp_z"));
                    r.setEntryTpYaw(rs.getFloat("entry_tp_yaw"));
                    r.setEntryTpPitch(rs.getFloat("entry_tp_pitch"));
                }

                String exitWorld = rs.getString("exit_tp_world");
                if (exitWorld != null && !exitWorld.isEmpty()) {
                    r.setExitTpWorld(exitWorld);
                    r.setExitTpX(rs.getDouble("exit_tp_x"));
                    r.setExitTpY(rs.getDouble("exit_tp_y"));
                    r.setExitTpZ(rs.getDouble("exit_tp_z"));
                    r.setExitTpYaw(rs.getFloat("exit_tp_yaw"));
                    r.setExitTpPitch(rs.getFloat("exit_tp_pitch"));
                }

                regions.put(r.getName().toLowerCase(), r);
            }
            plugin.getLogger().info("Regioes carregadas: " + regions.size());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao carregar regioes", e);
        }
    }

    public void saveRegion(Region region) {
        String sql = """
                INSERT OR REPLACE INTO regions
                (name, world, x1, y1, z1, x2, y2, z2, priority, flags,
                 entry_tp_world, entry_tp_x, entry_tp_y, entry_tp_z, entry_tp_yaw, entry_tp_pitch,
                 exit_tp_world, exit_tp_x, exit_tp_y, exit_tp_z, exit_tp_yaw, exit_tp_pitch)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, region.getName());
            ps.setString(2, region.getWorld());
            ps.setInt(3, region.getX1());
            ps.setInt(4, region.getY1());
            ps.setInt(5, region.getZ1());
            ps.setInt(6, region.getX2());
            ps.setInt(7, region.getY2());
            ps.setInt(8, region.getZ2());
            ps.setInt(9, region.getPriority());
            ps.setString(10, region.serializeFlags());
            ps.setString(11, region.getEntryTpWorld());
            ps.setDouble(12, region.getEntryTpX());
            ps.setDouble(13, region.getEntryTpY());
            ps.setDouble(14, region.getEntryTpZ());
            ps.setFloat(15, region.getEntryTpYaw());
            ps.setFloat(16, region.getEntryTpPitch());
            ps.setString(17, region.getExitTpWorld());
            ps.setDouble(18, region.getExitTpX());
            ps.setDouble(19, region.getExitTpY());
            ps.setDouble(20, region.getExitTpZ());
            ps.setFloat(21, region.getExitTpYaw());
            ps.setFloat(22, region.getExitTpPitch());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao salvar regiao " + region.getName(), e);
        }
        regions.put(region.getName().toLowerCase(), region);
    }

    public void deleteRegion(String name) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection()
                .prepareStatement("DELETE FROM regions WHERE name = ?")) {
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao deletar regiao " + name, e);
        }
        regions.remove(name.toLowerCase());
    }

    // --- Query ---

    public List<Region> getRegionsAt(Location loc) {
        List<Region> result = new ArrayList<>();
        for (Region r : regions.values()) {
            if (r.contains(loc)) result.add(r);
        }
        result.sort(Comparator.comparingInt(Region::getPriority).reversed());
        return result;
    }

    /** Returns the highest-priority region at loc, or null if none. No allocation. */
    private Region highestAt(Location loc) {
        Region best = null;
        for (Region r : regions.values()) {
            if (r.contains(loc) && (best == null || r.getPriority() > best.getPriority())) {
                best = r;
            }
        }
        return best;
    }

    public boolean isAllowed(Location loc, RegionFlag flag) {
        Region r = highestAt(loc);
        return r == null || r.isAllowed(flag);
    }

    public Region getRegion(String name) {
        return regions.get(name.toLowerCase());
    }

    public Collection<Region> getAllRegions() {
        return regions.values();
    }

    public boolean hasRegion(String name) {
        return regions.containsKey(name.toLowerCase());
    }

    // --- Selections ---

    public void setPos1(UUID uuid, Location loc) {
        selections.computeIfAbsent(uuid, k -> new Location[2])[0] = loc;
    }

    public void setPos2(UUID uuid, Location loc) {
        selections.computeIfAbsent(uuid, k -> new Location[2])[1] = loc;
    }

    public Location getPos1(UUID uuid) {
        Location[] sel = selections.get(uuid);
        return sel != null ? sel[0] : null;
    }

    public Location getPos2(UUID uuid) {
        Location[] sel = selections.get(uuid);
        return sel != null ? sel[1] : null;
    }

    public boolean hasBothPositions(UUID uuid) {
        Location[] sel = selections.get(uuid);
        return sel != null && sel[0] != null && sel[1] != null;
    }
}
