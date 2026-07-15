package com.psdk.ec;

import com.psdk.PSDK;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class EnderChestGUI implements InventoryHolder {

    private final Player player;
    private final Inventory inventory;
    private final PSDK plugin;

    public EnderChestGUI(Player player, PSDK plugin) {
        this.player = player;
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 54, net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize("<#9b59b6>Ender Chest"));
        load();
    }

    private void load() {
        // Fonte única: banco (54 slots).
        ItemStack[] db = plugin.getEnderChestManager().load(player.getUniqueId());
        boolean dbHadItems = false;
        for (int i = 0; i < EnderChestManager.SLOTS; i++) {
            if (db[i] != null) { inventory.setItem(i, db[i]); dbHadItems = true; }
        }

        // A EC vanilla DEVE estar sempre vazia (invariante anti-dupe). Se tiver itens:
        //  • banco vazio  -> migração legítima da EC antiga: traz pro nosso inventário.
        //  • banco cheio  -> a vanilla é um RESÍDUO/dupe (ex.: .dat stale após crash, ou
        //                    injeção externa). Mesclar duplicaria, então NÃO mesclamos —
        //                    o banco é a verdade e a cópia vanilla é descartada.
        if (!isVanillaEmpty()) {
            if (!dbHadItems) {
                for (ItemStack it : player.getEnderChest().getContents()) {
                    if (it == null || it.getType().isAir()) continue;
                    int free = inventory.firstEmpty();
                    if (free >= 0 && free < EnderChestManager.SLOTS) {
                        inventory.setItem(free, it);
                    } else {
                        player.getInventory().addItem(it).values()
                                .forEach(o -> player.getWorld().dropItemNaturally(player.getLocation(), o));
                    }
                }
            } else {
                plugin.getLogger().warning("[EC] EC vanilla de " + player.getName()
                        + " continha itens com o banco já populado — cópia descartada (anti-dupe).");
            }
            player.getEnderChest().clear();
        }
    }

    /** Persiste os 54 slots no banco e garante a EC vanilla vazia (anti-dupe). */
    public void save() {
        ItemStack[] contents = new ItemStack[EnderChestManager.SLOTS];
        for (int i = 0; i < EnderChestManager.SLOTS; i++) {
            contents[i] = inventory.getItem(i);
        }
        boolean ok = plugin.getEnderChestManager().save(player.getUniqueId(), contents);
        if (!isVanillaEmpty()) player.getEnderChest().clear();

        // ANTI-DUPE/ANTI-PERDA EM CRASH: a EC vive no banco e é salva na hora, mas a
        // mudança correspondente no inventário do jogador (item que saiu/entrou) só iria
        // pro .dat num save limpo. Se o servidor cair entre os dois, o .dat e o banco
        // ficam dessincronizados (item em dois lugares = dupe, ou em nenhum = perda).
        // Persistir o .dat aqui, junto com o save no banco, mantém ambos consistentes.
        if (ok && player.isOnline()) {
            try { player.saveData(); } catch (Throwable ignored) {}
        }
    }

    private boolean isVanillaEmpty() {
        for (ItemStack it : player.getEnderChest().getContents()) {
            if (it != null && !it.getType().isAir()) return false;
        }
        return true;
    }

    public Player getPlayer() { return player; }

    @Override public Inventory getInventory() { return inventory; }
}
