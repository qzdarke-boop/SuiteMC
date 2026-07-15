package com.psdk.vip.util;

import org.bukkit.Bukkit;
import org.bukkit.Sound;

public class SoundUtil {
    public static void sendGlobalSound(String soundData) {
        if (soundData == null || soundData.isEmpty()) return;
        try {
            String[] parts = soundData.split(";");
            String soundName = parts[0].toUpperCase().replace(".", "_");
            Sound sound = Sound.valueOf(soundName);
            float vol = Float.parseFloat(parts[1]);
            float pitch = Float.parseFloat(parts[2]);

            Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), sound, vol, pitch));
        } catch (Exception e) {
            Bukkit.getLogger().warning("Erro ao tocar som: " + soundData);
        }
    }
}
