package com.psdk.thepit.topboard;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TopBoardCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final double REMOVE_RADIUS_SQ = 6 * 6;

    private final PSDK plugin;

    public TopBoardCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);

        if (name.equals("topboard")) {
            if (!sender.hasPermission("psdk.settopboard")) {
                sender.sendMessage(MM.deserialize("<#ff5555>Sem permissao."));
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                plugin.getTopBoardManager().despawnAll();
                plugin.getTopBoardManager().loadAll();
                plugin.getTopBoardManager().spawnAll();
                plugin.getTopQueryService().refreshAll();
                sender.sendMessage(MM.deserialize("<#10fc46>Top boards recarregados."));
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("reset")) {
                sender.sendMessage(MM.deserialize("<#848c94>Resetando tops semanal e mensal..."));
                plugin.getTopStatsTracker().resetAllTopStats(() ->
                        sender.sendMessage(MM.deserialize(
                                "<#10fc46>Top semanal e mensal resetado! Kills, coins e horas globais foram mantidos.")));
                return true;
            }
            sender.sendMessage(MM.deserialize("<#848c94>Uso: /topboard <reload|reset>"));
            return true;
        }

        if (name.equals("removetopboard")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MM.deserialize("<#ff5555>Apenas jogadores."));
                return true;
            }
            if (!player.hasPermission("psdk.settopboard")) {
                player.sendMessage(MM.deserialize("<#ff5555>Sem permissao."));
                return true;
            }
            TopBoard board = plugin.getTopBoardManager().findNearest(player.getLocation(), REMOVE_RADIUS_SQ);
            if (board == null) {
                player.sendMessage(MM.deserialize("<#ff5555>Nenhum top board proximo."));
                return true;
            }
            plugin.getTopBoardManager().removeBoard(board);
            player.sendMessage(MM.deserialize("<#10fc46>Top board removido."));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(MM.deserialize("<#ff5555>Apenas jogadores."));
            return true;
        }
        if (!player.hasPermission("psdk.settopboard")) {
            player.sendMessage(MM.deserialize("<#ff5555>Sem permissao."));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(MM.deserialize("<#848c94>Uso: /settopboard <kills|horas|coins>"));
            return true;
        }

        TopBoardType type = TopBoardType.fromId(args[0]);
        if (type == null) {
            player.sendMessage(MM.deserialize("<#ff5555>Tipo invalido. Use: kills, horas ou coins."));
            return true;
        }

        TopBoard board = plugin.getTopBoardManager().createBoard(type, player.getLocation());
        player.sendMessage(MM.deserialize(
                "<#10fc46>Holograma <#fcc850>" + type.getTitle()
                        + " <#10fc46>criado. Cada jogador ve o seu proprio ranking."));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                  @NotNull String alias, @NotNull String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("settopboard") && args.length == 1) {
            return filter(List.of("kills", "horas", "coins", "deaths", "level", "tokens",
                    "blocosquebrados", "blocoscolocados"), args[0]);
        }
        if (name.equals("topboard") && args.length == 1) {
            return filter(List.of("reload", "reset"), args[0]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String opt : options) {
            if (opt.startsWith(lower)) out.add(opt);
        }
        return out;
    }
}
