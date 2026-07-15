package com.psdk.cage;

import org.bukkit.Location;

import java.util.List;
import java.util.UUID;

/**
 * Modelo de uma Jaula ativa (item especial). Cubo de 8x8x8 alinhado aos eixos,
 * com casca de vidro vermelho e interior oco 6x6x6.
 *
 * <p>Guarda tudo o que o {@link CageManager} precisa para operar e limpar:
 * ID único, dono, mundo, limites, contador de tentativas, estado ativo, momento
 * de criação e a lista de blocos temporários (com o estado ORIGINAL para restaurar).
 */
public class Cage {

    /** Um bloco da casca: posição + BlockData original (string) para restauração. */
    public record TempBlock(int x, int y, int z, String originalData) {}

    private final UUID id;
    private final UUID owner;
    private final String world;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;
    private final long createdAt;
    private final List<TempBlock> tempBlocks;

    private int hits = 0;
    private boolean active = true;

    public Cage(UUID id, UUID owner, String world,
                int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                long createdAt, List<TempBlock> tempBlocks) {
        this.id = id;
        this.owner = owner;
        this.world = world;
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        this.createdAt = createdAt;
        this.tempBlocks = tempBlocks;
    }

    public UUID getId()               { return id; }
    public UUID getOwner()            { return owner; }
    public String getWorld()          { return world; }
    public int getMinX()              { return minX; }
    public int getMinY()              { return minY; }
    public int getMinZ()              { return minZ; }
    public int getMaxX()              { return maxX; }
    public int getMaxY()              { return maxY; }
    public int getMaxZ()              { return maxZ; }
    public long getCreatedAt()        { return createdAt; }
    public List<TempBlock> getTempBlocks() { return tempBlocks; }

    public int getHits()              { return hits; }
    public void addHit()              { hits++; }
    public boolean isActive()         { return active; }
    public void setActive(boolean a)  { active = a; }

    /** Centro geométrico do cubo (para efeitos). */
    public double centerX() { return (minX + maxX + 1) / 2.0; }
    public double centerY() { return (minY + maxY + 1) / 2.0; }
    public double centerZ() { return (minZ + maxZ + 1) / 2.0; }

    /** True se o ponto está no INTERIOR oco (estritamente dentro da casca). */
    public boolean isInsideInterior(int x, int y, int z) {
        return x > minX && x < maxX
                && y > minY && y < maxY
                && z > minZ && z < maxZ;
    }

    /** True se a localização está no interior desta Jaula (mesmo mundo). */
    public boolean isInsideInterior(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(world)) return false;
        return isInsideInterior(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /** True se o bounding box (inclusive a casca) desta Jaula intercepta outro. */
    public boolean intersectsBox(String otherWorld,
                                 int oMinX, int oMinY, int oMinZ,
                                 int oMaxX, int oMaxY, int oMaxZ) {
        if (!world.equals(otherWorld)) return false;
        return minX <= oMaxX && maxX >= oMinX
                && minY <= oMaxY && maxY >= oMinY
                && minZ <= oMaxZ && maxZ >= oMinZ;
    }
}
