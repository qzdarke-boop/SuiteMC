package com.psdk.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

public final class SkullUtils {

    private SkullUtils() {}

    public static ItemStack createTextured(String base64Texture) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (base64Texture == null || base64Texture.isEmpty()) return head;

        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;

        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
        profile.setProperty(new ProfileProperty("textures", base64Texture));
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }

    public static ItemStack createOwned(OfflinePlayer player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            head.setItemMeta(meta);
        }
        return head;
    }

    public static void applyTexture(SkullMeta meta, String base64Texture) {
        if (base64Texture == null || base64Texture.isEmpty()) return;
        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
        profile.setProperty(new ProfileProperty("textures", base64Texture));
        meta.setPlayerProfile(profile);
    }
}
