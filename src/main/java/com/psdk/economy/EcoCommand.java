package com.psdk.economy;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class EcoCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public EcoCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("psdk.eco")) {
            sender.sendMessage(mm.deserialize("<#FF0000>Você não tem permissão para isso."));
            return true;
        }
        if (args.length < 3) { sendHelp(sender); return true; }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Jogador não encontrado."));
            return true;
        }

        double amount;
        try { amount = Double.parseDouble(args[2]); }
        catch (NumberFormatException e) {
            sender.sendMessage(mm.deserialize("<#FF0000>Quantidade inválida!"));
            return true;
        }
        // Rejeita NaN/Infinity/negativos — valores que corromperiam o saldo no banco.
        if (!Double.isFinite(amount) || amount <= 0) {
            sender.sendMessage(mm.deserialize("<#FF0000>Quantidade deve ser um número positivo!"));
            return true;
        }

        EconomyManager eco = plugin.getEconomyManager();

        switch (args[0].toLowerCase()) {
            case "givecoins" -> {
                eco.addCoins(target.getUniqueId(), target.getName(), amount);
                double bal = eco.getCoins(target.getUniqueId());
                sender.sendMessage(mm.deserialize("<#10fc46>+" + fmt(amount) +
                        " coins para <#fcc850>" + target.getName() + "<#10fc46>. Saldo: <#efa600>" + fmt(bal)));
            }
            case "setcoins" -> {
                eco.setCoins(target.getUniqueId(), target.getName(), amount);
                sender.sendMessage(mm.deserialize("<#10fc46>Coins de <#fcc850>" + target.getName() +
                        " <#10fc46>definido para <#efa600>" + fmt(amount)));
            }
            case "removecoins" -> {
                if (eco.removeCoins(target.getUniqueId(), amount)) {
                    double bal = eco.getCoins(target.getUniqueId());
                    sender.sendMessage(mm.deserialize("<#10fc46>-" + fmt(amount) +
                            " coins de <#fcc850>" + target.getName() + "<#10fc46>. Saldo: <#efa600>" + fmt(bal)));
                } else {
                    sender.sendMessage(mm.deserialize("<#FF0000>Saldo insuficiente!"));
                }
            }
            case "givetokens" -> {
                eco.addTokens(target.getUniqueId(), target.getName(), amount);
                double bal = eco.getTokens(target.getUniqueId());
                sender.sendMessage(mm.deserialize("<#10fc46>+" + fmt(amount) +
                        " tokens para <#fcc850>" + target.getName() + "<#10fc46>. Saldo: <#efa600>" + fmt(bal)));
            }
            case "settokens" -> {
                eco.setTokens(target.getUniqueId(), target.getName(), amount);
                sender.sendMessage(mm.deserialize("<#10fc46>Tokens de <#fcc850>" + target.getName() +
                        " <#10fc46>definido para <#efa600>" + fmt(amount)));
            }
            case "removetokens" -> {
                if (eco.removeTokens(target.getUniqueId(), amount)) {
                    double bal = eco.getTokens(target.getUniqueId());
                    sender.sendMessage(mm.deserialize("<#10fc46>-" + fmt(amount) +
                            " tokens de <#fcc850>" + target.getName() + "<#10fc46>. Saldo: <#efa600>" + fmt(bal)));
                } else {
                    sender.sendMessage(mm.deserialize("<#FF0000>Saldo insuficiente!"));
                }
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private String fmt(double v) { return com.psdk.util.NumberUtil.abbrev(v); }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(mm.deserialize("<#efa600><bold>Economia Admin:"));
        sender.sendMessage(mm.deserialize("  <#fcc850>/eco givecoins <jogador> <qtd>"));
        sender.sendMessage(mm.deserialize("  <#fcc850>/eco setcoins <jogador> <qtd>"));
        sender.sendMessage(mm.deserialize("  <#fcc850>/eco removecoins <jogador> <qtd>"));
        sender.sendMessage(mm.deserialize("  <#fcc850>/eco givetokens <jogador> <qtd>"));
        sender.sendMessage(mm.deserialize("  <#fcc850>/eco settokens <jogador> <qtd>"));
        sender.sendMessage(mm.deserialize("  <#fcc850>/eco removetokens <jogador> <qtd>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("psdk.eco")) return List.of();

        if (args.length == 1) {
            return List.of("givecoins", "setcoins", "removecoins", "givetokens", "settokens", "removetokens").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }
        return List.of();
    }
}