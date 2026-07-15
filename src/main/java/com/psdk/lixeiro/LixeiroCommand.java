package com.psdk.lixeiro;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * {@code /lixeiro} — mostra quanto falta para a próxima passada do lixeiro.
 * Com permissão {@code psdk.lixeiro.admin} e o argumento "agora", força a limpeza.
 */
public class LixeiroCommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PSDK plugin;

    public LixeiroCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("agora")) {
            if (!sender.hasPermission("psdk.lixeiro.admin")) {
                sender.sendMessage(MM.deserialize("<#FF0000>Você não tem permissão para isso!"));
                return true;
            }
            plugin.getLixeiroManager().clearGround();
            return true;
        }

        sender.sendMessage(MM.deserialize(plugin.getLixeiroManager().statusMessage()));
        return true;
    }
}
