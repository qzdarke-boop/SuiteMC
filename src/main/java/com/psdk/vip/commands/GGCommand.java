package com.psdk.vip.commands;

import com.psdk.PSDK;
import com.psdk.vip.VipConfig;
import com.psdk.vip.util.GGWaveManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GGCommand implements CommandExecutor {

    private final PSDK plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public GGCommand(PSDK plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem usar este comando.");
            return true;
        }

        GGWaveManager wave = plugin.getVipManager().getGgWaveManager();
        if (!wave.isActive()) {
            player.sendMessage(mm.deserialize(VipConfig.GG_NO_WAVE));
            return true;
        }

        if (wave.hasAlreadySentGG(player.getUniqueId())) {
            player.sendMessage(mm.deserialize(VipConfig.GG_ALREADY_SENT));
            return true;
        }

        wave.markGGSent(player.getUniqueId());

        String color    = wave.getRandomColor(VipConfig.GG_COLORS);
        String withGg   = VipConfig.GG_WAVE_FORMAT.replace("%gg%", color);

        String finalMsg = withGg;
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                finalMsg = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, withGg);
            } catch (Exception e) {
                finalMsg = withGg;
            }
        }
        // IMPORTANTE: resolve %player_name%/%player% DEPOIS do PAPI — a tag do LuckPerms
        // (%luckperms_suffix%) pode conter %player_name%, que o PAPI não re-resolve.
        finalMsg = VipConfig.fillName(finalMsg, player.getName());

        var component = mm.deserialize(finalMsg);
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(component));
        return true;
    }
}
