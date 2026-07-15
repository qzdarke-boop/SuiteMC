package com.psdk.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextUtil {

    private static final Pattern HEX_LEGACY_PATTERN =
            Pattern.compile("(?i)[§&]x([§&][0-9a-f]){6}");

    private TextUtil() {}

    public static String legacyToMiniMessage(String input) {
        if (input == null) return "";

        Matcher hexMatcher = HEX_LEGACY_PATTERN.matcher(input);
        StringBuffer hexResult = new StringBuffer();
        while (hexMatcher.find()) {
            String digits = hexMatcher.group().replaceAll("[§&xX]", "");
            hexMatcher.appendReplacement(hexResult, Matcher.quoteReplacement("<#" + digits + ">"));
        }
        hexMatcher.appendTail(hexResult);
        input = hexResult.toString();

        StringBuilder sb = new StringBuilder(input.length());
        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if ((chars[i] == '§' || chars[i] == '&') && i + 1 < chars.length) {
                char next = Character.toLowerCase(chars[i + 1]);
                String tag = codeToTag(next);
                if (tag != null) {
                    sb.append(tag);
                    i++;
                    continue;
                }
            }
            sb.append(chars[i]);
        }
        return sb.toString();
    }

    private static String codeToTag(char code) {
        return switch (code) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'l' -> "<bold>";
            case 'o' -> "<italic>";
            case 'n' -> "<underlined>";
            case 'm' -> "<strikethrough>";
            case 'k' -> "<obfuscated>";
            case 'r' -> "<reset>";
            default -> null;
        };
    }

    private static final Pattern MM_HEX_TAG =
            Pattern.compile("<#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})>", Pattern.CASE_INSENSITIVE);
    private static final Pattern MM_GRADIENT_OPEN =
            Pattern.compile("<gradient:[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern MM_COLOR_HEX_TAG =
            Pattern.compile("<color:#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})>", Pattern.CASE_INSENSITIVE);
    private static final Pattern MM_RAINBOW_OPEN =
            Pattern.compile("<rainbow(?:[:][^>]*)?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEGACY_HEX_AMP =
            Pattern.compile("&#([0-9a-fA-F]{6})", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEGACY_HEX_SECTION =
            Pattern.compile("§#([0-9a-fA-F]{6})", Pattern.CASE_INSENSITIVE);

    /** Remove hex, gradientes e rainbow — quem não tem psdk.chat.hexbypass não pode usar no chat. */
    public static String stripAdvancedColors(String text) {
        if (text == null || text.isEmpty()) return text;

        text = MM_HEX_TAG.matcher(text).replaceAll("");
        text = MM_COLOR_HEX_TAG.matcher(text).replaceAll("");
        text = MM_GRADIENT_OPEN.matcher(text).replaceAll("");
        text = text.replace("</gradient>", "");
        text = MM_RAINBOW_OPEN.matcher(text).replaceAll("");
        text = text.replace("</rainbow>", "");
        text = text.replace("</color>", "");
        text = LEGACY_HEX_AMP.matcher(text).replaceAll("");
        text = LEGACY_HEX_SECTION.matcher(text).replaceAll("");
        text = HEX_LEGACY_PATTERN.matcher(text).replaceAll("");
        return text;
    }
}
