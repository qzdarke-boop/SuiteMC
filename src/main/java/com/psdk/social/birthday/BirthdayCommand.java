package com.psdk.social.birthday;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class BirthdayCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final PSDK plugin;

    public BirthdayCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("psdk.aniversariante")) {
            sender.sendMessage(MM.deserialize("<#e22c27>Você não tem permissão para usar este comando."));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(MM.deserialize("<#fcc850>Uso: /aniversariante <jogador>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(MM.deserialize("<#e22c27>Jogador não encontrado ou offline."));
            return true;
        }

        plugin.getBirthdayManager().announce(target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("psdk.aniversariante")) return List.of();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
