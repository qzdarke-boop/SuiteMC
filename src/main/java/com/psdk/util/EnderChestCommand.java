package com.psdk.util;

import com.psdk.PSDK;
import com.psdk.ec.EnderChestGUIListener;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EnderChestCommand implements CommandExecutor {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private final PSDK plugin;

    public EnderChestCommand(PSDK plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<red>Apenas jogadores podem usar este comando."));
            return true;
        }
        // Abertura segura (fecha/salva a EC atual antes de recarregar) — anti-dupe.
        EnderChestGUIListener.open(plugin, player);
        return true;
    }
}
