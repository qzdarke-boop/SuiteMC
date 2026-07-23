package com.psdk.crates;

import com.psdk.PSDK;
import com.psdk.economy.EconomyManager;
import com.psdk.util.MessageFormatter;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class CrateGUIListener implements Listener {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final boolean HAS_PAPI = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

    private final PSDK plugin;

    public CrateGUIListener(PSDK plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CrateGUI gui)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals(event.getInventory())) return;

        int slot = event.getSlot();
        Crate crate = gui.getCrate();

        if (gui.isStoreSlot(slot))  { com.psdk.social.SuiteStore.sendStoreMessage(player); return; }
        if (gui.isBuySlot(slot))    { handleBuy(player, gui, crate, slot); return; }
        if (gui.isRewardSlot(slot)) { handleClaim(player, gui, crate, slot); }
    }

    private void handleBuy(Player player, CrateGUI gui, Crate crate, int slot) {
        int quantidade = gui.getBuyAmount(slot);
        if (quantidade <= 0) return;

        double preco = gui.getPreco(quantidade);
        EconomyManager eco = plugin.getEconomyManager();

        if (!eco.hasTokens(player.getUniqueId(), preco)) {
            player.sendMessage(mm.deserialize("<#FF0000>Tokens insuficientes!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }
        if (crate.isEsgotada()) {
            player.sendMessage(mm.deserialize("<#FF0000>Essa caixa exclusiva esta esgotada!"));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 0.8f);
            return;
        }
        if (crate.getTipo() == Crate.Tipo.EXCLUSIVA && crate.getLimiteGlobal() > 0
                && quantidade > crate.getLimiteGlobal()) {
            player.sendMessage(mm.deserialize("<#FF0000>Restam apenas " + crate.getCor()
                    + crate.getLimiteGlobal() + " <#FF0000>chave(s) dessa caixa exclusiva!"));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 0.8f);
            return;
        }
        ItemStack testKey = plugin.getKeyManager().createKeyItem(crate, 1);
        if (testKey == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Erro interno: chave não pôde ser criada!"));
            return;
        }

        if (!eco.removeTokens(player.getUniqueId(), preco)) {
            player.sendMessage(mm.deserialize("<#FF0000>Tokens insuficientes!"));
            return;
        }

        int restante = quantidade;
        while (restante > 0) {
            int stackSize = Math.min(restante, 64);
            ItemStack keyItem = plugin.getKeyManager().createKeyItem(crate, stackSize);
            if (keyItem != null) {
                if (player.getInventory().firstEmpty() == -1) {
                    player.getWorld().dropItemNaturally(player.getLocation(), keyItem);
                } else {
                    player.getInventory().addItem(keyItem);
                }
            }
            restante -= stackSize;
        }

        CrateManager cm = plugin.getCrateManager();

        if (crate.getTipo() == Crate.Tipo.EXCLUSIVA && crate.getLimiteGlobal() > 0) {
            crate.setLimiteGlobal(Math.max(0, crate.getLimiteGlobal() - quantidade));
            cm.updateLimiteGlobal(crate);
        }

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.4f);

        String cor = crate.getCor();

        player.sendActionBar(mm.deserialize("<#10fc46>Você comprou " + cor + quantidade + " <#10fc46>chave(s) de " + cor + crate.getNome() + "<#10fc46>!"));

        String tag = resolvePlayerTag(player);

        if (crate.getTipo() == Crate.Tipo.EXCLUSIVA) {
            plugin.getServer().sendMessage(mm.deserialize(
                    "\n" + cor + "<bold>Caixa " + crate.getNome() + " </bold><#e22c27>[ᴇxᴄʟᴜsɪᴠᴀ]\n<#cbd1d7>O jogador<reset> " + tag + " <#cbd1d7>comprou " + cor + quantidade + " <#cbd1d7>chave(s)!\n<#9812ff><bold>POUCAS UNIDADES!\n<#848c94>Apenas " + cor + crate.getLimiteGlobal() + " <#848c94>restantes!\n"));
        } else {
            plugin.getServer().sendMessage(mm.deserialize(
                    "\n" + cor + "<bold>Caixa " + crate.getNome() + "\n<#a4a4a4>O jogador " + tag + " <#a4a4a4>comprou " + cor + quantidade + " <#a4a4a4>chave(s)!\n"));
        }

        int newSaldo = cm.getSaldo(player.getUniqueId(), crate.getNome());
        double newTokens = eco.getTokens(player.getUniqueId());
        gui.refreshDynamic(player, newSaldo, newTokens);
    }

    private void handleClaim(Player player, CrateGUI gui, Crate crate, int slot) {
        int index = gui.getRewardIndex(slot);
        if (index < 0 || index >= Crate.MAX_ITENS) return;

        ItemStack rewardItem = crate.getItens().get(index);
        if (rewardItem == null || rewardItem.getType().isAir()) return;

        if (!player.hasPermission("psdk.crates.use")) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não tem permissão para fazer isso!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }

        CrateManager cm = plugin.getCrateManager();
        int saldo = cm.getSaldo(player.getUniqueId(), crate.getNome());
        if (saldo <= 0) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não tem chaves suficientes para coletar os itens dessa caixa!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(mm.deserialize("<#FF0000>Seu inventário está cheio!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }

        if (!cm.consumeSaldo(player.getUniqueId(), crate.getNome())) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não tem chaves suficientes para coletar os itens dessa caixa!"));
            return;
        }

        ItemStack toGive = rewardItem.clone();
        toGive.setAmount(1);
        java.util.Map<Integer, ItemStack> overflow = player.getInventory().addItem(toGive);
        for (ItemStack o : overflow.values()) player.getWorld().dropItemNaturally(player.getLocation(), o);

        int novoSaldo = cm.getSaldo(player.getUniqueId(), crate.getNome());
        String cor = crate.getCor();

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
        player.sendMessage(mm.deserialize("<#cbd1d7>Item coletado! <#cbd1d7>Chaves restante: " + cor + novoSaldo + "<#cbd1d7>."));
        player.getWorld().spawnParticle(Particle.ENCHANT,
                player.getLocation().add(0, 1.5, 0), 25, 0.4, 0.3, 0.4, 0.5);

        double tokens = plugin.getEconomyManager().getTokens(player.getUniqueId());
        gui.refreshDynamic(player, novoSaldo, tokens);
    }

    private String resolvePlayerTag(Player player) {
        if (!HAS_PAPI) return "<reset>" + player.getName();
        String prefix = PlaceholderAPI.setPlaceholders(player, "%luckperms_prefix%");
        String suffix = PlaceholderAPI.setPlaceholders(player, "%luckperms_suffix%");
        prefix = MessageFormatter.replaceName(player, prefix);
        suffix = MessageFormatter.replaceName(player, suffix);
        return prefix + " " + suffix;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CrateGUI) {
            event.setCancelled(true);
        }
    }
}