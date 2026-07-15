package com.psdk.thepit;

import com.psdk.PSDK;
import com.psdk.util.WandUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class ArenaCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public ArenaCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Este comando é apenas para jogadores."));
            return true;
        }
        if (!player.hasPermission("psdk.arena")) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não tem permissão para isso."));
            return true;
        }
        if (args.length == 0) { sendUsage(player); return true; }

        ArenaManager am = plugin.getArenaManager();
        ArenaData data = am.getArenaData();

        switch (args[0].toLowerCase()) {
            case "pos1" -> {
                data.setPos1(player.getLocation());
                player.sendMessage(mm.deserialize(
                        "<#10fc46>Pos1 definida em <#fcc850>"
                        + player.getLocation().getBlockX() + ", "
                        + player.getLocation().getBlockY() + ", "
                        + player.getLocation().getBlockZ() + "<#10fc46>."));
            }
            case "pos2" -> {
                data.setPos2(player.getLocation());
                player.sendMessage(mm.deserialize(
                        "<#10fc46>Pos2 definida em <#fcc850>"
                        + player.getLocation().getBlockX() + ", "
                        + player.getLocation().getBlockY() + ", "
                        + player.getLocation().getBlockZ() + "<#10fc46>."));
            }
            case "save" -> {
                if (!data.hasBothPositions()) {
                    player.sendMessage(mm.deserialize("<#FF0000>Você precisa definir pos1 e pos2 primeiro."));
                    return true;
                }
                am.saveSnapshot(data.getPos1(), data.getPos2(), player);
            }
            case "reset" -> {
                if (!data.isDefined()) {
                    player.sendMessage(mm.deserialize("<#FF0000>A arena ainda não foi configurada."));
                    return true;
                }
                if (am.isResetting()) {
                    player.sendMessage(mm.deserialize("<#FF0000>A arena já está sendo resetada."));
                    return true;
                }
                player.sendMessage(mm.deserialize("<#fcc850>Resetando arena..."));
                am.resetArena(() -> {
                    if (player.isOnline())
                        player.sendMessage(mm.deserialize("<#10fc46>Arena resetada com sucesso!"));
                });
            }
            case "wand" -> {
                player.getInventory().addItem(WandUtils.createWand());
                player.sendMessage(mm.deserialize("<#10fc46>Você recebeu a ferramenta de seleção de arena."));
            }
            case "info" -> {
                if (!data.isDefined()) {
                    player.sendMessage(mm.deserialize("<#FF0000>A arena ainda não foi configurada."));
                    return true;
                }
                player.sendMessage(mm.deserialize("<#efa600><bold>Arena Info:"));
                player.sendMessage(mm.deserialize("<#fcc850>Mundo: <#a4a4a4>" + (data.getPos1().getWorld() != null ? data.getPos1().getWorld().getName() : "?")));
                player.sendMessage(mm.deserialize("<#fcc850>Pos1: <#a4a4a4>" + data.getPos1().getBlockX() + ", " + data.getPos1().getBlockY() + ", " + data.getPos1().getBlockZ()));
                player.sendMessage(mm.deserialize("<#fcc850>Pos2: <#a4a4a4>" + data.getPos2().getBlockX() + ", " + data.getPos2().getBlockY() + ", " + data.getPos2().getBlockZ()));
                player.sendMessage(mm.deserialize("<#fcc850>Blocos: <#a4a4a4>" + data.getBlocksInDatabase()));
                player.sendMessage(mm.deserialize("<#fcc850>Volume: <#a4a4a4>" + data.getVolume()));
            }
            default -> sendUsage(player);
        }
        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage(mm.deserialize("<#efa600><bold>Comandos de Arena:"));
        player.sendMessage(mm.deserialize("<#fcc850>/arena pos1 <#a4a4a4>- Definir posicao 1"));
        player.sendMessage(mm.deserialize("<#fcc850>/arena pos2 <#a4a4a4>- Definir posicao 2"));
        player.sendMessage(mm.deserialize("<#fcc850>/arena save <#a4a4a4>- Salvar snapshot da arena"));
        player.sendMessage(mm.deserialize("<#fcc850>/arena reset <#a4a4a4>- Resetar arena (volta ao snapshot salvo)"));
        player.sendMessage(mm.deserialize("<#fcc850>/arena wand <#a4a4a4>- Ferramenta de selecao"));
        player.sendMessage(mm.deserialize("<#fcc850>/arena info <#a4a4a4>- Ver informacoes da arena"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("pos1", "pos2", "save", "reset", "wand", "info");
        return List.of();
    }
}
