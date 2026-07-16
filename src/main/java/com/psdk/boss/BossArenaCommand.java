package com.psdk.boss;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code /bossarena <pos1|pos2|save|regen|info>} — define e gerencia a arena do boss.
 *  pos1/pos2 = cantos do cubo · save = snapshot pro regen · regen = regenerar agora.
 */
public class BossArenaCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public BossArenaCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("psdk.boss.admin")) {
            sender.sendMessage(mm.deserialize("<#FF0000>Você não tem permissão para isso."));
            return true;
        }
        BossArenaManager am = plugin.getBossArenaManager();
        if (args.length == 0) {
            sender.sendMessage(mm.deserialize("<#efa600><bold>Arena do Boss:"));
            sender.sendMessage(mm.deserialize("  <#6817ff>/bossarena pos1 <#a4a4a4>- canto 1 (sua posição)"));
            sender.sendMessage(mm.deserialize("  <#6817ff>/bossarena pos2 <#a4a4a4>- canto 2 (sua posição)"));
            sender.sendMessage(mm.deserialize("  <#6817ff>/bossarena save <#a4a4a4>- salva o estado limpo (pro regen)"));
            sender.sendMessage(mm.deserialize("  <#6817ff>/bossarena regen <#a4a4a4>- regenera agora"));
            sender.sendMessage(mm.deserialize("  <#6817ff>/bossarena info <#a4a4a4>- status"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "pos1" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(mm.deserialize("<#FF0000>Use no jogo.")); return true; }
                am.setCorner1(p.getLocation());
                p.sendMessage(mm.deserialize("<#10fc46>Canto 1 da arena definido!"));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
            }
            case "pos2" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(mm.deserialize("<#FF0000>Use no jogo.")); return true; }
                am.setCorner2(p.getLocation());
                p.sendMessage(mm.deserialize("<#10fc46>Canto 2 da arena definido!"));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
            }
            case "save" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(mm.deserialize("<#FF0000>Use no jogo.")); return true; }
                if (!am.isDefined()) {
                    sender.sendMessage(mm.deserialize("<#FF0000>Defina os 2 cantos primeiro (/bossarena pos1 e pos2)."));
                    return true;
                }
                am.saveSnapshot(p);   // async (streaming p/ SQLite, sem limite) — manda as mensagens sozinho
            }
            case "regen" -> {
                if (!am.hasSnapshot()) {
                    sender.sendMessage(mm.deserialize("<#FF0000>Nenhum snapshot salvo. Use /bossarena save primeiro."));
                    return true;
                }
                am.regen();
                sender.sendMessage(mm.deserialize("<#10fc46>Regenerando a arena..."));
            }
            case "info" -> {
                sender.sendMessage(mm.deserialize("<#cbd1d7>Arena definida: <#6817ff>" + am.isDefined()));
                sender.sendMessage(mm.deserialize("<#cbd1d7>Snapshot salvo: <#6817ff>" + am.hasSnapshot()
                        + " <#a4a4a4>(" + am.volume() + " blocos)"));
            }
            default -> sender.sendMessage(mm.deserialize("<#FF0000>Use /bossarena pos1|pos2|save|regen|info."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("psdk.boss.admin")) return List.of();
        if (args.length == 1) {
            return List.of("pos1", "pos2", "save", "regen", "info").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        return List.of();
    }
}
