package com.psdk.vip.listeners;

import com.psdk.PSDK;
import com.psdk.vip.commands.SetVipCommand;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public class ProxyMessageListener implements PluginMessageListener {

    private final PSDK plugin;
    private final SetVipCommand setVipCommand;

    public ProxyMessageListener(PSDK plugin, SetVipCommand setVipCommand) {
        this.plugin = plugin;
        this.setVipCommand = setVipCommand;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        ByteArrayDataInput in = ByteStreams.newDataInput(message);

        switch (channel) {
            case "heaven:announce" -> {
                String playerName = in.readUTF();
                String category = in.readUTF();
                String prefix = in.readUTF();
                String suffix = in.readUTF();
                Bukkit.getScheduler().runTask(plugin, () ->
                        setVipCommand.runLocalAnnouncement(playerName, category, prefix, suffix));
            }
            case "heaven:command" -> {
                String type = in.readUTF();
                if ("SETVIP".equals(type)) {
                    String playerName = in.readUTF();
                    String category = in.readUTF();
                    String rankKey = in.readUTF();
                    String duration = in.readUTF();
                    String silent = in.readUTF(); // "-s" (silencioso) ou "" (com anúncio)
                    String silentArg = silent.isEmpty() ? "" : " " + silent;
                    Bukkit.getScheduler().runTask(plugin, () ->
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                    "setvip " + playerName + " " + category + " " + rankKey + " " + duration + silentArg));
                }
            }
        }
    }
}
