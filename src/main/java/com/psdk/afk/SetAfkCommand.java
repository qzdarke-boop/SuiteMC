package com.psdk.afk;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetAfkCommand implements CommandExecutor {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public SetAfkCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Esse comando é apenas para jogadores."));
            return true;
        }
        if (!player.hasPermission("psdk.setafk")) {
            player.sendMessage(mm.deserialize("<#FF0000>Sem permissão."));
            return true;
        }
        if (!player.getWorld().getName().equalsIgnoreCase(AfkManager.AFK_WORLD)) {
            player.sendMessage(mm.deserialize("<#FF0000>Você precisa estar no mundo <#fcc850>" + AfkManager.AFK_WORLD + " <#FF0000>para definir o local AFK."));
            return true;
        }

        plugin.getAfkManager().setAfkSpawn(player.getLocation());
        String coords = String.format("%.0f, %.0f, %.0f",
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ());
        player.sendMessage(mm.deserialize("<#10fc46>Local AFK definido em <#fcc850>" + coords + "<#10fc46>!"));
        return true;
    }
}
