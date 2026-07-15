package com.psdk.social;

import com.psdk.PSDK;
import com.psdk.util.TextUtil;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code /live <link> <nick>} — anuncia que um jogador está em live, com o link
 * clicável (abre no navegador). O nome vem com prefix + suffix do LuckPerms
 * (via PlaceholderAPI); se a tag tiver %player_name%, vira o nick do jogador.
 */
public class LiveCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final boolean HAS_PAPI = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

    private final PSDK plugin;

    public LiveCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("psdk.live")) {
            sender.sendMessage(mm.deserialize("<#FF0000>Você não tem permissão para isso."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<#fcc850>Uso: /live <link> <nick>"));
            return true;
        }

        String link = normalizeLink(args[0]);
        String tag  = resolveTag(args[1]);

        Bukkit.broadcast(mm.deserialize(" "));
        Bukkit.broadcast(mm.deserialize(
                "<#FF0000><bold>● AO VIVO</bold> <reset>" + tag + " <#cbd1d7>está em <#FF0000><bold>LIVE</bold> <#cbd1d7>agora!"));
        Bukkit.broadcast(mm.deserialize(
                "<#cbd1d7>Assista clicando aqui: <#F5F528><click:open_url:'" + link + "'>"
                + "<hover:show_text:'<#cbd1d7>Abrir <#F5F528>" + link + "'><u>" + link + "</u></hover></click>"));
        Bukkit.broadcast(mm.deserialize(" "));

        // Som de destaque pra todo mundo notar o anúncio.
        for (Player pl : Bukkit.getOnlinePlayers()) {
            pl.playSound(pl.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        }

        sender.sendMessage(mm.deserialize("<#10fc46>Anúncio de live enviado!"));
        return true;
    }

    /** Monta a tag do jogador: prefix + nick + suffix (LuckPerms via PAPI), resolvendo %player_name%. */
    private String resolveTag(String nick) {
        Player p = Bukkit.getPlayerExact(nick);
        if (p == null) {
            // Offline: sem como puxar a tag do LuckPerms, usa só o nick.
            return "<white>" + nick;
        }
        String name = p.getName();
        String prefix = "", suffix = "";
        if (HAS_PAPI) {
            prefix = PlaceholderAPI.setPlaceholders(p, "%luckperms_prefix%");
            suffix = PlaceholderAPI.setPlaceholders(p, "%luckperms_suffix%");
        }
        // A tag do LP pode conter %player_name% — resolve pro nick (PAPI não re-resolve).
        prefix = prefix.replace("%player_name%", name).replace("%player%", name);
        suffix = suffix.replace("%player_name%", name).replace("%player%", name);
        // o nome já pode vir no prefix/suffix (via %player_name%); só adiciona se faltar (não duplica).
        String raw = prefix + suffix;
        if (!raw.contains(name)) raw = prefix + name + suffix;
        return TextUtil.legacyToMiniMessage(raw);
    }

    /** Garante que o link abra no navegador (prefixa https:// se faltar). */
    private String normalizeLink(String link) {
        if (!link.startsWith("http://") && !link.startsWith("https://")) {
            return "https://" + link;
        }
        return link;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("psdk.live")) return List.of();
        if (args.length == 2) {
            String pref = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(pref))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
