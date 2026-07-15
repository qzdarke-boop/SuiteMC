package com.psdk.thepit;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TopsCommand implements CommandExecutor {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private final PSDK plugin;

    public TopsCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<color:#FF0000>Apenas jogadores."));
            return true;
        }
        TopsGUI.open(player);
        return true;
    }
}
