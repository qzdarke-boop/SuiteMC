package com.psdk.crates;

import com.psdk.PSDK;
import com.psdk.thepit.CombatManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class CaixasCommand implements CommandExecutor {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public CaixasCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Apenas jogadores podem usar este comando."));
            return true;
        }

        Location dest = plugin.getCratesSpawn();
        if (dest == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Local das crates não configurado!"));
            return true;
        }

        CombatManager cm = plugin.getCombatManager();
        if (cm.isInCombat(player)) {
            player.sendMessage(mm.deserialize("<#FF0000>Você está em combate! Aguarde sair do combate."));
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
                if (cur.getWorld() != startPos.getWorld() || cur.distanceSquared(startPos) > 0.25) {
                    player.sendMessage(mm.deserialize("<#FF0000>Cancelado! Você se moveu!"));
                    cancel(); return;
                }
                if (cm.isInCombat(player)) {
                    player.sendMessage(mm.deserialize("<#FF0000>Cancelado! Você entrou em combate!"));
                    cancel(); return;
                }
                if (seconds <= 0) {
                    player.teleport(dest);
                    player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.5f);
                    player.sendTitle(
                            LegacyComponentSerializer.legacySection().serialize(mm.deserialize("<#fcc850>CRATES")),
                            LegacyComponentSerializer.legacySection().serialize(mm.deserialize("<#a4a4a4>Você chegou nas crates!")),
                            5, 40, 10);
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
