package com.psdk.util;

import me.clip.placeholderapi.PlaceholderAPI;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;

/**
 * Formatação de tag de jogador e cores de clan (hex / gradient MiniMessage).
 */
public final class ClanText {

    private ClanText() {}

    /** Tag LP (prefix + suffix), com %player_name% resolvido — sem duplicar o nick. */
    public static String resolvePlayerTag(Player player) {
        if (player == null) return "";
        try {
            RegisteredServiceProvider<LuckPerms> provider =
                    Bukkit.getServicesManager().getRegistration(LuckPerms.class);
            if (provider == null) return player.getName();

            User user = provider.getProvider().getPlayerAdapter(Player.class).getUser(player);
            String prefix = user.getCachedData().getMetaData().getPrefix();
            String suffix = user.getCachedData().getMetaData().getSuffix();
            return assembleTag(prefix, suffix, player.getName(), player);
        } catch (Exception e) {
            return player.getName();
        }
    }

    /** Tag LP para jogador offline (rankings, hologramas). */
    public static String resolveOfflinePlayerTag(UUID uuid, String fallbackName) {
        if (uuid == null) {
            return fallbackName != null ? fallbackName : "Desconhecido";
        }
        try {
            RegisteredServiceProvider<LuckPerms> provider =
                    Bukkit.getServicesManager().getRegistration(LuckPerms.class);
            if (provider == null) {
                return fallbackName != null ? fallbackName : uuid.toString();
            }

            LuckPerms lp = provider.getProvider();
            User user = lp.getUserManager().getUser(uuid);
            if (user == null) {
                user = lp.getUserManager().loadUser(uuid).join();
            }
            if (user == null) {
                return fallbackName != null ? fallbackName : "Desconhecido";
            }

            String prefix = user.getCachedData().getMetaData().getPrefix();
            String suffix = user.getCachedData().getMetaData().getSuffix();
            String name = fallbackName != null ? fallbackName : Bukkit.getOfflinePlayer(uuid).getName();
            if (name == null) name = "Desconhecido";

            return assembleTag(prefix, suffix, name, Bukkit.getOfflinePlayer(uuid));
        } catch (Exception e) {
            return fallbackName != null ? fallbackName : "Desconhecido";
        }
    }

    /**
     * Monta prefix + suffix do LuckPerms.
     * PAPI primeiro (expande placeholders aninhados), depois %player_name%/%player%.
     */
    private static String assembleTag(String prefix, String suffix, String name, OfflinePlayer papiTarget) {
        prefix = prefix != null ? prefix : "";
        suffix = suffix != null ? suffix : "";
        if (name == null || name.isBlank()) name = "Desconhecido";

        if (papiTarget != null && MessageFormatter.hasPAPI()) {
            prefix = PlaceholderAPI.setPlaceholders(papiTarget, prefix);
            suffix = PlaceholderAPI.setPlaceholders(papiTarget, suffix);
        }

        prefix = replaceNameTokens(prefix, name);
        suffix = replaceNameTokens(suffix, name);
        prefix = normalizeReset(prefix);
        suffix = normalizeReset(suffix);

        String tag = prefix + suffix;
        if (!tag.contains(name)) {
            tag = prefix + name + suffix;
        }
        return tag;
    }

    private static String replaceNameTokens(String text, String name) {
        return text.replace("%player_name%", name).replace("%player%", name);
    }

    private static String normalizeReset(String text) {
        return text.replace("{reset}", "<reset>")
                .replace("&r", "<reset>")
                .replace("§r", "<reset>");
    }

    private static final java.util.regex.Pattern VALID_HEX = java.util.regex.Pattern.compile(
            "#[0-9a-fA-F]{3,6}");
    private static final java.util.regex.Pattern VALID_GRADIENT = java.util.regex.Pattern.compile(
            "<?gradient(:[^>]+)?>?", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** Aplica cor do clan a um texto (hex #RRGGBB ou tag gradient). */
    public static String applyColor(String colorTag, String content) {
        if (colorTag == null || colorTag.isBlank()) return content;
        String trimmed = colorTag.strip();
        if (trimmed.isEmpty()) return content;

        // Gradient completo: <gradient:#A:#B>
        if (trimmed.startsWith("<gradient")) {
            String open = trimmed.endsWith(">") ? trimmed : trimmed + ">";
            return open + content + "</gradient>";
        }
        // Gradient sem os <>: gradient:#A:#B
        if (trimmed.toLowerCase().startsWith("gradient")) {
            return "<" + trimmed + ">" + content + "</gradient>";
        }
        // Tag MiniMessage genérica com <> (ex: <#abc123> ou <color:#abc123>)
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            return trimmed + content;
        }
        // Hex puro: #FFFFFF ou #FFF
        if (trimmed.startsWith("#") && VALID_HEX.matcher(trimmed).matches()) {
            return "<" + trimmed + ">" + content;
        }
        // Hex sem #: FFFFFF
        if (trimmed.matches("[0-9a-fA-F]{6}")) {
            return "<#" + trimmed + ">" + content;
        }
        // Formato não reconhecido: retorna conteúdo sem cor (evita tag malformada)
        return content;
    }

    /** Formata tag do clan com cor: [TAG] */
    public static String formatClanTag(String colorHex, String tag) {
        return applyColor(colorHex, "[" + tag + "]");
    }

    /** Alias de {@link #applyColor(String, String)} — aplica a cor/gradiente do clan ao conteúdo. */
    public static String colorize(String colorTag, String content) {
        return applyColor(colorTag, content);
    }
}
