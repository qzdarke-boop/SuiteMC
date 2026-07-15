package com.psdk.staff;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class StaffChatCommand implements CommandExecutor {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<color:#FF0000>Apenas jogadores."));
            return true;
        }
        if (!player.hasPermission("psdk.staff.chat")) {
            player.sendMessage(mm.deserialize("<color:#FF0000>Sem permissão."));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(mm.deserialize("<color:#FF0000>Uso: /sc <mensagem>"));
            return true;
        }
        StringBuilder msg = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) msg.append(" ");
            msg.append(args[i]);
        }
        StaffCommand.broadcastStaffMessage(player, msg.toString());
        return true;
    }
}
