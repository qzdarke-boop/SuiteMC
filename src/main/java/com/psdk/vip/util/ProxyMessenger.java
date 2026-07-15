package com.psdk.vip.util;

import com.psdk.PSDK;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class ProxyMessenger {

    public static final String CHANNEL_ANNOUNCE = "heaven:announce";
    public static final String CHANNEL_COMMAND = "heaven:command";

    public static boolean sendAnnouncement(PSDK plugin,
                                           String playerName,
                                           String category,
                                           String prefix,
                                           String suffix) {
        Player carrier = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (carrier == null) return false;

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(playerName);
        out.writeUTF(category);
        out.writeUTF(prefix != null ? prefix : "");
        out.writeUTF(suffix != null ? suffix : "<white>");

        carrier.sendPluginMessage(plugin, CHANNEL_ANNOUNCE, out.toByteArray());
        return true;
    }
}
