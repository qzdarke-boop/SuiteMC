package com.psdk.thepit.topboard;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public final class TopBoard {

    private final String id;
    private final TopBoardType type;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private UUID interactionUuid;

    public TopBoard(String id, TopBoardType type, Location loc) {
        this.id = id;
        this.type = type;
        this.worldName = loc.getWorld().getName();
        this.x = loc.getX();
        this.y = loc.getY();
        this.z = loc.getZ();
    }

    public String getId() { return id; }
    public TopBoardType getType() { return type; }
    public UUID getInteractionUuid() { return interactionUuid; }

    public void setInteractionUuid(UUID interactionUuid) { this.interactionUuid = interactionUuid; }

    public Location getLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z);
    }
}
