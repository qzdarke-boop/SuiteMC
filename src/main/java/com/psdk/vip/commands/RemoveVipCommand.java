package com.psdk.vip.commands;

import com.psdk.PSDK;
import com.psdk.vip.VipConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RemoveVipCommand implements CommandExecutor, TabCompleter {

    private final PSDK plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public RemoveVipCommand(PSDK plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vip.admin")) {
            sender.sendMessage(mm.deserialize(VipConfig.MSG_NO_PERMISSION));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(mm.deserialize("<#FF0000>Uso: /removevip <player> <vips/staff/adicionais/parceria> <rank>"));
            return true;
        }

        String playerName = args[0];
        String category   = args[1].toLowerCase();
        String rankKey    = args[2].toUpperCase();

        Map<String, VipConfig.Rank> section = VipConfig.getCategory(category);
        if (section == null || !section.containsKey(rankKey)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Categoria ou Rank não encontrados na configuração."));
            return true;
        }

        VipConfig.Rank rank = section.get(rankKey);

        plugin.getVipManager().getLuckPermsHook().getOfflineUuid(playerName).thenAccept(uuid -> {
            if (uuid == null) {
                String notFound = VipConfig.fillName(VipConfig.MSG_PLAYER_NOT_FOUND, playerName);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(mm.deserialize(notFound)));
                return;
            }

            plugin.getVipManager().getLuckPermsHook().removeGroup(uuid, rank.luckpermsGroup());

            String successMsg = VipConfig.fillName(VipConfig.MSG_REMOVE_VIP, playerName)
                    .replace("%prefix%", rank.prefix());
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(mm.deserialize(successMsg)));
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 2) return List.of("vips", "staff", "adicionais", "parceria");
        if (args.length == 3) {
            Map<String, VipConfig.Rank> section = VipConfig.getCategory(args[1].toLowerCase());
            if (section != null) return new ArrayList<>(section.keySet());
        }
        return List.of();
    }
}
