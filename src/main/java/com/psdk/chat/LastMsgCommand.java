package com.psdk.chat;

import com.psdk.PSDK;
import com.psdk.util.TextUtil;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedList;

public class LastMsgCommand implements CommandExecutor {

    private final PSDK plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public LastMsgCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("psdk.lastmsg")) {
            sender.sendMessage(mm.deserialize("<#FF0000>Sem permissão!"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(mm.deserialize("<#FF0000>Uso: /lastmsg <jogador>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Jogador não encontrado."));
            return true;
        }

        String displayName = target.getName();
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            String raw = PlaceholderAPI.setPlaceholders(target, "%luckperms_prefix%%luckperms_suffix%");
            displayName = TextUtil.legacyToMiniMessage(raw.replace("%player_name%", target.getName()).replace("%player%", target.getName()));
        }

        LinkedList<String> msgs = plugin.getChatManager().getPlayerHistory(target.getUniqueId());

        if (msgs.isEmpty()) {
            sender.sendMessage(mm.deserialize("<#FF9000>Nenhuma mensagem recente de " + displayName));
            return true;
        }

        sender.sendMessage(mm.deserialize("<#FF9000>Últimas mensagens de " + displayName + "<#FF9000>:"));
        for (String m : msgs) {
            sender.sendMessage(mm.deserialize("<dark_gray>- <white>" + m));
        }
        return true;
    }
}
