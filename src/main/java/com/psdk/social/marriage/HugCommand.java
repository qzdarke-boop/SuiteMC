package com.psdk.social.marriage;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class HugCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final double MAX_DIST_SQ = 4.0 * 4.0;

    private final PSDK plugin;

    public HugCommand(PSDK plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem usar este comando.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(MM.deserialize("<#fcc850>Uso: /abraçar <jogador>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(MM.deserialize("<#e22c27>Jogador não encontrado ou offline."));
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage(MM.deserialize("<#e22c27>Você não pode se abraçar."));
            return true;
        }
        if (!player.getWorld().equals(target.getWorld())
                || player.getLocation().distanceSquared(target.getLocation()) > MAX_DIST_SQ) {
            player.sendMessage(MM.deserialize("<#e22c27>Você precisa estar a até 4 blocos de distância!"));
            return true;
        }

        spawnHugEffects(player, target);

        MarriageManager mm2 = plugin.getMarriageManager();
        boolean partnered = mm2.isMarried(player.getUniqueId())
                && player.getUniqueId().equals(mm2.getPartner(target.getUniqueId()));

        player.sendMessage(MM.deserialize(partnered
                ? "<#FFB6C1>Você <white>abraçou seu amor <reset>" + mm2.resolveTag(target) + "<#FFB6C1>!"
                : "<#FFB6C1>Você <white>abraçou <reset>" + mm2.resolveTag(target) + "<#FFB6C1>!"));
        target.sendMessage(MM.deserialize(mm2.resolveTag(player) + " <white>abraçou você!"));
        return true;
    }

    private void spawnHugEffects(Player from, Player to) {
        var loc = from.getLocation().add(0, 1.0, 0);
        var toLoc = to.getLocation().add(0, 1.0, 0);

        Particle.DustOptions pink = new Particle.DustOptions(Color.fromRGB(0xFF, 0x69, 0xB4), 1.2f);

        // Partículas calorosas ao redor dos dois jogadores.
        from.getWorld().spawnParticle(Particle.DUST, loc,   28, 0.55, 0.55, 0.55, 0, pink);
        from.getWorld().spawnParticle(Particle.DUST, toLoc, 28, 0.55, 0.55, 0.55, 0, pink);
        from.getWorld().spawnParticle(Particle.HEART, loc,   6, 0.45, 0.45, 0.45, 0.01);
        from.getWorld().spawnParticle(Particle.HEART, toLoc, 6, 0.45, 0.45, 0.45, 0.01);
        from.getWorld().spawnParticle(Particle.NOTE, loc,   10, 0.5, 0.5, 0.5, 1);
        from.getWorld().spawnParticle(Particle.NOTE, toLoc, 10, 0.5, 0.5, 0.5, 1);

        from.playSound(from.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8f, 1.25f);
        to.playSound(to.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8f, 1.25f);
        from.playSound(from.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.55f);
        to.playSound(to.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.55f);
        from.playSound(from.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.45f, 1.6f);
        to.playSound(to.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.45f, 1.6f);
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
