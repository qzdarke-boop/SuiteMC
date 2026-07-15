package com.psdk.crates;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetCratesCommand implements CommandExecutor {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public SetCratesCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Apenas jogadores."));
            return true;
        }
        if (!player.hasPermission("psdk.setcrates")) {
            player.sendMessage(mm.deserialize("<#FF0000>Sem permissão!"));
            return true;
        }

        plugin.setCratesSpawn(player.getLocation());
        String coords = String.format("%.0f, %.0f, %.0f",
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ());
        player.sendMessage(mm.deserialize("<#10fc46>Local das crates definido em <#fcc850>" + coords + " <#a4a4a4>(" + player.getWorld().getName() + ")"));
        return true;
    }
}
