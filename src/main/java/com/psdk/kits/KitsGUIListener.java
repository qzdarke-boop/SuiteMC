package com.psdk.kits;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

public class KitsGUIListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PSDK plugin;

    public KitsGUIListener(PSDK plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof KitsInventoryHolder holder)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getInventory()) return;
        if (event.getCurrentItem() == null) return;

        switch (holder.getType()) {
            case MAIN -> handleMainMenu(event, player);
            case BASICOS -> handleBasicosMenu(event, player);
            case VIP -> handleVipMenu(event, player);
            case WEBSITE -> handleWebsiteMenu(event, player);
            case BASIC_PREVIEW -> handlePreviewMenu(event, player);
            case VIP_PREVIEW -> handleVipPreviewMenu(event, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof KitsInventoryHolder) {
            event.setCancelled(true);
        }
    }

    private void openNextTick(Player player, Inventory inventory) {
        plugin.getServer().getScheduler().runTask(plugin, (Runnable) () -> player.openInventory(inventory));
    }

    private void closeNextTick(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, (Runnable) player::closeInventory);
    }

    private KitCooldownManager cooldowns() {
        return plugin.getKitCooldownManager();
    }

    private void handleMainMenu(InventoryClickEvent event, Player player) {
        int slot = event.getSlot();

        if (slot == KitsGUI.SLOT_BASICOS) {
            openNextTick(player, KitsBasicosGUI.build(player.getUniqueId(), cooldowns()));
        } else if (slot == KitsGUI.SLOT_VIP) {
            openNextTick(player, KitsVipGUI.build(player.getUniqueId(), cooldowns()));
        } else if (slot == KitsGUI.SLOT_WEBSITE) {
            // Cabeça da Loja da Suite: envia o site + cupom do dia no chat (menu segue aberto).
            com.psdk.social.SuiteStore.sendStoreMessage(player);
        }
    }

    private void handleBasicosMenu(InventoryClickEvent event, Player player) {
        int slot = event.getSlot();

        if (slot == KitsBasicosGUI.SLOT_BACK) {
            openNextTick(player, KitsGUI.build());
            return;
        }

        Kit kit = KitsBasicosGUI.kitAtSlot(slot);
        if (kit == null) return;

        KitCooldownManager cooldowns = cooldowns();

        if (event.isRightClick()) {
            openNextTick(player, KitPreviewGUI.build(kit));
            return;
        }

        if (cooldowns.isOnCooldown(player.getUniqueId(), kit)) {
            String rem = cooldowns.formatRemaining(player.getUniqueId(), kit);
            player.sendMessage(MM.deserialize("<#e22c27>Aguarde <#cbd1d7>" + (rem == null ? "" : rem)
                    + " <#e22c27>para coletar este kit novamente."));
            openNextTick(player, KitPreviewGUI.build(kit));
            return;
        }

        giveKit(player, kit);
        cooldowns.setCooldown(player.getUniqueId(), kit);
        closeNextTick(player);
        player.sendMessage(MM.deserialize("<#10fc46>Kit <#cbd1d7>" + kit.getName() + " <#10fc46>coletado com sucesso!"));
    }

    private void handleVipMenu(InventoryClickEvent event, Player player) {
        int slot = event.getSlot();

        if (slot == KitsVipGUI.SLOT_BACK) {
            openNextTick(player, KitsGUI.build());
            return;
        }

        if (slot == KitsVipGUI.SLOT_STORE) {
            // Cabeça da Loja da Suite: mesma função da cabeça do menu principal.
            com.psdk.social.SuiteStore.sendStoreMessage(player);
            return;
        }

        VipKit kit = VipKit.atMenuSlot(slot);
        if (kit == null) return;

        KitCooldownManager cooldowns = cooldowns();

        if (event.isRightClick()) {
            openNextTick(player, VipKitPreviewGUI.build(plugin, kit));
            return;
        }

        if (!player.hasPermission(kit.getPermission())) {
            player.sendMessage(MM.deserialize("<reset><#e22c27>⚠️Necessário: <reset><white>")
                    .append(MM.deserialize(KitsVipGUI.iconFor(kit))));
            return;
        }

        if (cooldowns.isOnCooldown(player.getUniqueId(), kit)) {
            String rem = cooldowns.formatRemaining(player.getUniqueId(), kit);
            player.sendMessage(MM.deserialize("<#e22c27>Aguarde <#cbd1d7>" + (rem == null ? "" : rem)
                    + " <#e22c27>para coletar este kit novamente."));
            openNextTick(player, VipKitPreviewGUI.build(plugin, kit));
            return;
        }

        giveVipKit(player, kit);
        cooldowns.setCooldown(player.getUniqueId(), kit);
        closeNextTick(player);
        player.sendMessage(MM.deserialize("<#10fc46>" + kit.getDisplayName() + " <#10fc46>coletado com sucesso!"));
    }

    private void handleWebsiteMenu(InventoryClickEvent event, Player player) {
        if (event.getSlot() == KitsWebsiteGUI.SLOT_BACK) {
            openNextTick(player, KitsGUI.build());
        }
    }

    private void handlePreviewMenu(InventoryClickEvent event, Player player) {
        if (event.getSlot() == KitPreviewGUI.SLOT_BACK) {
            openNextTick(player, KitsBasicosGUI.build(player.getUniqueId(), cooldowns()));
        }
    }

    private void handleVipPreviewMenu(InventoryClickEvent event, Player player) {
        if (event.getSlot() == VipKitPreviewGUI.SLOT_BACK) {
            openNextTick(player, KitsVipGUI.build(player.getUniqueId(), cooldowns()));
        }
    }

    private void giveKit(Player player, Kit kit) {
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(
                kit.getItems().stream().map(ItemStack::clone).toArray(ItemStack[]::new));
        if (!overflow.isEmpty()) {
            overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            player.sendMessage(MM.deserialize("<#fcc850>Inventário cheio! Alguns itens caíram no chão."));
        }
    }

    private void giveVipKit(Player player, VipKit kit) {
        ItemStack[] items = kit.resolveSlotItems(plugin).values().stream()
                .map(ItemStack::clone)
                .toArray(ItemStack[]::new);
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(items);
        if (!overflow.isEmpty()) {
            overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            player.sendMessage(MM.deserialize("<#fcc850>Inventário cheio! Alguns itens caíram no chão."));
        }
    }
}
