package com.psdk.pitems;

import com.psdk.PSDK;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class PSDKItemExpireTask extends BukkitRunnable {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final long LORE_UPDATE_THRESHOLD = 3600000L; // 1 hour
    private static final long LORE_UPDATE_INTERVAL = 120000L;   // 2 minutes

    private final PSDK plugin;
    private long lastLoreUpdate;

    public PSDKItemExpireTask(PSDK plugin) {
        this.plugin = plugin;
        this.lastLoreUpdate = 0;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        boolean shouldUpdateLore = (now - lastLoreUpdate) >= LORE_UPDATE_INTERVAL;

        for (Player player : Bukkit.getOnlinePlayers()) {
            scanInventory(player, now, shouldUpdateLore);
        }

        if (shouldUpdateLore) lastLoreUpdate = now;
    }

    private void scanInventory(Player player, long now, boolean shouldUpdateLore) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType().isAir()) continue;

            String typeId = PSDKItems.getItemTypeId(item);
            if (typeId == null) continue;

            Long expireTime = PSDKItems.getExpireTime(item);
            if (expireTime == null) continue;

            if (now >= expireTime) {
                Component displayName = item.getItemMeta() != null ? item.getItemMeta().displayName() : null;
                player.getInventory().setItem(i, null);

                if (displayName != null) {
                    player.sendMessage(mm.deserialize("<#FF0000>Seu item ")
                            .append(displayName)
                            .append(mm.deserialize("<#FF0000> expirou!")));
                } else {
                    player.sendMessage(mm.deserialize("<#FF0000>Seu item expirou!"));
                }
                continue;
            }

            if (!shouldUpdateLore) continue;

            long remaining = expireTime - now;
            if (remaining > LORE_UPDATE_THRESHOLD) continue;

            PSDKItems.ItemType type = PSDKItems.ItemType.fromId(typeId);
            if (type == null) continue;

            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            int uses = -1;
            if (type == PSDKItems.ItemType.TOTEM_INFERNAL) {
                uses = PSDKItems.getTotemUses(item);
            }

            List<Component> newLore = PSDKItems.buildLore(type, expireTime, uses);
            meta.lore(newLore);
            item.setItemMeta(meta);
        }
    }

    public void start() {
        this.runTaskTimer(plugin, 600L, 600L); // 30 seconds
    }
}
