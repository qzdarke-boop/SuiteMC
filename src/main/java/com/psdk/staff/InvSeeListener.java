package com.psdk.staff;

import com.psdk.PSDK;
import com.psdk.util.OfflinePlayerDataUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Sincroniza o inventário espelhado do /invsee com o jogador alvo.
 *
 * <ul>
 *   <li><b>Online</b>: edição é refletida ao vivo no jogador.</li>
 *   <li><b>Offline</b>: edição livre; ao fechar, grava de volta no playerdata (NBT).
 *       Se o jogador tiver entrado durante a edição, aplica direto nele.</li>
 * </ul>
 */
public class InvSeeListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PSDK plugin;

    public InvSeeListener(PSDK plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof InvSeeGUI holder)) return;

        Inventory top = event.getView().getTopInventory();

        // Bloqueia mexer nos vidros decorativos (online e offline).
        if (event.getClickedInventory() == top && InvSeeGUI.isGlassSlot(event.getSlot())) {
            event.setCancelled(true);
            return;
        }

        if (holder.isOffline()) {
            // Offline: edição livre, persistência no fechamento (sem sync ao vivo).
            return;
        }

        if (event.isShiftClick() && event.getClickedInventory() != top) {
            event.setCancelled(true);
            handleShiftFromViewer(event, holder, top);
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> afterEdit(holder, top));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof InvSeeGUI holder)) return;

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize(event) && InvSeeGUI.isGlassSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }

        if (holder.isOffline()) {
            return; // edição livre, persiste no fechamento
        }

        Inventory top = event.getView().getTopInventory();
        plugin.getServer().getScheduler().runTask(plugin, () -> afterEdit(holder, top));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof InvSeeGUI holder)) return;

        Player target = Bukkit.getPlayer(holder.getTargetId());

        if (!holder.isOffline()) {
            if (target != null && target.isOnline()) {
                InvSeeGUI.syncToPlayer(target, event.getInventory());
            }
            return;
        }

        // Offline: se o alvo entrou durante a edição, aplica nele ao vivo; senão grava no NBT.
        HumanEntity staff = event.getPlayer();
        if (target != null && target.isOnline()) {
            InvSeeGUI.syncToPlayer(target, event.getInventory());
            staff.sendMessage(MM.deserialize("<#10fc46>" + holder.getTargetName()
                    + " entrou durante a edição — as mudanças foram aplicadas nele."));
            return;
        }

        ItemStack[] contents = InvSeeGUI.extractContents(event.getInventory());
        boolean ok = OfflinePlayerDataUtil.saveInventory(holder.getTargetId(), contents);
        if (ok) {
            staff.sendMessage(MM.deserialize("<#10fc46>Inventário de <#fcc850>" + holder.getTargetName()
                    + "<#10fc46> salvo (offline)."));
        } else {
            staff.sendMessage(MM.deserialize("<#e22c27>Não consegui salvar o inventário offline de <#fcc850>"
                    + holder.getTargetName() + "<#e22c27> (veja o console). Nada foi alterado."));
        }
    }

    private void afterEdit(InvSeeGUI holder, Inventory gui) {
        Player target = Bukkit.getPlayer(holder.getTargetId());
        if (target == null || !target.isOnline()) return;

        InvSeeGUI.syncToPlayer(target, gui);
        holder.refreshGlass();
    }

    private void handleShiftFromViewer(InventoryClickEvent event, InvSeeGUI holder, Inventory top) {
        ItemStack stack = event.getCurrentItem();
        if (stack == null || stack.getType().isAir()) return;

        for (int guiSlot = InvSeeGUI.MAIN_START; guiSlot <= InvSeeGUI.HOTBAR_END; guiSlot++) {
            ItemStack existing = top.getItem(guiSlot);
            if (existing != null && !existing.getType().isAir()) continue;
            top.setItem(guiSlot, stack.clone());
            event.getClickedInventory().setItem(event.getSlot(), null);
            afterEdit(holder, top);
            return;
        }

        for (int guiSlot : InvSeeGUI.armorGuiSlots()) {
            ItemStack existing = top.getItem(guiSlot);
            if (existing != null && !existing.getType().isAir()) continue;
            top.setItem(guiSlot, stack.clone());
            event.getClickedInventory().setItem(event.getSlot(), null);
            afterEdit(holder, top);
            return;
        }
    }

    private static int topSize(InventoryDragEvent event) {
        return event.getView().getTopInventory().getSize();
    }
}
