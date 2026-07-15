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

public class ChatClearCommand implements CommandExecutor {

    private final PSDK plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public ChatClearCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("psdk.chatclear")) {
            sender.sendMessage(mm.deserialize("<#FF0000>Você não tem permissão para limpar o chat!"));
            return true;
        }

        for (int i = 0; i < 450; i++) {
            Bukkit.broadcast(mm.deserialize(" "));
        }

        String adminDisplay = sender.getName();
        if (sender instanceof Player player && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            String raw = PlaceholderAPI.setPlaceholders(player, "%luckperms_prefix%%luckperms_suffix%");
            adminDisplay = TextUtil.legacyToMiniMessage(raw.replace("%player_name%", player.getName()).replace("%player%", player.getName()));
        }

        Bukkit.broadcast(mm.deserialize(" "));
        Bukkit.broadcast(mm.deserialize("<#00FF00>O chat foi limpo por:<reset> " + adminDisplay));
        Bukkit.broadcast(mm.deserialize(" "));
        return true;
    }
}
