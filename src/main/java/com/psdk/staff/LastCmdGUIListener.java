package com.psdk.staff;

import com.psdk.PSDK;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.List;

/**
 * Paginação (somente leitura) da GUI de {@code /staff lastcmd}.
 */
public class LastCmdGUIListener implements Listener {

    private final PSDK plugin;

    public LastCmdGUIListener(PSDK plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof LastCmdGUI gui)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getInventory()) return;

        int slot = event.getSlot();
        int target;
        if (slot == LastCmdGUI.SLOT_PREV)      target = gui.getPage() - 1;
        else if (slot == LastCmdGUI.SLOT_NEXT) target = gui.getPage() + 1;
        else return;

        if (target < 0 || target >= gui.getTotalPages()) return;

        List<StaffManager.CmdEntry> entries =
                plugin.getStaffManager().getCommandLogNewestFirst(gui.getTargetId());
        LastCmdGUI next = LastCmdGUI.build(gui.getTargetId(), gui.getTargetName(), entries, target);
        player.openInventory(next.getInventory());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof LastCmdGUI) event.setCancelled(true);
    }
}
