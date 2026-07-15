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

public class LockCommand implements CommandExecutor {

    private final PSDK plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public LockCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("psdk.lockchat")) {
            sender.sendMessage(mm.deserialize("<#FF0000>Você não tem permissão!"));
            return true;
        }

        if (!plugin.getChatManager().isChatEnabled()) {
            sender.sendMessage(mm.deserialize("<#FF0000>O chat já está bloqueado!"));
            return true;
        }

        plugin.getChatManager().setChatEnabled(false);

        String adminDisplay = sender.getName();
        if (sender instanceof Player player && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            String raw = PlaceholderAPI.setPlaceholders(player, "%luckperms_prefix%%luckperms_suffix%");
            adminDisplay = TextUtil.legacyToMiniMessage(raw.replace("%player_name%", player.getName()).replace("%player%", player.getName()));
        }

        Bukkit.broadcast(mm.deserialize("<#FF0000>O chat foi fechado por:<reset> " + adminDisplay));
        return true;
    }
}
