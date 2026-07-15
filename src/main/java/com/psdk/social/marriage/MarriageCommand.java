package com.psdk.social.marriage;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class MarriageCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final PSDK plugin;

    public MarriageCommand(PSDK plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem usar este comando.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(MM.deserialize("<#fcc850>Uso: /casar <jogador>"));
            return true;
        }

        // Subcommand interno: /casar aceitar <uuid>
        if (args[0].equalsIgnoreCase("aceitar") && args.length >= 2) {
            try {
                UUID proposerId = UUID.fromString(args[1]);
                plugin.getMarriageManager().acceptProposal(player, proposerId);
            } catch (IllegalArgumentException e) {
                player.sendMessage(MM.deserialize("<#e22c27>Pedido inválido."));
            }
            return true;
        }

        // Proposta de casamento
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(MM.deserialize("<#e22c27>Jogador não encontrado ou offline."));
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage(MM.deserialize("<#e22c27>Você não pode se casar consigo mesmo."));
            return true;
        }

        plugin.getMarriageManager().sendProposal(player, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
