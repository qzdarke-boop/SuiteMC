package com.psdk.clan;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /a <mensagem> — atalho para o chat de aliança.
 * Equivale a /ally chat <mensagem>.
 */
public class AllyChatCommand implements CommandExecutor {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private final PSDK plugin;

    public AllyChatCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Apenas jogadores podem usar este comando."));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(mm.deserialize("<#FF0000>Uso: /a <mensagem>"));
            return true;
        }
        AllyCommand.sendAllyChat(plugin, player, String.join(" ", args));
        return true;
    }
}
