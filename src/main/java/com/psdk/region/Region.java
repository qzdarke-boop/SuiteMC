package com.psdk.region;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.EnumMap;
import java.util.Map;

public class Region {

    private String name;
    private String world;
    private int x1, y1, z1, x2, y2, z2;
    private int minX, maxX, minY, maxY, minZ, maxZ;
    private int priority;
    private final Map<RegionFlag, Boolean> flags = new EnumMap<>(RegionFlag.class);

    private String entryTpWorld;
    private double entryTpX, entryTpY, entryTpZ;
    private float entryTpYaw, entryTpPitch;

    private String exitTpWorld;
    private double exitTpX, exitTpY, exitTpZ;
    private float exitTpYaw, exitTpPitch;

    public Region(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.name = name;
        this.world = world;
        this.x1 = x1; this.y1 = y1; this.z1 = z1;
        this.x2 = x2; this.y2 = y2; this.z2 = z2;
        this.priority = 0;
        recomputeBounds();
    }

    private void recomputeBounds() {
        minX = Math.min(x1, x2); maxX = Math.max(x1, x2);
        minY = Math.min(y1, y2); maxY = Math.max(y1, y2);
        minZ = Math.min(z1, z2); maxZ = Math.max(z1, z2);
    }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(world)) return false;
        int lx = loc.getBlockX(), ly = loc.getBlockY(), lz = loc.getBlockZ();
        return lx >= minX && lx <= maxX && ly >= minY && ly <= maxY && lz >= minZ && lz <= maxZ;
    }

    public boolean isAllowed(RegionFlag flag) {
        return flags.getOrDefault(flag, true);
    }

    public void setFlag(RegionFlag flag, boolean allowed) {
        flags.put(flag, allowed);
    }

    // --- Entry teleport ---

    public boolean hasEntryTp() {
        return entryTpWorld != null;
    }

    public Location getEntryTpLocation() {
        if (entryTpWorld == null) return null;
        World w = Bukkit.getWorld(entryTpWorld);
        if (w == null) return null;
        return new Location(w, entryTpX, entryTpY, entryTpZ, entryTpYaw, entryTpPitch);
    }

    public void setEntryTp(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        this.entryTpWorld = loc.getWorld().getName();
        this.entryTpX = loc.getX();
        this.entryTpY = loc.getY();
        this.entryTpZ = loc.getZ();
        this.entryTpYaw = loc.getYaw();
        this.entryTpPitch = loc.getPitch();
    }

    public void clearEntryTp() {
        this.entryTpWorld = null;
        this.entryTpX = 0;
        this.entryTpY = 0;
        this.entryTpZ = 0;
        this.entryTpYaw = 0;
        this.entryTpPitch = 0;
    }

    // --- Exit teleport ---

    public boolean hasExitTp() {
        return exitTpWorld != null;
    }

    public Location getExitTpLocation() {
        if (exitTpWorld == null) return null;
        World w = Bukkit.getWorld(exitTpWorld);
        if (w == null) return null;
        return new Location(w, exitTpX, exitTpY, exitTpZ, exitTpYaw, exitTpPitch);
    }

    public void setExitTp(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        this.exitTpWorld = loc.getWorld().getName();
        this.exitTpX = loc.getX();
        this.exitTpY = loc.getY();
        this.exitTpZ = loc.getZ();
        this.exitTpYaw = loc.getYaw();
        this.exitTpPitch = loc.getPitch();
    }

    public void clearExitTp() {
        this.exitTpWorld = null;
        this.exitTpX = 0;
        this.exitTpY = 0;
        this.exitTpZ = 0;
        this.exitTpYaw = 0;
        this.exitTpPitch = 0;
    }

    // --- Flags serialization ---

    public String serializeFlags() {
        if (flags.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<RegionFlag, Boolean> entry : flags.entrySet()) {
            if (!sb.isEmpty()) sb.append(',');
            sb.append(entry.getKey().name()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    public static Map<RegionFlag, Boolean> deserializeFlags(String raw) {
        Map<RegionFlag, Boolean> map = new EnumMap<>(RegionFlag.class);
        if (raw == null || raw.isBlank()) return map;
        for (String pair : raw.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length != 2) continue;
            try {
                map.put(RegionFlag.valueOf(kv[0].trim()), Boolean.parseBoolean(kv[1].trim()));
            } catch (IllegalArgumentException ignored) {}
        }
        return map;
    }

    // --- Getters / Setters ---

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }

    public int getX1() { return x1; }
    public void setX1(int x1) { this.x1 = x1; recomputeBounds(); }
    public int getY1() { return y1; }
    public void setY1(int y1) { this.y1 = y1; recomputeBounds(); }
    public int getZ1() { return z1; }
    public void setZ1(int z1) { this.z1 = z1; recomputeBounds(); }

    public int getX2() { return x2; }
    public void setX2(int x2) { this.x2 = x2; recomputeBounds(); }
    public int getY2() { return y2; }
    public void setY2(int y2) { this.y2 = y2; recomputeBounds(); }
    public int getZ2() { return z2; }
    public void setZ2(int z2) { this.z2 = z2; recomputeBounds(); }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public Map<RegionFlag, Boolean> getFlags() { return flags; }

    public String getEntryTpWorld() { return entryTpWorld; }
    public void setEntryTpWorld(String entryTpWorld) { this.entryTpWorld = entryTpWorld; }
    public double getEntryTpX() { return entryTpX; }
    public void setEntryTpX(double entryTpX) { this.entryTpX = entryTpX; }
    public double getEntryTpY() { return entryTpY; }
    public void setEntryTpY(double entryTpY) { this.entryTpY = entryTpY; }
    public double getEntryTpZ() { return entryTpZ; }
    public void setEntryTpZ(double entryTpZ) { this.entryTpZ = entryTpZ; }
    public float getEntryTpYaw() { return entryTpYaw; }
    public void setEntryTpYaw(float entryTpYaw) { this.entryTpYaw = entryTpYaw; }
    public float getEntryTpPitch() { return entryTpPitch; }
    public void setEntryTpPitch(float entryTpPitch) { this.entryTpPitch = entryTpPitch; }

    public String getExitTpWorld() { return exitTpWorld; }
    public void setExitTpWorld(String exitTpWorld) { this.exitTpWorld = exitTpWorld; }
    public double getExitTpX() { return exitTpX; }
    public void setExitTpX(double exitTpX) { this.exitTpX = exitTpX; }
    public double getExitTpY() { return exitTpY; }
    public void setExitTpY(double exitTpY) { this.exitTpY = exitTpY; }
    public double getExitTpZ() { return exitTpZ; }
    public void setExitTpZ(double exitTpZ) { this.exitTpZ = exitTpZ; }
    public float getExitTpYaw() { return exitTpYaw; }
    public void setExitTpYaw(float exitTpYaw) { this.exitTpYaw = exitTpYaw; }
    public float getExitTpPitch() { return exitTpPitch; }
    public void setExitTpPitch(float exitTpPitch) { this.exitTpPitch = exitTpPitch; }
}
