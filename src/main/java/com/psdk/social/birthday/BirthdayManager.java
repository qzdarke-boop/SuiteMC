package com.psdk.social.birthday;

import com.psdk.PSDK;
import com.psdk.vip.VipConfig;
import com.psdk.vip.util.GGWaveRewards;
import com.psdk.vip.util.GGWaveManager;
import com.psdk.vip.util.SkinRenderer;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;

public class BirthdayManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final boolean HAS_PAPI = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

    // ── EDITE AS MENSAGENS AQUI ───────────────────────────────────────────────
    private static final int GG_DURATION_SECONDS = 20;
    private static final String GG_ACTION_BAR =
            "<#cbd1d7>Aproveite a <b><#F5F528>ONDA</b> <#cbd1d7>de <#F5F528><bold>GG!</bold> <#848c94>(%time%s)";

    private static final String TITLE = "<#F5F528><bold>Feliz Aniversário!";
    private static final String SUBTITLE = "<#cbd1d7>O aniversariante é <reset>%tagTarget%<#cbd1d7>!";

    /** Uma linha por fileira da cabeça 8x8 (mesmo layout do anúncio VIP). */
    private static final List<String> BROADCAST_LINES = List.of(
            "",
            "<bold><#F5F528>Feliz Aniversário</bold>!",
            "<#cbd1d7>O aniversariante é <reset>%tagTarget% <#cbd1d7>!",
            "",
            "<gray>▸ <#cbd1d7>Mande <#F5F528>GG <#cbd1d7>para comemorar!",
            "",
            "",
            ""
    );
    // ─────────────────────────────────────────────────────────────────────────

    private final PSDK plugin;
    private final GGWaveManager ggWaveManager;

    public BirthdayManager(PSDK plugin) {
        this.plugin = plugin;
        this.ggWaveManager = new GGWaveManager(plugin);
    }

    public GGWaveManager getGgWaveManager() {
        return ggWaveManager;
    }

    public void announce(Player birthdayPlayer) {
        String tagTarget = resolveTag(birthdayPlayer);

        Title.Times times = Title.Times.times(
                Duration.ofMillis(10 * 50L),
                Duration.ofMillis(70 * 50L),
                Duration.ofMillis(20 * 50L));

        Title title = Title.title(
                MM.deserialize(TITLE),
                MM.deserialize(SUBTITLE.replace("%tagTarget%", tagTarget)),
                times);

        Bukkit.getOnlinePlayers().forEach(player -> {
            player.showTitle(title);
            player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.15f);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        });

        broadcastBirthday(birthdayPlayer, tagTarget);
    }

    public void handleBirthdayGG(Player player) {
        if (!ggWaveManager.isActive()) return;
        if (ggWaveManager.hasAlreadySentGG(player.getUniqueId())) {
            player.sendMessage(MM.deserialize(VipConfig.GG_ALREADY_SENT));
            return;
        }
        ggWaveManager.markGGSent(player.getUniqueId());

        String color = ggWaveManager.getRandomColor(VipConfig.GG_COLORS);
        String msg = VipConfig.GG_WAVE_FORMAT.replace("%gg%", color);

        if (HAS_PAPI) {
            try { msg = PlaceholderAPI.setPlaceholders(player, msg); } catch (Exception ignored) {}
        }
        msg = VipConfig.fillName(msg, player.getName());

        var comp = MM.deserialize(msg);
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(comp));
    }

    private void broadcastBirthday(Player birthdayPlayer, String tagTarget) {
        SkinRenderer.getSkinLines(birthdayPlayer.getName()).thenAccept(lines ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (VipConfig.HEAD_TOP_MARGIN) sendBlankLine();

                    for (int i = 0; i < 8; i++) {
                        String headLine = (lines != null && lines.size() > i)
                                ? VipConfig.HEAD_SPACE_AFTER + lines.get(i)
                                : VipConfig.HEAD_SPACE_AFTER + "        ";
                        String textLine = i < BROADCAST_LINES.size()
                                ? BROADCAST_LINES.get(i).replace("%tagTarget%", tagTarget)
                                : "";
                        var component = MM.deserialize(headLine + textLine);
                        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(component));
                    }

                    if (VipConfig.HEAD_BOTTOM_MARGIN) sendBlankLine();
                    ggWaveManager.startWave(GG_DURATION_SECONDS, GG_ACTION_BAR,
                            GGWaveRewards.tokenReward(plugin));
                }));
    }

    private void sendBlankLine() {
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(MM.deserialize("")));
    }

    private String resolveTag(Player player) {
        if (HAS_PAPI) {
            String prefix = PlaceholderAPI.setPlaceholders(player, "%luckperms_prefix%");
            String suffix = PlaceholderAPI.setPlaceholders(player, "%luckperms_suffix%");
            String name = player.getName();
            prefix = prefix.replace("%player_name%", name).replace("%player%", name);
            suffix = suffix.replace("%player_name%", name).replace("%player%", name);
            String tag = prefix + suffix;
            if (!tag.contains(name)) tag = tag + name;
            return tag;
        }
        return player.getName();
    }
}
