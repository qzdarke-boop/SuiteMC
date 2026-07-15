package com.psdk.clan;

import org.bukkit.Material;

import java.util.List;

public class ClanColor {

    private String name;
    private String colorHex;
    private String displayName;
    private List<String> lore;
    private Material keyMaterial;
    private String keyDisplayName;
    private List<String> keyLore;
    private boolean animationEnabled;
    private String animationStyle; // PULSE, WAVE, GRADIENT, etc.
    private List<String> items; // Base64 encoded ItemStacks

    public ClanColor() {}

    public ClanColor(String name, String colorHex, String displayName) {
        this.name = name;
        this.colorHex = colorHex;
        this.displayName = displayName;
        this.animationEnabled = true;
        this.animationStyle = "PULSE";
        this.keyMaterial = Material.TRIPWIRE_HOOK;
        this.keyDisplayName = "Chave de Cor";
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public List<String> getLore() { return lore; }
    public void setLore(List<String> lore) { this.lore = lore; }

    public Material getKeyMaterial() { return keyMaterial; }
    public void setKeyMaterial(Material keyMaterial) { this.keyMaterial = keyMaterial; }

    public String getKeyDisplayName() { return keyDisplayName; }
    public void setKeyDisplayName(String keyDisplayName) { this.keyDisplayName = keyDisplayName; }

    public List<String> getKeyLore() { return keyLore; }
    public void setKeyLore(List<String> keyLore) { this.keyLore = keyLore; }

    public boolean isAnimationEnabled() { return animationEnabled; }
    public void setAnimationEnabled(boolean animationEnabled) { this.animationEnabled = animationEnabled; }

    public String getAnimationStyle() { return animationStyle; }
    public void setAnimationStyle(String animationStyle) { this.animationStyle = animationStyle; }

    public List<String> getItems() { return items; }
    public void setItems(List<String> items) { this.items = items; }
}
