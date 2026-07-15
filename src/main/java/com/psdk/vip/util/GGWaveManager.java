package com.psdk.vip.util;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

public class GGWaveManager {

    private final PSDK plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Random random = new Random();
    private final Set<UUID> playersWhoSentGG = new HashSet<>();
    private boolean active = false;
    private int timeLeft = 0;
    private BukkitTask currentTask;
    private BiConsumer<Set<UUID>, Integer> onWaveEnd;

    public GGWaveManager(PSDK plugin) { this.plugin = plugin; }

    public void startWave(int duration, String actionBarTemplate) {
        startWave(duration, actionBarTemplate, null);
    }

    public void startWave(int duration, String actionBarTemplate,
                          BiConsumer<Set<UUID>, Integer> onWaveEnd) {
        cancelWithoutReward();
        this.active = true;
        this.timeLeft = duration;
        this.onWaveEnd = onWaveEnd;
        this.playersWhoSentGG.clear();

        this.currentTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (timeLeft <= 0) {
                    finishWave();
                    return;
                }
                String display = actionBarTemplate.replace("%time%", String.valueOf(timeLeft));
                var component = mm.deserialize(display);
                Bukkit.getOnlinePlayers().forEach(p -> p.sendActionBar(component));
                timeLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void finishWave() {
        if (currentTask != null) {
            currentTask.cancel();
            currentTask = null;
        }
        this.active = false;

        Set<UUID> participants = new HashSet<>(playersWhoSentGG);
        int count = participants.size();
        BiConsumer<Set<UUID>, Integer> callback = onWaveEnd;
        onWaveEnd = null;
        playersWhoSentGG.clear();

        if (callback != null && count > 0) {
            callback.accept(participants, count);
        }
    }

    private void cancelWithoutReward() {
        if (currentTask != null) {
            currentTask.cancel();
            currentTask = null;
        }
        this.active = false;
        this.onWaveEnd = null;
        this.playersWhoSentGG.clear();
    }

    public void stopWave() {
        cancelWithoutReward();
    }

    public boolean hasAlreadySentGG(UUID uuid) {
        return playersWhoSentGG.contains(uuid);
    }

    public void markGGSent(UUID uuid) {
        playersWhoSentGG.add(uuid);
    }

    public String getRandomColor(List<String> colors) {
        if (colors == null || colors.isEmpty()) return "<white>";
        return colors.get(random.nextInt(colors.size()));
    }

    public boolean isActive() { return active; }
}
