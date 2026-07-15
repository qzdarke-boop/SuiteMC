package com.psdk.staff;

import com.psdk.PSDK;
import com.psdk.util.OfflinePlayerDataUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /invsee <jogador>} — abre o inventário completo de outro jogador (staff).
 *
 * <ul>
 *   <li><b>Online</b>: inventário editável (mochila, hotbar, armadura e offhand).</li>
 *   <li><b>Offline</b>: snapshot somente leitura do playerdata.</li>
 * </ul>
 */
public class InvSeeCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PSDK plugin;

    public InvSeeCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MM.deserialize("<#e22c27>Apenas jogadores podem usar este comando."));
            return true;
        }
        if (!player.hasPermission("psdk.invsee")) {
            player.sendMessage(MM.deserialize("<#e22c27>Você não tem permissão para isso."));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(MM.deserialize("<#fcc850>Uso: /invsee <jogador>"));
            return true;
        }

        Player online = Bukkit.getPlayerExact(args[0]);
        if (online != null) {
            if (online.equals(player)) {
                player.sendMessage(MM.deserialize("<#e22c27>Você não pode usar invsee em si mesmo."));
                return true;
            }
            InvSeeGUI gui = InvSeeGUI.create(online);
            player.openInventory(gui.getInventory());
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.6f, 1.2f);
            player.sendMessage(MM.deserialize("<#10fc46>Editando o inventário de <#fcc850>"
                    + online.getName() + "<#10fc46>."));
            return true;
        }

        OfflinePlayer off = resolveOffline(args[0]);
        if (off == null || off.getName() == null || !off.hasPlayedBefore()) {
            player.sendMessage(MM.deserialize("<#e22c27>Esse jogador nunca entrou no servidor."));
            return true;
        }

        String targetName = off.getName();
        player.sendMessage(MM.deserialize("<#848c94>Carregando o inventário offline de <#fcc850>"
                + targetName + "<#848c94>..."));

        OfflinePlayerDataUtil.loadInventoryAsync(plugin, off.getUniqueId()).thenAccept(contents ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;

                    // Never open a stale disk snapshot if the target joined during the read.
                    Player joined = Bukkit.getPlayer(off.getUniqueId());
                    if (joined != null && joined.isOnline()) {
                        InvSeeGUI liveGui = InvSeeGUI.create(joined);
                        player.openInventory(liveGui.getInventory());
                        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.6f, 1.2f);
                        player.sendMessage(MM.deserialize("<#10fc46>O jogador entrou durante o carregamento; "
                                + "abrindo o inventário online de <#fcc850>" + joined.getName() + "<#10fc46>."));
                        return;
                    }

                    if (contents == null) {
                        player.sendMessage(MM.deserialize("<#e22c27>Não consegui ler o inventário offline de <#fcc850>"
                                + targetName + "<#e22c27>. Veja o erro detalhado no console."));
                        return;
                    }

                    InvSeeGUI gui = InvSeeGUI.createOffline(off.getUniqueId(), targetName, contents);
                    player.openInventory(gui.getInventory());
                    player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.6f, 0.9f);
                    player.sendMessage(MM.deserialize("<#10fc46>Editando o inventário de <#fcc850>" + targetName
                            + " <gray>(offline — salva ao fechar)<#10fc46>."));
                }));
        return true;
    }

    private OfflinePlayer resolveOffline(String name) {
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(name)) return op;
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("psdk.invsee")) return List.of();
        if (args.length == 1) {
            String pref = args[0].toLowerCase();
            List<String> out = new ArrayList<>();
            for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                String n = op.getName();
                if (n != null && n.toLowerCase().startsWith(pref) && !out.contains(n)) out.add(n);
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(pref) && !out.contains(p.getName())) out.add(p.getName());
            }
            if (out.size() > 100) return out.subList(0, 100);
            return out;
        }
        return List.of();
    }
}
