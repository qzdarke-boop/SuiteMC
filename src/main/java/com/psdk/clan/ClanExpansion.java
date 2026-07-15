package com.psdk.clan;

import com.psdk.PSDK;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Placeholders %psdk_clan_*%. Leitura EXCLUSIVA de caches em memória
 * (ClanManager.getCachedClanByPlayer e ClanTopQueryService) — placeholders rodam
 * em threads assíncronas e nunca podem tocar no banco.
 */
public class ClanExpansion extends PlaceholderExpansion {

    private final PSDK plugin;

    public ClanExpansion(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "psdk"; }
    @Override public @NotNull String getAuthor() { return "PSDK"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (!params.startsWith("clan_")) return null;
        String key = params.substring(5);

        // Tops: clan_top_<members|kills|treasury>_<pos>_<tag|name|value>
        if (key.startsWith("top_")) {
            return resolveTop(key.substring(4));
        }

        if (player == null) return "";
        Clan clan = plugin.getClanManager().getCachedClanByPlayer(player.getUniqueId());
        if (clan == null) {
            if (!plugin.getClanManager().isClanStateKnown(player.getUniqueId())) {
                plugin.getClanManager().warmPlayerClan(player.getUniqueId());
            }
            return "";
        }

        return switch (key) {
            case "tag" -> clan.getTag();
            case "name" -> clan.getName();
            case "role" -> resolveRole(clan, player);
            case "members" -> String.valueOf(clan.getMembers().size());
            case "treasury" -> String.format("%,.0f", plugin.getClanTopQueryService().getTreasury(clan.getId())).replace(",", ".");
            case "kills" -> String.valueOf(plugin.getClanTopQueryService().getKills(clan.getId()));
            case "description" -> clan.getDescription();
            default -> null;
        };
    }

    private String resolveRole(Clan clan, OfflinePlayer player) {
        for (ClanMember m : clan.getMembers()) {
            if (m.uuid().equals(player.getUniqueId())) {
                // Cargos são personalizados por clan: o nome exibido vive em clan_members.role.
                return "lider".equals(m.role()) ? "Líder" : m.role();
            }
        }
        return "";
    }

    private String resolveTop(String spec) {
        // spec: <members|kills|treasury>_<pos>_<tag|name|value>
        String[] parts = spec.split("_");
        if (parts.length != 3) return null;

        ClanTopQueryService.ClanTopType type = switch (parts[0]) {
            case "members" -> ClanTopQueryService.ClanTopType.MEMBERS;
            case "kills" -> ClanTopQueryService.ClanTopType.KILLS;
            case "treasury" -> ClanTopQueryService.ClanTopType.TREASURY;
            default -> null;
        };
        if (type == null) return null;

        int pos;
        try { pos = Integer.parseInt(parts[1]); } catch (NumberFormatException e) { return null; }
        if (pos < 1 || pos > ClanTopQueryService.LIMIT) return null;

        List<ClanTopQueryService.ClanTopEntry> top = plugin.getClanTopQueryService().getTop(type);
        if (pos > top.size()) return "-";
        ClanTopQueryService.ClanTopEntry entry = top.get(pos - 1);

        return switch (parts[2]) {
            case "tag" -> entry.tag();
            case "name" -> entry.name();
            case "value" -> type == ClanTopQueryService.ClanTopType.TREASURY
                    ? String.format("%,.0f", entry.value()).replace(",", ".")
                    : String.valueOf((long) entry.value());
            default -> null;
        };
    }
}
