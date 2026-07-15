package com.psdk.thepit;

import org.bukkit.Location;

public class ArenaData {

    private Location pos1;
    private Location pos2;
    private boolean defined;
    private int blocksInDatabase;

    public ArenaData() {
        this.defined = false;
        this.blocksInDatabase = 0;
    }

    public Location getPos1() { return pos1; }
    public void setPos1(Location pos1) { this.pos1 = pos1; }

    public Location getPos2() { return pos2; }
    public void setPos2(Location pos2) { this.pos2 = pos2; }

    public boolean isDefined() { return defined; }
    public void setDefined(boolean defined) { this.defined = defined; }

    public int getBlocksInDatabase() { return blocksInDatabase; }
    public void setBlocksInDatabase(int count) { this.blocksInDatabase = count; }

    public boolean hasBothPositions() { return pos1 != null && pos2 != null; }

    public int getMinX() { return Math.min(pos1.getBlockX(), pos2.getBlockX()); }
    public int getMaxX() { return Math.max(pos1.getBlockX(), pos2.getBlockX()); }
    public int getMinY() { return Math.min(pos1.getBlockY(), pos2.getBlockY()); }
    public int getMaxY() { return Math.max(pos1.getBlockY(), pos2.getBlockY()); }
    public int getMinZ() { return Math.min(pos1.getBlockZ(), pos2.getBlockZ()); }
    public int getMaxZ() { return Math.max(pos1.getBlockZ(), pos2.getBlockZ()); }

    public long getVolume() {
        if (!hasBothPositions()) return 0;
        return (long) (getMaxX() - getMinX() + 1)
                * (getMaxY() - getMinY() + 1)
                * (getMaxZ() - getMinZ() + 1);
    }

    public static class BlockPosition {
        private final int x, y, z;

        public BlockPosition(int x, int y, int z) {
            this.x = x; this.y = y; this.z = z;
        }

        public int getX() { return x; }
        public int getY() { return y; }
        public int getZ() { return z; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BlockPosition other)) return false;
            return x == other.x && y == other.y && z == other.z;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }

        @Override
        public String toString() { return x + "," + y + "," + z; }

        public static BlockPosition fromString(String s) {
            String[] parts = s.split(",");
            return new BlockPosition(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        }
    }
}
