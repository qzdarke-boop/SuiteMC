package com.psdk.colina;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetarColinaCommand implements CommandExecutor {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public SetarColinaCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Apenas jogadores."));
            return true;
        }
        if (!player.hasPermission("psdk.setarcolina")) {
            player.sendMessage(mm.deserialize("<#FF0000>Sem permissão!"));
            return true;
        }

        double raio = plugin.getColinaManager().getRadius();   // mantém o raio atual se não passar
        if (args.length >= 1) {
            try {
                raio = Double.parseDouble(args[0]);
            } catch (NumberFormatException e) {
                player.sendMessage(mm.deserialize("<#FF0000>Raio inválido. Use: <#fcc850>/setarcolina [raio]<#FF0000> (ex: 10)"));
                return true;
            }
        }

        plugin.getColinaManager().setLocation(player.getLocation(), raio);
        player.sendMessage(mm.deserialize(
                "<#10fc46>Colina setada em <#fcc850>"
                        + String.format("%.0f", player.getLocation().getX()) + ", "
                        + String.format("%.0f", player.getLocation().getY()) + ", "
                        + String.format("%.0f", player.getLocation().getZ())
                        + "<#10fc46> com raio <#fcc850>" + String.format("%.0f", plugin.getColinaManager().getRadius())
                        + "<#10fc46>!"));
        player.sendMessage(mm.deserialize("<#a4a4a4>Dica: fique no <bold>centro do morro</bold> (longe do spawn) ao setar."));
        return true;
    }
}
