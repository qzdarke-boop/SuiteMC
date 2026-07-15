package com.psdk.lootchest;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Comando admin de teste do sistema de baús: {@code /baus spawn <raridade>} e
 * {@code /baus clear}.
 */
public class LootChestCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private final PSDK plugin;

    public LootChestCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("psdk.baus")) {
            sender.sendMessage(mm.deserialize("<#FF0000>Sem permissão!"));
            return true;
        }
        if (args.length == 0) { usage(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "spawn" -> {
                if (args.length < 2) {
                    sender.sendMessage(mm.deserialize("<#fcc850>Uso: /baus spawn <normal|raro|epico|lendario>"));
                    return true;
                }
                LootRarity rarity = LootRarity.fromString(args[1]);
                if (rarity == null) {
                    sender.sendMessage(mm.deserialize("<#FF0000>Raridade inválida! Use: normal, raro, epico ou lendario"));
                    return true;
                }
                boolean ok = plugin.getLootChestManager().spawnChest(rarity);
                if (ok) {
                    sender.sendMessage(mm.deserialize("<#10fc46>Baú " + rarity.getColor() + rarity.getDisplayName() + " <#10fc46>spawnado!"));
                } else {
                    sender.sendMessage(mm.deserialize("<#FF0000>Não foi possível spawnar (sem local livre ou mundo '" + LootChestManager.WORLD + "' ausente)."));
                }
            }
            case "clear" -> {
                plugin.getLootChestManager().clearAll();
                sender.sendMessage(mm.deserialize("<#10fc46>Todos os baús foram removidos."));
            }
            default -> usage(sender);
        }
        return true;
    }

    private void usage(CommandSender s) {
        s.sendMessage(mm.deserialize("<#a4a4a4><strikethrough>----</strikethrough> <#fcc850><bold>Baús Admin <#a4a4a4><strikethrough>----"));
        s.sendMessage(mm.deserialize("<#fcc850>/baus spawn <normal|raro|epico|lendario>"));
        s.sendMessage(mm.deserialize("<#fcc850>/baus clear <#a4a4a4>- remove todos os baús ativos"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("psdk.baus")) return List.of();
        if (args.length == 1) return filter(List.of("spawn", "clear"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn"))
            return filter(List.of("normal", "raro", "epico", "lendario"), args[1]);
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        return options.stream().filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase())).collect(Collectors.toList());
    }
}
