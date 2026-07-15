package com.psdk.thepit;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class ResetKillsCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final PSDK plugin;

    public ResetKillsCommand(PSDK plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("psdk.thepit")) {
            sender.sendMessage(MM.deserialize("<#e22c27>Sem permissão."));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(MM.deserialize("<#fcc850>Uso: /resetkills <jogador>"));
            return true;
        }

        UUID uuid = resolveUUID(args[0]);
        if (uuid == null) {
            sender.sendMessage(MM.deserialize("<#e22c27>Jogador não encontrado."));
            return true;
        }

        plugin.getPlayerDataManager().resetKills(uuid);
        sender.sendMessage(MM.deserialize("<#10fc46>Kills de <#cbd1d7>" + args[0] + " <#10fc46>zeradas!"));

        Player online = Bukkit.getPlayer(uuid);
        if (online != null)
            online.sendMessage(MM.deserialize("<#e22c27>Suas kills foram resetadas por um administrador."));
        return true;
    }

    private UUID resolveUUID(String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online.getUniqueId();
        @SuppressWarnings("deprecation")
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        return off.hasPlayedBefore() ? off.getUniqueId() : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
