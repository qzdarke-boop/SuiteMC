package com.psdk.economy;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class CoinsCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public CoinsCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player target;

        if (args.length >= 1 && sender.hasPermission("psdk.eco")) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(mm.deserialize("<#FF0000>Jogador nao encontrado."));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(mm.deserialize("<#fcc850>Uso: /" + label + " <jogador>"));
            return true;
        }

        double bal = plugin.getEconomyManager().getCoins(target.getUniqueId());

        if (target == sender) {
            sender.sendMessage(mm.deserialize("<#fcc850>Suas coins: <#10fc46>" + com.psdk.util.NumberUtil.abbrev(bal)));
        } else {
            sender.sendMessage(mm.deserialize("<#fcc850>Coins de <#efa600>" + target.getName() +
                    "<#fcc850>: <#10fc46>" + com.psdk.util.NumberUtil.abbrev(bal)));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("psdk.eco")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }
}
