package com.psdk.thepit.topboard;

import com.psdk.util.ClanText;
import com.psdk.util.PlayerDisplayFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Formatação unificada dos rankings: (posição) (clã) (nick formatado) (valor).
 * O nick vem de {@link PlayerDisplayFormat} — prefix + nome + suffix do LP com
 * cores e gradientes preservados (VIPs com nick colorido aparecem corretos).
 */
public final class TopBoardFormat {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Component SPACE = Component.space();

    private TopBoardFormat() {}

    // ── Linhas ──────────────────────────────────────────────────────────────

    /** (posição) (clã) (nick formatado) (valor) */
    public static Component buildRankLine(int position, TopEntry entry, String value) {
        return Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(positionPart(position))
                .append(clanPart(entry.clanTag(), entry.clanColor()))
                .append(playerPrefixPart(entry))
                .append(valuePart(value));
    }

    /** Igual a {@link #buildRankLine} mas com quebra de linha (holograma). */
    public static Component buildHologramRankLine(int position, TopEntry entry, String value) {
        return buildRankLine(position, entry, value).append(Component.newline());
    }

    public static Component buildEmptyLine(int position) {
        return positionPart(position)
                .append(MM.deserialize("<!italic><reset><#848c94>Vago"));
    }

    public static Component buildHologramEmptyLine(int position) {
        return buildEmptyLine(position).append(Component.newline());
    }

    // ── Partes da linha ─────────────────────────────────────────────────────

    private static Component positionPart(int position) {
        if (position <= 3) {
            return MM.deserialize("<!italic><reset><#848c94><bold>" + position + "</bold><#848c94> - ");
        }
        return MM.deserialize("<!italic><reset><#848c94>" + position + " - ");
    }

    private static Component clanPart(String clanTag, String clanColor) {
        if (clanTag == null || clanTag.isBlank()) return Component.empty();
        return MM.deserialize("<reset>" + ClanText.formatClanTag(clanColor, clanTag.strip())).append(SPACE);
    }

    /**
     * Isolates the LuckPerms prefix from the gray rank-position style. Unset
     * colors now fall back to white, matching the original chat rendering.
     */
    private static Component playerPrefixPart(TopEntry entry) {
        return Component.empty()
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false)
                .append(PlayerDisplayFormat.displayName(entry.uuid(), entry.name()));
    }

    private static Component valuePart(String value) {
        return MM.deserialize("<!italic><#cbd1d7>: " + value);
    }

    // ── Valores ─────────────────────────────────────────────────────────────

    public static String formatValue(TopBoardType type, double value) {
        if (type == TopBoardType.HOURS) {
            long totalMinutes = (long) (value / 60_000L);
            long hours = totalMinutes / 60;
            long minutes = totalMinutes % 60;
            return hours + "h " + minutes + "m";
        }
        return formatValue(value);
    }

    public static String formatValue(double value) {
        return String.format("%,d", (long) value).replace(",", ".");
    }
}
