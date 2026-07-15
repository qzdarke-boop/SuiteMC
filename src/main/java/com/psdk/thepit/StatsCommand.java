package com.psdk.thepit;

import com.psdk.PSDK;
import com.psdk.util.TextUtil;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class StatsCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final boolean HAS_PAPI = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

    private final PSDK plugin;

    public StatsCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Esse comando é apenas para jogadores."));
            return true;
        }

        Player target = player;
        if (args.length >= 1) {
            if (!player.hasPermission("psdk.thepit")) {
                player.sendMessage(mm.deserialize("<#FF0000>Você não tem permissão para ver estatísticas de outros jogadores!"));
                return true;
            }
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(mm.deserialize("<#FF0000>O Jogador não foi encontrado."));
                return true;
            }
        }

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(target);
        if (data == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Dados não encontrados."));
            return true;
        }

        LevelManager lm = plugin.getLevelManager();
        int level            = lm.getLevel(data.getKills());
        int nextKills        = lm.killsForNextLevel(level);
        int currentLevelKills = lm.killsForLevel(level);
        int progressKills    = Math.max(0, data.getKills() - currentLevelKills);
        int neededKills      = Math.max(1, nextKills - currentLevelKills);
        double coins         = plugin.getEconomyManager().getCoins(target.getUniqueId());
        double tokens        = plugin.getEconomyManager().getTokens(target.getUniqueId());

        player.sendMessage(mm.deserialize(""));
        player.sendMessage(mm.deserialize(TextUtil.legacyToMiniMessage(
                "<#a4a4a4><strikethrough>----------<reset> <#efa600><bold>ESTATÍSTICAS <reset>" +
                        resolvePlayerTag(target) + " <#a4a4a4><strikethrough>----------")));
        player.sendMessage(mm.deserialize(""));
        player.sendMessage(mm.deserialize("<!italic>  <bold><#fcc850>Combate"));
        player.sendMessage(mm.deserialize("<!italic>  <#a4a4a4>Level: <#efa600><bold>" + level));
        player.sendMessage(mm.deserialize("<!italic>  <#a4a4a4>Kills: <#10fc46>" + data.getKills()));
        player.sendMessage(mm.deserialize("<!italic>  <#a4a4a4>Mortes: <#e22c27>" + data.getDeaths()));
        player.sendMessage(mm.deserialize(""));
        player.sendMessage(mm.deserialize("<!italic>  <bold><#fcc850>Economia"));
        player.sendMessage(mm.deserialize("<!italic>  <#a4a4a4>Coins: <#10fc46>" + String.format("%.0f", coins)));
        player.sendMessage(mm.deserialize("<!italic>  <#a4a4a4>Tokens: <#10fc46>" + String.format("%.0f", tokens)));
        player.sendMessage(mm.deserialize(""));
        player.sendMessage(mm.deserialize("<!italic>  <bold><#fcc850>Progresso"));
        player.sendMessage(mm.deserialize("<!italic>  <#a4a4a4>" + progressKills + "<#a4a4a4>/<#fcc850>" + neededKills + " <#a4a4a4>kills para o próximo level"));
        player.sendMessage(mm.deserialize("<!italic>  " + lm.buildBar(data.getKills(), level)));
        player.sendMessage(mm.deserialize(""));
        player.sendMessage(mm.deserialize("<#a4a4a4><strikethrough>----------------------------------------"));

        return true;
    }

    private String resolvePlayerTag(Player player) {
        if (!HAS_PAPI) return "<reset>" + player.getName();
        String prefix = PlaceholderAPI.setPlaceholders(player, "%luckperms_prefix%");
        String suffix = PlaceholderAPI.setPlaceholders(player, "%luckperms_suffix%");
        String name = player.getName();
        suffix = suffix.replace("%player_name%", name).replace("%player%", name);
        prefix = prefix.replace("%player_name%", name).replace("%player%", name);
        // o nome já pode vir no prefix/suffix (via %player_name%); só adiciona se faltar (não duplica).
        String raw = prefix + suffix;
        if (!raw.contains(name)) raw = prefix + name + suffix;
        return raw;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("psdk.thepit")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }
}