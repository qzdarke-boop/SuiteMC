package com.psdk.afk;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class AfkCommand implements CommandExecutor {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public AfkCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Esse comando é apenas para jogadores."));
            return true;
        }

        AfkManager afkManager = plugin.getAfkManager();

        if (plugin.getCombatManager().isInCombat(player)) {
            player.sendMessage(mm.deserialize("<#FF0000>Você está em combate! Aguarde sair do combate."));
            return true;
        }

        if (afkManager.isAfk(player)) {
            player.sendMessage(mm.deserialize("<#a4a4a4>Você já está AFK!"));
            return true;
        }

        Location afkSpawn = afkManager.getAfkSpawn();
        if (afkSpawn == null) {
            player.sendMessage(mm.deserialize("<#FF0000>O local AFK ainda não foi definido."));
            return true;
        }

        final Location startPos = player.getLocation().clone();
        player.sendActionBar(mm.deserialize("<#fcc850>Teleportando em <#dea114><bold>3</bold>s"));
        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.0f);

        new BukkitRunnable() {
            int seconds = 2;
            @Override
            public void run() {
                if (!player.isOnline()) { cancel(); return; }
                Location cur = player.getLocation();
                // Mudou de mundo ou se afastou = movimento -> cancela (distance() entre
                // mundos diferentes lança exceção, por isso checamos o mundo primeiro).
                if (cur.getWorld() != startPos.getWorld() || cur.distanceSquared(startPos) > 0.25) {
                    player.sendActionBar(mm.deserialize("<#e22c27>Cancelado! Você se moveu!"));
                    cancel(); return;
                }
                if (plugin.getCombatManager().isInCombat(player)) {
                    player.sendActionBar(mm.deserialize("<#e22c27>Cancelado! Você entrou em combate!"));
                    cancel(); return;
                }
                if (seconds <= 0) {
                    player.teleport(afkSpawn);
                    afkManager.setAfk(player);
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    player.sendActionBar(mm.deserialize("<#10fc46>Você foi levado para o AFK!"));
                    cancel(); return;
                }
                player.sendActionBar(mm.deserialize("<#fcc850>Teleportando em <#dea114><bold>" + seconds + "</bold>s"));
                player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.0f);
                seconds--;
            }
        }.runTaskTimer(plugin, 20L, 20L);

        return true;
    }
}
