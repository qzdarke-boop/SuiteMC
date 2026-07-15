package com.psdk.boss;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /setbossarena} — define o destino do {@code /boss}.
 * {@code /setbossspawn} — define onde o boss nasce (auto a cada 15 min e /pboss spawn).
 * (Mesma classe registrada nos dois comandos; diferencia pelo nome.)
 */
public class BossSetCommand implements CommandExecutor {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public BossSetCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("psdk.boss.admin")) {
            sender.sendMessage(mm.deserialize("<#FF0000>Você não tem permissão para isso."));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Apenas jogadores podem usar este comando."));
            return true;
        }

        if (command.getName().equalsIgnoreCase("setbossarena")) {
            plugin.getBossManager().setArena(player.getLocation());
            player.sendMessage(mm.deserialize("<#10fc46>Arena do boss definida! O <#fcc850>/boss <#10fc46>leva pra cá."));
        } else {
            plugin.getBossManager().setBossSpawn(player.getLocation());
            player.sendMessage(mm.deserialize("<#10fc46>Local de nascimento do boss definido! Ele nasce aqui a cada <#fcc850>1h30<#10fc46>."));
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
        return true;
    }
}
