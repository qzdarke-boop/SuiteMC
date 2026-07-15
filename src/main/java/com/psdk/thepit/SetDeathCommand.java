package com.psdk.thepit;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetDeathCommand implements CommandExecutor {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public SetDeathCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Apenas jogadores."));
            return true;
        }
        if (!player.hasPermission("psdk.setdeath")) {
            player.sendMessage(mm.deserialize("<#FF0000>Sem permissao."));
            return true;
        }

        plugin.setDeathSpawnLocation(player.getLocation());
        player.sendMessage(mm.deserialize(
                "<#10fc46>Local de respawn ao morrer definido em <#fcc850>"
                + String.format("%.0f", player.getLocation().getX()) + ", "
                + String.format("%.0f", player.getLocation().getY()) + ", "
                + String.format("%.0f", player.getLocation().getZ())
                + " <#a4a4a4>(" + player.getWorld().getName() + ")"));
        return true;
    }
}
