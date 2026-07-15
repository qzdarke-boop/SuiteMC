package com.psdk.shop;

import com.psdk.PSDK;
import com.psdk.region.RegionFlag;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ShopCommand implements CommandExecutor {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public ShopCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Esse comando é apenas para jogadores."));
            return true;
        }
        // A loja só abre na área segura E fora de combate. Estar em combate na área
        // segura ou apenas fora do PvP não é suficiente — as duas condições precisam
        // valer ao mesmo tempo. Como este comando é o único ponto de entrada da loja
        // (o tutorial usa /shop e não há aliases), a checagem aqui cobre todos os
        // atalhos e chamadas internas.
        if (plugin.getCombatManager().isInCombat(player)) {
            player.sendMessage(mm.deserialize("<#e22c27>Você não pode abrir a loja enquanto estiver em combate."));
            return true;
        }
        if (plugin.getRegionManager().isAllowed(player.getLocation(), RegionFlag.PVP)) {
            player.sendMessage(mm.deserialize("<#e22c27>Você só pode abrir a loja na área segura."));
            return true;
        }
        plugin.getShopGUIListener().trackOpen(player.getUniqueId());
        player.openInventory(ShopGUI.buildMain(plugin.getShopManager()));
        return true;
    }
}