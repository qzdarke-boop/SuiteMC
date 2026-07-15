package com.psdk.clan;

import com.psdk.PSDK;
import com.psdk.util.ClanText;
import com.psdk.util.MessageFormatter;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ClanChatCommand implements CommandExecutor {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private final PSDK plugin;

    public ClanChatCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Apenas jogadores."));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(mm.deserialize("<#FF0000>Uso: /c <mensagem>"));
            return true;
        }
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não está em um clan!"));
            return true;
        }
        StringBuilder msg = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) msg.append(" ");
            msg.append(args[i]);
        }
        String text = msg.toString();
        if (!player.hasPermission("psdk.chat.hexbypass")) {
            text = com.psdk.util.TextUtil.stripAdvancedColors(text);
        }
        if (!player.hasPermission("psdk.colors")) {
            text = text.replace("\\", "\\\\").replace("<", "\\<");
        }
        broadcastClanMessage(plugin, clan, player, text);
        return true;
    }

    public static void broadcastClanMessage(PSDK plugin, Clan clan, Player sender, String message) {
        String tag = ClanText.resolvePlayerTag(sender);
        String clanTag = ClanText.formatClanTag(clan.getColorHex(), clan.getTag());
        String formatted = MessageFormatter.format(sender, "<reset><font:nexo:default>ꐠ</font><reset> " + clanTag + "<reset> " + tag + "<gray>: <white>" + message);

        for (ClanMember member : clan.getMembers()) {
            Player online = Bukkit.getPlayer(member.uuid());
            if (online != null) {
                online.sendMessage(mm.deserialize(formatted));
            }
        }
        for (Clan ally : plugin.getClanManager().getAllies(clan.getId())) {
            for (ClanMember member : ally.getMembers()) {
                Player online = Bukkit.getPlayer(member.uuid());
                if (online != null) {
                    online.sendMessage(mm.deserialize("<dark_gray>[ALLY] " + formatted));
                }
            }
        }
    }
}
