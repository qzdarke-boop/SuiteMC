package com.psdk.social;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /spawnnpcdiscord} — posiciona o NPC do Discord (Wumpus) na sua posição.
 * {@code /spawnnpcdiscord remove} — remove o NPC. Só op / psdk.npc.discord.
 */
public class SpawnNpcDiscordCommand implements CommandExecutor {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public SpawnNpcDiscordCommand(PSDK plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Apenas jogadores."));
            return true;
        }
        if (!p.hasPermission("psdk.npc.discord")) {
            p.sendMessage(mm.deserialize("<#FF0000>Você não tem permissão para isso."));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("remove")) {
            plugin.getDiscordNpcManager().removeNpc();
            p.sendMessage(mm.deserialize("<#10fc46>NPC do Discord removido."));
            return true;
        }
        plugin.getDiscordNpcManager().setLocation(p.getLocation());
        p.sendMessage(mm.deserialize("<#10fc46>NPC do Discord (Wumpus) posicionado aqui! "
                + "<#a4a4a4>(use <#fcc850>/spawnnpcdiscord remove<#a4a4a4> pra tirar)"));
        return true;
    }
}
