package com.psdk.vip.commands;

import com.psdk.PSDK;
import com.psdk.vip.VipConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public class VipTimeCommand implements CommandExecutor {

    private final PSDK plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public VipTimeCommand(PSDK plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && sender.hasPermission("vip.admin")) {
            plugin.getVipManager().getLuckPermsHook().getOfflineUuid(args[0]).thenAccept(uuid -> {
                if (uuid != null) sendTime(sender, uuid, args[0]);
            });
        } else if (sender instanceof Player p) {
            sendTime(sender, p.getUniqueId(), p.getName());
        }
        return true;
    }

    private void sendTime(CommandSender sender, UUID uuid, String name) {
        String[] order = {"staff", "vips", "adicionais", "parceria"};
        Map<String, List<String>> resultsMap = new LinkedHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (String cat : order) {
            List<String> catList = new CopyOnWriteArrayList<>();
            resultsMap.put(cat, catList);
            futures.add(processCategory(uuid, cat, catList));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
            String playerSuffix = getPlayerSuffix(uuid);
            String header = VipConfig.fillName(VipConfig.VIPTIME_HEADER, name)
                    .replace("%suffix%", playerSuffix);

            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(mm.deserialize(header));

                boolean anyFound = false;
                for (String cat : order) {
                    List<String> lines = resultsMap.get(cat);
                    if (!lines.isEmpty()) {
                        anyFound = true;
                        String sectionTitle = VipConfig.VIPTIME_SECTIONS.get(cat);
                        if (sectionTitle != null) sender.sendMessage(mm.deserialize("\n" + sectionTitle));
                        lines.forEach(line -> sender.sendMessage(mm.deserialize(line)));
                    }
                }
                if (!anyFound) {
                    sender.sendMessage(mm.deserialize(VipConfig.VIPTIME_NO_PLANS));
                }
            });
        });
    }

    private CompletableFuture<Void> processCategory(UUID uuid, String path, List<String> results) {
        Map<String, VipConfig.Rank> section = VipConfig.getCategory(path);
        if (section == null) return CompletableFuture.completedFuture(null);

        List<CompletableFuture<Void>> checks = new ArrayList<>();
        for (Map.Entry<String, VipConfig.Rank> entry : section.entrySet()) {
            VipConfig.Rank rank = entry.getValue();

            checks.add(plugin.getVipManager().getLuckPermsHook()
                    .getRemainingTime(uuid, rank.luckpermsGroup()).thenAccept(time -> {
                if (time == null) return;

                String color = time.equals("Permanente") ? VipConfig.VIPTIME_COLOR_PERM
                        : time.contains("d")             ? VipConfig.VIPTIME_COLOR_FAR
                                                         : VipConfig.VIPTIME_COLOR_NEAR;

                String format = VipConfig.VIPTIME_FORMAT
                        .replace("%prefix%", rank.prefix())
                        .replace("%suffix%", rank.suffix())
                        .replace("%color%",  color)
                        .replace("%time%",   time);
                results.add(format);
            }));
        }
        return CompletableFuture.allOf(checks.toArray(new CompletableFuture[0]));
    }

    private String getPlayerSuffix(UUID uuid) {
        var user = plugin.getVipManager().getLuckPermsHook().getLuckPerms().getUserManager().getUser(uuid);
        if (user == null) return "<white>";
        String suffix = user.getCachedData().getMetaData().getSuffix();
        return (suffix != null) ? suffix : "<white>";
    }
}
