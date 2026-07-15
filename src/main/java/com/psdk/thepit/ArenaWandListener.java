package com.psdk.thepit;

import com.psdk.PSDK;
import com.psdk.util.WandUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class ArenaWandListener implements Listener {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public ArenaWandListener(PSDK plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("psdk.arena")) return;

        ItemStack item = event.getItem();
        if (item == null || !WandUtils.isWand(item)) return;

        if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null) {
            plugin.getArenaManager().getArenaData().setPos1(event.getClickedBlock().getLocation());
            player.sendMessage(mm.deserialize(
                    "<#10fc46>Pos1 definida em <#fcc850>"
                    + event.getClickedBlock().getX() + ", "
                    + event.getClickedBlock().getY() + ", "
                    + event.getClickedBlock().getZ() + "<#10fc46>."));
            event.setCancelled(true);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            plugin.getArenaManager().getArenaData().setPos2(event.getClickedBlock().getLocation());
            player.sendMessage(mm.deserialize(
                    "<#10fc46>Pos2 definida em <#fcc850>"
                    + event.getClickedBlock().getX() + ", "
                    + event.getClickedBlock().getY() + ", "
                    + event.getClickedBlock().getZ() + "<#10fc46>."));
            event.setCancelled(true);
        }
    }
}
