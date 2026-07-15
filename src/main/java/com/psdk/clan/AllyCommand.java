package com.psdk.clan;

import com.psdk.PSDK;
import com.psdk.util.ClanText;
import com.psdk.util.MessageFormatter;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

/**
 * /ally — Gerencia alianças e chat de aliança.
 *
 * Subcomandos:
 *   /ally <tag>               → aliar com o clan (líder)
 *   /ally chat <mensagem>     → chat de aliança
 *   /ally remove <tag>        → remover aliança (líder)
 *   /ally list                → listar aliados do seu clan
 *
 * Atalho: /a <mensagem> → /ally chat <mensagem>
 */
public class AllyCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private final PSDK plugin;

    public AllyCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Apenas jogadores."));
            return true;
        }

        if (args.length == 0) { showHelp(player); return true; }

        String sub = args[0].toLowerCase();

        // /ally chat <msg> ou /ally c <msg>
        if (sub.equals("chat") || sub.equals("c")) {
            if (args.length < 2) {
                player.sendMessage(mm.deserialize("<#FF0000>Uso: /ally chat <mensagem>"));
                return true;
            }
            String msg = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            if (!player.hasPermission("psdk.chat.hexbypass")) {
                msg = com.psdk.util.TextUtil.stripAdvancedColors(msg);
            }
            if (!player.hasPermission("psdk.colors")) {
                msg = msg.replace("\\", "\\\\").replace("<", "\\<");
            }
            sendAllyChat(plugin, player, msg);
            return true;
        }

        // /ally list
        if (sub.equals("list") || sub.equals("lista")) {
            listAllies(player);
            return true;
        }

        // /ally remove <tag>
        if (sub.equals("remove") || sub.equals("remover")) {
            if (args.length < 2) {
                player.sendMessage(mm.deserialize("<#FF0000>Uso: /ally remove <tag>"));
                return true;
            }
            removeAlly(player, args[1]);
            return true;
        }

        // /ally <tag>  → aliar
        addAlly(player, args[0]);
        return true;
    }

    // ════════════════════════════════════════════════════════
    //  SUBCOMANDOS
    // ════════════════════════════════════════════════════════

    private void addAlly(Player player, String tag) {
        ClanManager cm = plugin.getClanManager();
        Clan clan = cm.getClanByPlayer(player.getUniqueId());
        if (clan == null) { player.sendMessage(mm.deserialize("<#FF0000>Você não está em um clan!")); return; }
        if (!clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(mm.deserialize("<#FF0000>Apenas o líder pode gerenciar aliados!")); return;
        }
        Clan target = cm.getClanByTag(tag.toUpperCase());
        if (target == null) { player.sendMessage(mm.deserialize("<#FF0000>Clan não encontrado.")); return; }
        if (target.getId() == clan.getId()) { player.sendMessage(mm.deserialize("<#FF0000>Não pode aliar ao próprio clan.")); return; }

        if (cm.addAlly(clan.getId(), target.getId())) {
            player.sendMessage(mm.deserialize("<#10fc46>Aliança formada com " + ClanText.formatClanTag(target.getColorHex(), target.getTag()) + "<#10fc46>!"));
            // Notifica o outro clan
            String clanTagFormatted = ClanText.formatClanTag(clan.getColorHex(), clan.getTag());
            for (ClanMember m : target.getMembers()) {
                Player online = Bukkit.getPlayer(m.uuid());
                if (online != null) {
                    online.sendMessage(mm.deserialize("<#fcc850>O clan " + clanTagFormatted + " <#fcc850>formou aliança com vocês!"));
                }
            }
        } else {
            player.sendMessage(mm.deserialize("<#FF0000>Não foi possível formar aliança (já são aliados?)."));
        }
    }

    private void removeAlly(Player player, String tag) {
        ClanManager cm = plugin.getClanManager();
        Clan clan = cm.getClanByPlayer(player.getUniqueId());
        if (clan == null) { player.sendMessage(mm.deserialize("<#FF0000>Você não está em um clan!")); return; }
        if (!clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(mm.deserialize("<#FF0000>Apenas o líder pode gerenciar aliados!")); return;
        }
        Clan target = cm.getClanByTag(tag.toUpperCase());
        if (target == null) { player.sendMessage(mm.deserialize("<#FF0000>Clan não encontrado.")); return; }

        if (cm.removeAlly(clan.getId(), target.getId())) {
            player.sendMessage(mm.deserialize("<gray>Aliança removida."));
        } else {
            player.sendMessage(mm.deserialize("<#FF0000>Não foi possível remover aliança."));
        }
    }

    private void listAllies(Player player) {
        ClanManager cm = plugin.getClanManager();
        Clan clan = cm.getClanByPlayer(player.getUniqueId());
        if (clan == null) { player.sendMessage(mm.deserialize("<#FF0000>Você não está em um clan!")); return; }

        List<Clan> allies = cm.getAllies(clan.getId());
        if (allies.isEmpty()) {
            player.sendMessage(mm.deserialize("<#848c94>Seu clan não tem aliados. Use <white>/ally <tag> <#848c94>para aliar."));
            return;
        }
        player.sendMessage(mm.deserialize("<#fcc850><bold>Aliados do seu clan:"));
        for (Clan ally : allies) {
            long online = ally.getMembers().stream()
                    .filter(m -> Bukkit.getPlayer(m.uuid()) != null).count();
            player.sendMessage(mm.deserialize("  " + ClanText.formatClanTag(ally.getColorHex(), ally.getTag())
                    + " <#848c94>" + ally.getName() + " <dark_gray>(" + online + "/" + ally.getMembers().size() + " online)"));
        }
    }

    // ════════════════════════════════════════════════════════
    //  CHAT DE ALIANÇA (estático, chamado por /a também)
    // ════════════════════════════════════════════════════════

    public static void sendAllyChat(PSDK plugin, Player player, String message) {
        ClanManager cm = plugin.getClanManager();
        Clan clan = cm.getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<#FF0000>Você não está em um clan!"));
            return;
        }

        List<Clan> allies = cm.getAllies(clan.getId());
        if (allies.isEmpty()) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<#fcc850>Seu clan não tem aliados no momento."));
            return;
        }

        String playerTag = ClanText.resolvePlayerTag(player);
        String clanTag   = ClanText.formatClanTag(clan.getColorHex(), clan.getTag());
        String formatted = MessageFormatter.format(player,
                "<reset><gray>[ALIANÇA] <reset>" + clanTag + "<reset> " + playerTag + "<gray>: <white>" + message);

        // Próprio clan
        for (ClanMember member : clan.getMembers()) {
            Player online = Bukkit.getPlayer(member.uuid());
            if (online != null) online.sendMessage(MiniMessage.miniMessage().deserialize(formatted));
        }
        // Clans aliados
        for (Clan ally : allies) {
            for (ClanMember member : ally.getMembers()) {
                Player online = Bukkit.getPlayer(member.uuid());
                if (online != null) online.sendMessage(MiniMessage.miniMessage().deserialize(formatted));
            }
        }
    }

    // ════════════════════════════════════════════════════════
    //  TAB COMPLETE + HELP
    // ════════════════════════════════════════════════════════

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (args.length == 1) {
            List<String> subs = List.of("chat", "remove", "list");
            List<String> result = new java.util.ArrayList<>(subs.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList()));
            // Adiciona tags de clans para /ally <tag>
            plugin.getClanManager().getAllClans().stream()
                    .map(Clan::getTag).filter(t -> t.toLowerCase().startsWith(args[0].toLowerCase()))
                    .forEach(result::add);
            return result;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
            if (clan == null) return List.of();
            return plugin.getClanManager().getAllies(clan.getId()).stream()
                    .map(Clan::getTag).filter(t -> t.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private void showHelp(Player player) {
        player.sendMessage(mm.deserialize("<#fcc850><bold>Comandos de Aliança:"));
        player.sendMessage(mm.deserialize("  <white>/ally <tag>              <#848c94>Aliar com clan"));
        player.sendMessage(mm.deserialize("  <white>/ally remove <tag>       <#848c94>Remover aliança"));
        player.sendMessage(mm.deserialize("  <white>/ally list               <#848c94>Ver aliados"));
        player.sendMessage(mm.deserialize("  <white>/ally chat <msg>         <#848c94>Chat de aliança"));
        player.sendMessage(mm.deserialize("  <white>/a <msg>                 <#848c94>Atalho para chat"));
    }
}
