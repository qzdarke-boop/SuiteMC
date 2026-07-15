package com.psdk.thepit;

import com.psdk.thepit.topboard.TopBoardType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class TopsGUIListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Object holder = event.getInventory().getHolder();
        if (!(holder instanceof TopsGUI) && !(holder instanceof TopsDetailGUI)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getInventory()) return;

        int slot = event.getSlot();

        if (holder instanceof TopsGUI) {
            if (slot == TopsGUI.CLOSE_SLOT) {
                player.closeInventory();
                return;
            }
            TopBoardType category = TopsGUI.categoryAt(slot);
            if (category != null) {
                TopsDetailGUI.open(player, category,
                        category.supportsPeriods()
                                ? com.psdk.thepit.topboard.TopPeriod.WEEKLY
                                : com.psdk.thepit.topboard.TopPeriod.GLOBAL);
            }
            return;
        }

        TopsDetailGUI detail = (TopsDetailGUI) holder;
        if (slot == TopsDetailGUI.BACK_SLOT) {
            TopsGUI.open(player);
        } else if (slot == TopsDetailGUI.PERIOD_SLOT) {
            TopsDetailGUI.open(player, detail.getCategory(), detail.nextPeriod());
        }
    }
}
