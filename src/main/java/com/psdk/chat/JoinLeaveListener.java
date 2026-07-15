package com.psdk.chat;

import com.psdk.PSDK;
import com.psdk.util.TextUtil;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class JoinLeaveListener implements Listener {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private final PSDK plugin;

    public JoinLeaveListener(PSDK plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        Player player = event.getPlayer();

        // Pré-carrega as settings no cache (main thread), evitando acesso ao SQLite
        // pela thread assíncrona do chat ao verificar menções.
        if (plugin.getSettingsManager() != null) {
            plugin.getSettingsManager().loadPlayer(player.getUniqueId());
        }

        // Mantém clan_members.player_name atualizado (nick pode ter mudado).
        if (plugin.getClanManager() != null) {
            plugin.getClanManager().syncMemberName(player.getUniqueId(), player.getName());
        }

        String joinMsg = "%luckperms_prefix%%luckperms_suffix% <white>entrou no servidor!";
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            joinMsg = PlaceholderAPI.setPlaceholders(player, joinMsg);
        }
        joinMsg = joinMsg.replace("%player_name%", player.getName()).replace("%player%", player.getName());

        Bukkit.broadcast(mm.deserialize(" "));
        Bukkit.broadcast(mm.deserialize(TextUtil.legacyToMiniMessage(joinMsg)));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        Player player = event.getPlayer();

        if (plugin.getSettingsManager() != null) {
            plugin.getSettingsManager().unloadPlayer(player.getUniqueId());
        }

        String quitMsg = "%luckperms_prefix%%luckperms_suffix% <white>saiu do servidor.";
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            quitMsg = PlaceholderAPI.setPlaceholders(player, quitMsg);
        }
        quitMsg = quitMsg.replace("%player_name%", player.getName()).replace("%player%", player.getName());

        Bukkit.broadcast(mm.deserialize(" "));
        Bukkit.broadcast(mm.deserialize(TextUtil.legacyToMiniMessage(quitMsg)));
    }
}