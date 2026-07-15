package com.psdk.vip.integrations;

import com.psdk.PSDK;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LuckPermsHook {
    private final LuckPerms lp;
    private final PSDK plugin;

    public LuckPermsHook(PSDK plugin) {
        this.lp = LuckPermsProvider.get();
        this.plugin = plugin;
    }

    public LuckPerms getLuckPerms() { return lp; }

    public void addVip(String name, String group, String timeStr) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean perm = timeStr.equalsIgnoreCase("perm")
                    || timeStr.equalsIgnoreCase("permanente") || timeStr.equals("0");
            // 1) Adiciona o grupo PRIMEIRO (setprimary exige ser membro antes).
            String addCmd = perm
                    ? "lp user " + name + " parent add " + group
                    : "lp user " + name + " parent addtemp " + group + " " + timeStr;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), addCmd);
            // 2) Só então torna primário (é o que define a tag/prefixo exibido).
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + name + " parent setprimary " + group);
        });
    }

    public void removeGroup(UUID uuid, String group) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        if (name == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + name + " parent remove " + group);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + name + " parent setprimary default");
        });
    }

    public CompletableFuture<UUID> getOfflineUuid(String name) {
        return lp.getUserManager().lookupUniqueId(name);
    }

    public CompletableFuture<String> getRemainingTime(UUID uuid, String group) {
        return lp.getUserManager().loadUser(uuid).thenApply(user -> {
            if (user == null) return null;
            var node = user.getNodes().stream()
                    .filter(n -> n.getKey().equalsIgnoreCase("group." + group))
                    .findFirst();

            if (node.isPresent()) {
                var expiry = node.get().getExpiry();
                if (expiry == null) return "Permanente";

                Duration d = Duration.between(Instant.now(), expiry);
                if (d.isNegative()) return null;

                long days = d.toDays();
                long hours = d.toHours() % 24;
                long minutes = d.toMinutes() % 60;
                long seconds = d.getSeconds() % 60;

                StringBuilder sb = new StringBuilder();
                if (days > 0) sb.append(days).append("d ");
                if (hours > 0) sb.append(hours).append("h ");

                if (days == 0) {
                    if (minutes > 0) sb.append(minutes).append("m ");
                    if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append("s");
                }

                return sb.toString().trim();
            }
            return null;
        });
    }
}
