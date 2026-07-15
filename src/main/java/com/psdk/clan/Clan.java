package com.psdk.clan;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Clan {

    private int id;
    private String tag;
    private String name;
    private UUID leader;
    private String colorHex;
    private long createdAt;
    private boolean isPublic;
    private boolean friendlyFire;
    private boolean allyFriendlyFire;
    private String description = "";
    private List<ClanMember> members;

    public Clan(int id, String tag, String name, UUID leader, String colorHex, long createdAt, boolean isPublic) {
        this.id = id;
        this.tag = tag;
        this.name = name;
        this.leader = leader;
        this.colorHex = colorHex;
        this.createdAt = createdAt;
        this.isPublic = isPublic;
        this.friendlyFire = false;
        this.members = new ArrayList<>();
    }

    public int getId() { return id; }
    public String getTag() { return tag; }
    public String getName() { return name; }
    public UUID getLeader() { return leader; }
    public String getColorHex() { return colorHex; }
    public long getCreatedAt() { return createdAt; }
    public List<ClanMember> getMembers() { return members; }

    public boolean isPublic() { return isPublic; }
    public boolean isFriendlyFire() { return friendlyFire; }
    public boolean isAllyFriendlyFire() { return allyFriendlyFire; }
    public void setAllyFriendlyFire(boolean allyFriendlyFire) { this.allyFriendlyFire = allyFriendlyFire; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description != null ? description : ""; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }
    public void setFriendlyFire(boolean friendlyFire) { this.friendlyFire = friendlyFire; }
    public void setMembers(List<ClanMember> members) { this.members = members; }
    public void setLeader(UUID leader) { this.leader = leader; }
}
