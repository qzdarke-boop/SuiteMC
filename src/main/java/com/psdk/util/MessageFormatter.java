package com.psdk.util;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Centraliza formatação de mensagens com substituição de placeholders.
 * Substitui %player_name% pelo nome do jogador e processa PlaceholderAPI.
 */
public final class MessageFormatter {

    private static final boolean HAS_PAPI = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

    private MessageFormatter() {}

    /**
     * Formata uma mensagem substituindo %player_name% e processando PlaceholderAPI.
     * A ordem é: PAPI primeiro (resolve %luckperms_suffix% etc), depois %player_name%.
     */
    public static String format(Player player, String message) {
        if (message == null || message.isEmpty()) return message;

        String result = message;

        if (HAS_PAPI) {
            result = PlaceholderAPI.setPlaceholders(player, result);
        }

        result = replaceName(player, result);

        return result;
    }

    /**
     * Substitui apenas %player_name% pelo nome do jogador.
     * Útil quando você já processou PAPI separadamente.
     */
    public static String replaceName(Player player, String message) {
        if (message == null || player == null) return message;
        return message.replace("%player_name%", player.getName());
    }

    /**
     * Verifica se PlaceholderAPI está disponível.
     */
    public static boolean hasPAPI() {
        return HAS_PAPI;
    }
}
