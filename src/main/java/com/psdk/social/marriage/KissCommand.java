package com.psdk.social.marriage;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class KissCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final double MAX_DIST_SQ = 4.0 * 4.0;

    private final PSDK plugin;

    public KissCommand(PSDK plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem usar este comando.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(MM.deserialize("<#fcc850>Uso: /beijar <jogador>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(MM.deserialize("<#e22c27>Jogador não encontrado ou offline."));
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage(MM.deserialize("<#e22c27>Você não pode beijar a si mesmo."));
            return true;
        }

        MarriageManager mm2 = plugin.getMarriageManager();

        // Apenas casados podem usar /beijar
        if (!mm2.isMarried(player.getUniqueId())) {
            player.sendMessage(MM.deserialize("<#e22c27>Você precisa estar casado(a) para usar este comando!"));
            return true;
        }
        if (!target.getUniqueId().equals(mm2.getPartner(player.getUniqueId()))) {
            player.sendMessage(MM.deserialize("<#e22c27>Você só pode beijar o seu parceiro(a)!"));
            return true;
        }

        if (!player.getWorld().equals(target.getWorld())
                || player.getLocation().distanceSquared(target.getLocation()) > MAX_DIST_SQ) {
            player.sendMessage(MM.deserialize("<#e22c27>Você precisa estar a até 4 blocos de distância!"));
            return true;
        }

        spawnKissEffects(player, target);

        player.sendMessage(MM.deserialize("<#FF69B4>Você <white>beijou seu amor <reset>"
                + mm2.resolveTag(target) + "<#FF69B4>!"));
        target.sendMessage(MM.deserialize(mm2.resolveTag(player) + " <white>beijou você! <#FF69B4>❤"));
        return true;
    }

    private void spawnKissEffects(Player from, Player to) {
        var loc = from.getLocation().add(
                (to.getLocation().getX() - from.getLocation().getX()) / 2,
                1.2,
                (to.getLocation().getZ() - from.getLocation().getZ()) / 2);

        from.getWorld().spawnParticle(Particle.HEART, loc, 18, 0.45, 0.45, 0.45, 0.02);
        from.getWorld().spawnParticle(Particle.NOTE, loc, 8, 0.35, 0.35, 0.35, 1);
        from.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 12, 0.35, 0.35, 0.35, 0.01);

        from.playSound(from.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9f, 1.8f);
        to.playSound(to.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9f, 1.8f);
        from.playSound(from.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.7f);
        to.playSound(to.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.7f);
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
