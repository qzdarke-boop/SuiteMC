package com.psdk.chat;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AnnounceCommand implements CommandExecutor {

    private final PSDK plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public AnnounceCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("psdk.anunciar")) {
            sender.sendMessage(mm.deserialize("<#FF0000>Você não tem permissão para usar este comando."));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(mm.deserialize("<#F0C039>Uso correto: /anunciar <mensagem>"));
            return true;
        }

        String message = String.join(" ", args);

        Bukkit.broadcast(mm.deserialize(""));
        Bukkit.broadcast(mm.deserialize(message));
        Bukkit.broadcast(mm.deserialize(""));

        sender.sendMessage(mm.deserialize("<#55FF55>Anúncio enviado para todo o servidor."));
        return true;
    }
}
