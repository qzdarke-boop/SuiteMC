package com.psdk.staff;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class StaffCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private final PSDK plugin;

    public StaffCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    /**
     * Envia uma mensagem do chat de staff (/sc) para todos os membros da equipe
     * online (permissão {@code psdk.staff.chat}) e para o console. O texto do
     * jogador é escapado para impedir injeção de tags MiniMessage.
     */
    public static void broadcastStaffMessage(Player sender, String message) {
        var component = mm.deserialize(
                "<#5c6cfa>[STAFF] <white>" + sender.getName() + "<gray>: <#dcdcdc>"
                        + mm.escapeTags(message));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("psdk.staff.chat")) p.sendMessage(component);
        }
        Bukkit.getConsoleSender().sendMessage(component);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<color:#FF0000>Apenas jogadores."));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(mm.deserialize("<color:#FF0000>Uso: /staff lastcmd"));
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "lastcmd" -> handleLastCmd(player, args);
            default -> player.sendMessage(mm.deserialize("<color:#FF0000>Uso: /staff lastcmd"));
        }
        return true;
    }

    private void handleLastCmd(Player player, String[] args) {
        if (!player.hasPermission("psdk.staff.lastcmd")) {
            player.sendMessage(mm.deserialize("<color:#FF0000>Sem permissão."));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(mm.deserialize("<color:#FF0000>Uso: /staff lastcmd <nick>"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        String targetName = target.getName() != null ? target.getName() : args[1];
        List<StaffManager.CmdEntry> cmds =
                plugin.getStaffManager().getCommandLogNewestFirst(target.getUniqueId());
        if (cmds.isEmpty()) {
            player.sendMessage(mm.deserialize("<gray>Nenhum comando registrado para <white>"
                    + targetName + "<gray>."));
            return;
        }
        LastCmdGUI gui = LastCmdGUI.build(target.getUniqueId(), targetName, cmds, 0);
        player.openInventory(gui.getInventory());
        player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.2f);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("lastcmd")) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("lastcmd")) {
            String p = args[1].toLowerCase();
            for (Player pl : Bukkit.getOnlinePlayers()) {
                if (pl.getName().toLowerCase().startsWith(p)) out.add(pl.getName());
            }
        }
        return out;
    }
}
