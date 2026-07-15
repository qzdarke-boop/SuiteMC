package com.psdk.thepit;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class KillsCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public KillsCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player target = (sender instanceof Player p) ? p : null;

        if (args.length >= 1 && sender.hasPermission("psdk.thepit")) {
            target = Bukkit.getPlayer(args[0]);
        }

        if (target == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Esse jogador não foi encontrado."));
            return true;
        }

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(target);
        if (data == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Os dados desse jogador não foram encontrados."));
            return true;
        }

        LevelManager lm = plugin.getLevelManager();
        int level = lm.getLevel(data.getKills());
        String bar = lm.buildBar(data.getKills(), level);

        sender.sendMessage(mm.deserialize(
                " <#a4a4a4>| <#ffffff>Kills: <#fcc850>" + data.getKills()
                + " <#a4a4a4>| <#ffffff>Level: <#fcc850>" + level
                + " <#a4a4a4>| " + bar));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("psdk.thepit")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }
}
