package com.psdk.thepit;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class SpawnCommand implements CommandExecutor {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public SpawnCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Esse comando é apenas para jogadores."));
            return true;
        }

        CombatManager cm = plugin.getCombatManager();
        if (cm.isInCombat(player)) {
            player.sendMessage(mm.deserialize("<#FF0000>Você está em combate! Aguarde sair do combate."));
            return true;
        }

        // Tanto o AFK quanto o teleporte normal usam a mesma contagem regressiva.
        final boolean afk = plugin.getAfkManager().isAfk(player);
        startTeleport(player, cm, afk);
        return true;
    }

    private void startTeleport(Player player, CombatManager cm, boolean afk) {
        final Location startPos = player.getLocation().clone();
        player.sendActionBar(mm.deserialize(
                "<#fcc850>Teleportando em <#dea114><bold>3</bold>s"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);

        new BukkitRunnable() {
            int seconds = 2;
            @Override
            public void run() {
                if (!player.isOnline()) { cancel(); return; }
                Location cur = player.getLocation();
                if (cur.getWorld() != startPos.getWorld() || cur.distanceSquared(startPos) > 0.25) {
                    player.sendActionBar(mm.deserialize("<#e22c27>Cancelado! Você se moveu!"));
                    cancel(); return;
                }
                if (cm.isInCombat(player)) {
                    player.sendActionBar(mm.deserialize("<#e22c27>Cancelado! Você entrou em combate!"));
                    cancel(); return;
                }
                if (seconds <= 0) {
                    Location spawn = plugin.getSpawnLocation();
                    if (spawn == null) spawn = player.getWorld().getSpawnLocation();
                    if (afk) plugin.getAfkManager().removeAfk(player);
                    player.teleport(spawn);
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    player.sendActionBar(mm.deserialize(afk
                            ? "<#10fc46>Você saiu do AFK e foi para o spawn!"
                            : "<#10fc46>Você foi levado para o spawn!"));
                    cancel(); return;
                }
                player.sendActionBar(mm.deserialize(
                        "<#fcc850>Teleportando em <#dea114><bold>" + seconds + "</bold>s"));
                player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.0f);
                seconds--;
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }
}
