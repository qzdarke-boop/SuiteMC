package com.psdk.boss;

import com.psdk.PSDK;
import com.psdk.thepit.CombatManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * {@code /boss} — teleporta para a arena do boss, no MESMO estilo do /spawn
 * (contagem de 3s, mesmos sons, cancela se mover ou entrar em combate).
 */
public class BossCommand implements CommandExecutor {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public BossCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Esse comando é apenas para jogadores."));
            return true;
        }
        if (plugin.getBossManager().getArena() == null) {
            player.sendMessage(mm.deserialize("<#FF0000>A arena do boss ainda não foi definida."));
            return true;
        }
        // Sem boss vivo: NÃO teleporta — só informa quanto falta pro próximo.
        // (quando o boss nascer, dar /boss leva pra arena normalmente.)
        if (!plugin.getBossManager().isActive()) {
            long next = plugin.getBossManager().getNextAutoSpawnMillis();
            long remaining = next - System.currentTimeMillis();
            player.sendMessage(mm.deserialize("<#6817ff>Não há nenhum boss vivo no momento."));
            if (remaining > 0) {
                player.sendMessage(mm.deserialize("<#a4a4a4>Próximo boss automático em: <#10fc46>" + formatDuration(remaining)));
            } else {
                player.sendMessage(mm.deserialize("<#a4a4a4>O próximo boss deve nascer a qualquer momento."));
            }
            player.sendMessage(mm.deserialize("<#a4a4a4>Use <#6817ff>/boss <#a4a4a4>de novo quando ele nascer para ir até a arena."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
            return true;
        }

        CombatManager cm = plugin.getCombatManager();
        if (cm.isInCombat(player)) {
            player.sendMessage(mm.deserialize("<#FF0000>Você está em combate! Aguarde sair do combate."));
            return true;
        }
        startTeleport(player, cm);
        return true;
    }

    /** Formata uma duração em ms para algo como "2h 15min" ou "45min" ou "30s". */
    private String formatDuration(long ms) {
        long totalSec = ms / 1000L;
        long h = totalSec / 3600L;
        long m = (totalSec % 3600L) / 60L;
        long s = totalSec % 60L;
        if (h > 0) return h + "h " + m + "min";
        if (m > 0) return m + "min " + s + "s";
        return s + "s";
    }

    private void startTeleport(Player player, CombatManager cm) {
        final Location startPos = player.getLocation().clone();
        player.sendActionBar(mm.deserialize("<#6817ff>Teleportando em <#5012cc><bold>3</bold>s"));
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
                    Location arena = plugin.getBossManager().getArena();
                    if (arena == null) {
                        player.sendActionBar(mm.deserialize("<#FF0000>A arena do boss não está mais definida."));
                        cancel(); return;
                    }
                    player.teleport(arena);
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    player.sendActionBar(mm.deserialize("<#10fc46>Você foi levado para a arena do boss!"));
                    cancel(); return;
                }
                player.sendActionBar(mm.deserialize("<#6817ff>Teleportando em <#5012cc><bold>" + seconds + "</bold>s"));
                player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.0f);
                seconds--;
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }
}
