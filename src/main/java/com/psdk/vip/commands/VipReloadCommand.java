package com.psdk.vip.commands;

import com.psdk.PSDK;
import com.psdk.vip.VipConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class VipReloadCommand implements CommandExecutor {

    private final PSDK plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public VipReloadCommand(PSDK plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vip.admin")) {
            sender.sendMessage(mm.deserialize(VipConfig.MSG_NO_PERMISSION));
            return true;
        }
        sender.sendMessage(mm.deserialize(
            "<aqua>A configuração VIP está compilada em <white>VipConfig.java<aqua>." +
            " Edite o arquivo e recompile o plugin para alterar."
        ));
        return true;
    }
}
