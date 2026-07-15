package com.psdk.thepit;

import java.util.UUID;

public class PlayerData {

    private final UUID uuid;
    private String name;
    private int level;
    private long xp;
    private int kills;
    private int deaths;
    private long blocksPlaced;
    private long blocksBroken;
    private boolean dirty;

    public PlayerData(UUID uuid, String name) {
        this(uuid, name, 1, 0, 0, 0, 0, 0);
    }

    public PlayerData(UUID uuid, String name, int level, long xp, int kills, int deaths) {
        this(uuid, name, level, xp, kills, deaths, 0, 0);
    }

    public PlayerData(UUID uuid, String name, int level, long xp, int kills, int deaths,
                      long blocksPlaced, long blocksBroken) {
        this.uuid = uuid;
        this.name = name;
        this.level = level;
        this.xp = xp;
        this.kills = kills;
        this.deaths = deaths;
        this.blocksPlaced = blocksPlaced;
        this.blocksBroken = blocksBroken;
        this.dirty = false;
    }

    public UUID getUuid() { return uuid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; markDirty(); }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = Math.max(1, level); markDirty(); }

    public long getXp() { return xp; }
    public void setXp(long xp) { this.xp = Math.max(0, xp); markDirty(); }
    public void addXp(long amount) { this.xp += amount; markDirty(); }

    public int getKills() { return kills; }
    public void setKills(int kills) { this.kills = Math.max(0, kills); markDirty(); }
    public void addKill() { this.kills++; markDirty(); }

    public int getDeaths() { return deaths; }
    public void setDeaths(int deaths) { this.deaths = Math.max(0, deaths); markDirty(); }
    public void addDeath() { this.deaths++; markDirty(); }

    public long getBlocksPlaced() { return blocksPlaced; }
    public void setBlocksPlaced(long v) { this.blocksPlaced = Math.max(0, v); markDirty(); }
    public void addBlockPlaced() { this.blocksPlaced++; markDirty(); }

    public long getBlocksBroken() { return blocksBroken; }
    public void setBlocksBroken(long v) { this.blocksBroken = Math.max(0, v); markDirty(); }
    public void addBlockBroken() { this.blocksBroken++; markDirty(); }

    public double getKdr() {
        return deaths == 0 ? kills : (double) kills / deaths;
    }

    public boolean isDirty() { return dirty; }
    public void markDirty() { this.dirty = true; }
    public void markClean() { this.dirty = false; }
}
