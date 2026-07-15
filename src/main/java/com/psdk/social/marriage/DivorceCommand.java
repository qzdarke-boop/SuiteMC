package com.psdk.social.marriage;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DivorceCommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final PSDK plugin;

    public DivorceCommand(PSDK plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem usar este comando.");
            return true;
        }
        plugin.getMarriageManager().divorce(player);
        return true;
    }
}
