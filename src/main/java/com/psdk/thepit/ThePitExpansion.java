package com.psdk.thepit;

import com.psdk.PSDK;
import com.psdk.clan.Clan;
import com.psdk.economy.EconomyManager;
import com.psdk.colina.ColinaManager;
import com.psdk.thepit.topboard.TopBoardType;
import com.psdk.thepit.topboard.TopPeriod;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ThePitExpansion extends PlaceholderExpansion {

    private final PSDK plugin;

    public ThePitExpansion(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "thepit"; }
    @Override public @NotNull String getAuthor()     { return "PSDK"; }
    @Override public @NotNull String getVersion()    { return plugin.getDescription().getVersion(); }
    @Override public boolean persist()               { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        EconomyManager eco = plugin.getEconomyManager();
        String lower = params.toLowerCase();

        if (lower.equals("coins")) {
            if (player == null) return "0";
            return com.psdk.util.NumberUtil.abbrev(eco.getCoins(player.getUniqueId()));
        }
        if (lower.equals("tokens")) {
            if (player == null) return "0";
            return com.psdk.util.NumberUtil.abbrev(eco.getTokens(player.getUniqueId()));
        }

        if (lower.equals("colina_dono")) {
            ColinaManager cm = plugin.getColinaManager();
            return cm.isActive() ? cm.getDono() : "Desativada";
        }
        if (lower.equals("colina_status")) {
            ColinaManager cm = plugin.getColinaManager();
            if (!cm.isActive()) return "&cDesativada";
            String d = cm.getDono();
            if (d.equals("Sem Dono")) return "&7Sem Dono";
            if (d.equals("Disputa!")) return "&cDisputa!";
            return "&a" + d;
        }
        if (lower.equals("colina_coords")) {
            ColinaManager cm = plugin.getColinaManager();
            if (!cm.isActive()) return "N/A";
            return String.format("%.0f, %.0f, %.0f", cm.getX(), cm.getY(), cm.getZ());
        }

        if (lower.equals("online")) {
            return String.valueOf(plugin.getServer().getOnlinePlayers().size());
        }
        if (lower.equals("max_online")) {
            return String.valueOf(plugin.getServer().getMaxPlayers());
        }

        if (lower.startsWith("top_")) {
            return handleTop(lower.substring(4));
        }

        if (lower.equals("prefixafk")) {
            if (player == null || !player.isOnline()) return "";
            return plugin.getAfkManager().isAfk(player.getUniqueId()) ? "<font:nexo:default>㊁</font> " : "";
        }

        if (lower.equals("clantag")) {
            if (player == null) return "";
            // UnlimitedNametags/TAB pedem este placeholder de threads ASYNC. A conexão
            // SQLite é única e não thread-safe, então NUNCA consultamos o banco aqui:
            // lemos só do cache em memória e agendamos o carregamento no main thread.
            Clan clan = plugin.getClanManager().getCachedClanByPlayer(player.getUniqueId());
            if (clan == null) {
                plugin.getClanManager().warmPlayerClan(player.getUniqueId());
                return "";
            }
            // Converte a tag (MiniMessage: hex/gradiente) para códigos legacy (§),
            // que o tab/nametag e o scoreboard renderizam — MiniMessage cru mostraria
            // os caracteres "<>" literais nesses contextos (diferente do chat).
            String mmTag = com.psdk.util.ClanText.formatClanTag(clan.getColorHex(), clan.getTag());
            Component comp = MiniMessage.miniMessage().deserialize(mmTag);
            return LegacyComponentSerializer.legacySection().serialize(comp) + " ";
        }

        if (player == null || !player.isOnline()) return "";
        Player p = player.getPlayer();
        if (p == null) return "";

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(p);
        if (data == null) return "";

        LevelManager lm = plugin.getLevelManager();
        int level = lm.getLevel(data.getKills());

        return switch (lower) {
            case "level"      -> String.valueOf(level);
            case "kills"      -> String.valueOf(data.getKills());
            case "deaths"     -> String.valueOf(data.getDeaths());
            case "next_kills" -> String.valueOf(lm.killsForNextLevel(level));
            case "killstreak" -> String.valueOf(data.getKills());
            case "kdr" -> {
                int kills = data.getKills();
                int deaths = data.getDeaths();
                yield deaths == 0 ? String.valueOf(kills) : String.format("%.2f", (double) kills / deaths);
            }
            case "level_bar" -> lm.buildBar(data.getKills(), level);
            case "level_progress" -> {
                int prev = lm.killsForLevel(level);
                int next = lm.killsForNextLevel(level);
                int prog = Math.max(0, data.getKills() - prev);
                int needed = Math.max(1, next - prev);
                yield prog + "/" + needed;
            }
            case "level_percent" -> {
                int prev2 = lm.killsForLevel(level);
                int next2 = lm.killsForNextLevel(level);
                int prog2 = Math.max(0, data.getKills() - prev2);
                int needed2 = Math.max(1, next2 - prev2);
                yield String.format("%.0f", (double) prog2 / needed2 * 100) + "%";
            }
            case "in_combat" -> plugin.getCombatManager().isInCombat(p) ? "true" : "false";
            case "max_level" -> String.valueOf(LevelManager.MAX_LEVEL);
            default -> null;
        };
    }

    private String handleTop(String param) {
        String[] parts = param.split("_");
        if (parts.length >= 3) {
            TopPeriod period = TopPeriod.parseId(parts[0]);
            TopBoardType boardType = TopBoardType.fromId(parts[1]);
            if (period != null && boardType != null) {
                return handlePeriodTop(period, boardType, parts);
            }
        }

        if (parts.length < 2) return null;

        String type = parts[0];
        int index;
        try {
            index = Integer.parseInt(parts[1]) - 1;
        } catch (NumberFormatException e) {
            return null;
        }
        if (index < 0 || index > 9) return null;

        boolean wantValue = parts.length >= 3 && parts[2].equals("value");
        boolean wantName  = parts.length >= 3 && parts[2].equals("name");

        EconomyManager eco = plugin.getEconomyManager();
        List<EconomyManager.TopEntry> top = switch (type) {
            case "tokens" -> eco.getTopTokens();
            case "coins"  -> eco.getTopCoins();
            case "kills"  -> eco.getTopKills();
            case "deaths" -> eco.getTopDeaths();
            default -> null;
        };

        if (top == null || index >= top.size()) {
            return wantValue ? "0" : "---";
        }

        EconomyManager.TopEntry entry = top.get(index);
        if (wantValue) return com.psdk.util.NumberUtil.abbrev(entry.value());
        if (wantName)  return entry.name();
        return entry.name();
    }

    private String handlePeriodTop(TopPeriod period, TopBoardType boardType, String[] parts) {
        int rank;
        try {
            rank = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (rank < 1 || rank > com.psdk.thepit.topboard.TopQueryService.MAX_RANK) return null;

        boolean wantValue = parts.length >= 4 && parts[3].equals("value");
        boolean wantName  = parts.length >= 4 && parts[3].equals("name");

        com.psdk.thepit.topboard.TopEntry entry =
                plugin.getTopQueryService().getEntry(boardType, period, rank);
        if (entry == null || entry.value() <= 0) {
            return wantValue ? "0" : "---";
        }
        if (wantValue) {
            if (boardType == TopBoardType.HOURS) {
                return com.psdk.thepit.topboard.TopBoardFormat.formatValue(boardType, entry.value());
            }
            return com.psdk.util.NumberUtil.abbrev(entry.value());
        }
        if (wantName) return entry.name();
        return entry.name();
    }
}