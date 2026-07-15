package com.psdk.chat;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SpyCommand implements CommandExecutor {

    private final Set<UUID> spying = new HashSet<>();
    private final MiniMessage mm = MiniMessage.miniMessage();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (!player.hasPermission("psdk.spy")) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não tem permissão!"));
            return true;
        }

        if (spying.contains(player.getUniqueId())) {
            spying.remove(player.getUniqueId());
            player.sendMessage(mm.deserialize("<#FF0000>Monitoramento de mensagens (SPY) desativado."));
        } else {
            spying.add(player.getUniqueId());
            player.sendMessage(mm.deserialize("<#00FF00>Monitoramento de mensagens (SPY) ativado!"));
        }
        return true;
    }

    public Set<UUID> getSpies() { return spying; }
}
