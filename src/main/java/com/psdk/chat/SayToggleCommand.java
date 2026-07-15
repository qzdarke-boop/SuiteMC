package com.psdk.chat;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class SayToggleCommand implements CommandExecutor {

    private final PSDK plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public SayToggleCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("psdk.saytoggle")) return true;

        UUID uuid = player.getUniqueId();
        ChatManager cm = plugin.getChatManager();

        if (cm.getSayTogglePlayers().contains(uuid)) {
            cm.getSayTogglePlayers().remove(uuid);
            cm.getSaySilentPlayers().remove(uuid);
            player.sendMessage(mm.deserialize("<#FF0000>Modo /say desativado."));
        } else {
            cm.getSayTogglePlayers().add(uuid);
            if (args.length > 0 && args[0].equalsIgnoreCase("-s")) {
                cm.getSaySilentPlayers().add(uuid);
                player.sendMessage(mm.deserialize("<#00FF00>Modo /say <bold>SILENCIOSO</bold> ativado!"));
            } else {
                player.sendMessage(mm.deserialize("<#00FF00>Modo /say ativado! (Com som)"));
            }
        }
        return true;
    }
}
