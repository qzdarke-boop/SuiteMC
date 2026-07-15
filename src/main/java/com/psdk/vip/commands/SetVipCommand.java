package com.psdk.vip.commands;

import com.psdk.PSDK;
import com.psdk.vip.VipConfig;
import com.psdk.vip.util.GGWaveRewards;
import com.psdk.vip.util.ProxyMessenger;
import com.psdk.vip.util.SkinRenderer;
import com.psdk.vip.util.SoundUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SetVipCommand implements CommandExecutor, TabCompleter {

    private final PSDK plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public SetVipCommand(PSDK plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vip.admin")) {
            sender.sendMessage(mm.deserialize(VipConfig.MSG_NO_PERMISSION));
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(mm.deserialize("<#FF0000>Uso: /setvip <player> <categoria> <rank> <tempo> [-s]"));
            return true;
        }

        String targetName    = args[0];
        String categoryInput = args[1];
        String rankKey       = args[2].toUpperCase();
        String duration      = args[3].equalsIgnoreCase("Permanente") ? "perm" : args[3];
        boolean silent       = args.length >= 5 && args[4].equalsIgnoreCase("-s");

        String path = VipConfig.pathFromCategory(categoryInput);
        Map<String, VipConfig.Rank> categoryMap = VipConfig.getCategory(path);

        if (categoryMap == null || !categoryMap.containsKey(rankKey)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Rank " + rankKey + " não encontrado na categoria " + path + "!"));
            return true;
        }

        VipConfig.Rank rank = categoryMap.get(rankKey);

        executeSetVip(sender, targetName, path, rankKey, duration, silent,
                rank.prefix(), rank.suffix(), rank.luckpermsGroup());
        return true;
    }

    private void executeSetVip(CommandSender sender, String targetName, String path, String rankKey,
                               String duration, boolean silent, String prefix, String suffix, String group) {
        plugin.getVipManager().getLuckPermsHook().addVip(targetName, group, duration);

        if (!silent) {
            String announceKey = path.equalsIgnoreCase("vips") ? "VIP" : path.toUpperCase();
            executeVisualAnnounce(targetName, announceKey, prefix, suffix);
        }

        sender.sendMessage(mm.deserialize("<#10fc46>Sucesso! " + targetName + " agora é " + rankKey));
    }

    public void executeVisualAnnounce(String target, String category, String prefix, String suffix) {
        if (VipConfig.PROXY_ENABLED) {
            boolean sent = ProxyMessenger.sendAnnouncement(plugin, target, category, prefix, suffix);
            if (!sent) runLocalAnnouncement(target, category, prefix, suffix);
        } else {
            runLocalAnnouncement(target, category, prefix, suffix);
        }
    }

    public void runLocalAnnouncement(String target, String category, String prefix, String suffix) {
        VipConfig.Announcement ann = VipConfig.ANNOUNCEMENTS.get(category);
        if (ann == null) return;

        String p = (prefix == null) ? "" : prefix;
        String s = (suffix == null) ? "" : suffix;
        String style = ann.style().toLowerCase();

        boolean playSound  = !style.equals("compact");
        boolean showTitle  = style.equals("full") || style.equals("title-only");
        boolean showGgWave = !style.equals("title-only");

        if (playSound) SoundUtil.sendGlobalSound(ann.sound());

        if (showTitle && !ann.title().isEmpty()) {
            String titleMsg = VipConfig.fillName(ann.title(),    target).replace("%prefix%", p).replace("%suffix%", s);
            String subMsg   = VipConfig.fillName(ann.subtitle(), target).replace("%prefix%", p).replace("%suffix%", s);

            Title.Times times = Title.Times.times(
                    Duration.ofMillis(ann.titleFadeIn()  * 50L),
                    Duration.ofMillis(ann.titleStay()    * 50L),
                    Duration.ofMillis(ann.titleFadeOut() * 50L));
            Title title = Title.title(mm.deserialize(titleMsg), mm.deserialize(subMsg), times);
            Bukkit.getOnlinePlayers().forEach(player -> player.showTitle(title));
        }

        if (style.equals("compact")) {
            sendChatLines(ann.text(), target, p, s);
            if (showGgWave && VipConfig.GG_WAVE_ENABLED)
                plugin.getVipManager().getGgWaveManager().startWave(
                        VipConfig.GG_WAVE_DURATION, VipConfig.GG_WAVE_ACTION_BAR,
                        GGWaveRewards.tokenReward(plugin));
            return;
        }

        if (style.equals("chat-only")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (VipConfig.HEAD_TOP_MARGIN)    sendBlankLine();
                sendChatLines(ann.text(), target, p, s);
                if (VipConfig.HEAD_BOTTOM_MARGIN) sendBlankLine();
                if (showGgWave && VipConfig.GG_WAVE_ENABLED)
                    plugin.getVipManager().getGgWaveManager().startWave(
                        VipConfig.GG_WAVE_DURATION, VipConfig.GG_WAVE_ACTION_BAR,
                        GGWaveRewards.tokenReward(plugin));
            });
            return;
        }

        if (style.equals("title-only")) return;

        // full: head + title + chat + gg wave
        SkinRenderer.getSkinLines(target).thenAccept(lines -> {
            List<String> textTemplate = ann.text();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (VipConfig.HEAD_TOP_MARGIN) sendBlankLine();

                for (int i = 0; i < 8; i++) {
                    String headLine = (lines != null && lines.size() > i)
                            ? VipConfig.HEAD_SPACE_AFTER + lines.get(i)
                            : VipConfig.HEAD_SPACE_AFTER + "        ";
                    String textLine = (i < textTemplate.size()) ? textTemplate.get(i) : "";
                    String finalLine = headLine + VipConfig.fillName(textLine, target)
                            .replace("%prefix%", p).replace("%suffix%", s);
                    var component = mm.deserialize(finalLine);
                    Bukkit.getOnlinePlayers().forEach(pl -> pl.sendMessage(component));
                }

                if (VipConfig.HEAD_BOTTOM_MARGIN) sendBlankLine();
                if (showGgWave && VipConfig.GG_WAVE_ENABLED)
                    plugin.getVipManager().getGgWaveManager().startWave(
                        VipConfig.GG_WAVE_DURATION, VipConfig.GG_WAVE_ACTION_BAR,
                        GGWaveRewards.tokenReward(plugin));
            });
        });
    }

    private void sendChatLines(List<String> lines, String target, String p, String s) {
        for (String line : lines) {
            String finalLine = VipConfig.fillName(line, target).replace("%prefix%", p).replace("%suffix%", s);
            var component = mm.deserialize(finalLine);
            Bukkit.getOnlinePlayers().forEach(pl -> pl.sendMessage(component));
        }
    }

    private void sendBlankLine() {
        Bukkit.getOnlinePlayers().forEach(pl -> pl.sendMessage(mm.deserialize("")));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> suggestions.add(p.getName()));
            return suggestions;
        }
        if (args.length == 2) return List.of("Planos", "Equipe", "Outros", "Criadores");
        if (args.length == 3) {
            String path = VipConfig.pathFromCategory(args[1]);
            Map<String, VipConfig.Rank> sec = VipConfig.getCategory(path);
            return (sec == null) ? List.of() : new ArrayList<>(sec.keySet());
        }
        if (args.length == 4) return List.of("1s", "1m", "1h", "1d", "Permanente");
        if (args.length == 5) return List.of("-s");
        return List.of();
    }
}
