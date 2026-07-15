package com.psdk.colina;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class DonoColinaCommand implements CommandExecutor {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public DonoColinaCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ColinaManager cm = plugin.getColinaManager();

        if (!cm.isActive()) {
            sender.sendMessage(mm.deserialize("<#cbd1d7>Nenhuma colina configurada."));
            return true;
        }

        String dono = cm.getDono();
        if (dono.equalsIgnoreCase("Sem Dono")) {
            sender.sendMessage(mm.deserialize("<#cbd1d7>Ninguém está dominando a colina."));
        } else if (dono.equalsIgnoreCase("Disputa!")) {
            sender.sendMessage(mm.deserialize("<#cbd1d7>Está tendo uma guerra na colina vá até lá e seja o primeiro a eliminar os outros jogadores!"));
        } else {
            sender.sendMessage(mm.deserialize("<#cbd1d7>Dono atual: <#efa600>" + dono));
        }
        return true;
    }
}
