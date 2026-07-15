package com.psdk.ec;

import com.psdk.PSDK;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EnderChestGUIListener implements Listener {

    private final PSDK plugin;

    /** Jogadores com um save de EC já agendado para este tick (coalescing). */
    private final Set<UUID> pendingSave = ConcurrentHashMap.newKeySet();

    public EnderChestGUIListener(PSDK plugin) { this.plugin = plugin; }

    /**
     * Abertura SEGURA da Ender Chest. Fecha qualquer inventário aberto ANTES de construir
     * o novo GUI: o fechar dispara o save da EC atual, então a (re)abertura sempre carrega
     * o estado já persistido — fechando a brecha de dupe por reabrir rápido (tirar item e
     * dar /ec de novo lia a EC antiga, ainda com o item).
     */
    public static void open(PSDK plugin, Player player) {
        UUID id = player.getUniqueId();
        player.closeInventory(); // dispara save + libera trava da EC atual
        // Anti-concorrência: se um staff (ou outra sessão) está editando esta EC agora,
        // não abrir uma 2ª cópia em memória (evita sobrescrita/dupe).
        if (!plugin.getEnderChestManager().acquireLock(id, id)) {
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize("<#FF0000>Sua ender chest está sendo acessada por um administrador. Tente novamente em instantes."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
            return;
        }
        EnderChestGUI gui = new EnderChestGUI(player, plugin);
        player.openInventory(gui.getInventory());
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.7f, 1.0f);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof EnderChestGUI gui) {
            gui.save();
            UUID id = gui.getPlayer().getUniqueId();
            plugin.getEnderChestManager().releaseLock(id, id);
        }
    }

    /** Salva a cada alteração (coalescido para 1x por tick) — mantém o banco sempre atual. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof EnderChestGUI gui) {
            scheduleSave(gui);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof EnderChestGUI gui) {
            scheduleSave(gui);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof EnderChestGUI gui) {
            gui.save();
            UUID id = gui.getPlayer().getUniqueId();
            plugin.getEnderChestManager().releaseLock(id, id);
        }
    }

    private void scheduleSave(EnderChestGUI gui) {
        UUID id = gui.getPlayer().getUniqueId();
        if (!pendingSave.add(id)) return; // já há save agendado para este jogador
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingSave.remove(id);
            if (gui.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof EnderChestGUI cur
                    && cur == gui) {
                gui.save();
            }
        });
    }

    /** Salva todas as EC abertas (chamado no onDisable do plugin). */
    public static void saveAllOpen() {
        var mgr = PSDK.getInstance().getEnderChestManager();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof EnderChestGUI gui) {
                gui.save();
                UUID id = gui.getPlayer().getUniqueId();
                mgr.releaseLock(id, id);
            }
        }
    }

    /**
     * Clicar com a direita num ENDER_CHEST abre o nosso GUI de 54 slots em vez do
     * enderchest vanilla. Roda depois da proteção de região (ignoreCancelled).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return; // evita disparo duplo (off-hand)
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ENDER_CHEST) return;

        Player player = event.getPlayer();
        // Agachado segurando um item: deixa o vanilla agir (colocar bloco, etc.).
        if (player.isSneaking()) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand != null && !hand.getType().isAir()) return;
        }

        event.setCancelled(true); // impede o enderchest vanilla de abrir
        open(plugin, player);
    }
}
