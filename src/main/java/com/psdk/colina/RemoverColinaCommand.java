package com.psdk.colina;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class RemoverColinaCommand implements CommandExecutor {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public RemoverColinaCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("psdk.removercolina")) {
            sender.sendMessage(mm.deserialize("<#FF0000>Sem permissao."));
            return true;
        }
        plugin.getColinaManager().remove();
        sender.sendMessage(mm.deserialize("<#10fc46>Colina removida com sucesso!"));
        return true;
    }
}
