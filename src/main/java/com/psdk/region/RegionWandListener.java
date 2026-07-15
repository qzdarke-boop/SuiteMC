package com.psdk.region;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class RegionWandListener implements Listener {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;
    private final NamespacedKey wandKey;

    public RegionWandListener(PSDK plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "psdk_region_wand");
    }

    private boolean isRegionWand(ItemStack item) {
        if (item == null || item.getType() != Material.GOLDEN_AXE) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("psdk.regiao")) return;

        ItemStack item = event.getItem();
        if (!isRegionWand(item)) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            plugin.getRegionManager().setPos1(player.getUniqueId(), block.getLocation());
            player.sendMessage(mm.deserialize("<#10fc46>Pos1 definida em <#fcc850>" + block.getX()
                    + ", " + block.getY() + ", " + block.getZ() + "<#10fc46>."));
            event.setCancelled(true);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            plugin.getRegionManager().setPos2(player.getUniqueId(), block.getLocation());
            player.sendMessage(mm.deserialize("<#10fc46>Pos2 definida em <#fcc850>" + block.getX()
                    + ", " + block.getY() + ", " + block.getZ() + "<#10fc46>."));
            event.setCancelled(true);
        }
    }
}
