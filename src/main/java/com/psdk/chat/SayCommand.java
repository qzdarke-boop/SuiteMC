package com.psdk.chat;

import com.psdk.PSDK;
import com.psdk.util.TextUtil;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SayCommand implements CommandExecutor {

    private final PSDK plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public SayCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("psdk.say")) {
            sender.sendMessage(mm.deserialize("<#FF0000>Você não tem permissão!"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(mm.deserialize("<#FF0000>Uso: /say <mensagem> [-s]"));
            return true;
        }

        String fullMessage = String.join(" ", args);
        boolean silent = false;

        if (fullMessage.endsWith(" -s")) {
            silent = true;
            fullMessage = fullMessage.substring(0, fullMessage.length() - 3);
        }

        String content;
        if (sender instanceof Player p) {
            content = "%luckperms_prefix%%luckperms_suffix%<white>: <reset>" + fullMessage;
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                content = PlaceholderAPI.setPlaceholders(p, content);
                content = content.replace("%player_name%", p.getName()).replace("%player%", p.getName());
            }
        } else {
            content = "<#FF5555>CONSOLE<white>: <reset>" + fullMessage;   // enviado pelo console
        }

        net.kyori.adventure.text.Component line = mm.deserialize(TextUtil.legacyToMiniMessage(content));
        net.kyori.adventure.text.Component blank = net.kyori.adventure.text.Component.empty();

        // Envia DIRETO pra cada jogador — o Bukkit.broadcast não chegava a todos nesse setup.
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(blank);
            p.sendMessage(blank);
            p.sendMessage(line);
            p.sendMessage(blank);
            p.sendMessage(blank);
            if (!silent) p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
        plugin.getServer().getConsoleSender().sendMessage(line);   // console vê também
        return true;
    }
}
