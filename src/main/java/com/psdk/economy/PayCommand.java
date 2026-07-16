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

/**
 * {@code /pay <coins|tokens> <jogador> <quantia>} — transfere moeda entre jogadores.
 *  • coins  → mínimo de 1 por envio.
 *  • tokens → mínimo de 1000 por envio.
 * A quantia é sempre em unidades inteiras (frações são descartadas), então o que é
 * exibido é exatamente o que é enviado (corrige o bug do "enviou 0 coins").
 * O alvo precisa estar online. Não dá pra pagar a si mesmo nem valores inválidos.
 */
public class PayCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final double MIN_COINS = 100;
    private static final double MIN_TOKENS = 1000;

    private final PSDK plugin;

    public PayCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player payer)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Apenas jogadores podem usar este comando."));
            return true;
        }
        if (args.length < 3) {
            payer.sendMessage(mm.deserialize("<#fcc850>Uso: /pay <coins|tokens> <jogador> <quantia>"));
            return true;
        }

        String moeda = args[0].toLowerCase();
        boolean coins;
        if (moeda.equals("coins"))      coins = true;
        else if (moeda.equals("tokens")) coins = false;
        else {
            payer.sendMessage(mm.deserialize("<#FF0000>Moeda inválida! Use <#fcc850>coins <#FF0000>ou <#fcc850>tokens<#FF0000>."));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            payer.sendMessage(mm.deserialize("<#FF0000>Jogador não encontrado ou offline."));
            return true;
        }
        if (target.getUniqueId().equals(payer.getUniqueId())) {
            payer.sendMessage(mm.deserialize("<#FF0000>Você não pode pagar a si mesmo!"));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2].replace(",", "."));
        } catch (NumberFormatException e) {
            payer.sendMessage(mm.deserialize("<#FF0000>Quantia inválida!"));
            return true;
        }
        // Trabalha sempre com unidades inteiras: descarta frações para que o valor
        // exibido seja exatamente o transferido (evita o "enviou 0 coins").
        amount = Math.floor(amount);
        if (amount <= 0) {
            payer.sendMessage(mm.deserialize("<#FF0000>A quantia precisa ser maior que zero!"));
            return true;
        }
        if (coins && amount < MIN_COINS) {
            payer.sendMessage(mm.deserialize("<#FF0000>O mínimo para enviar coins é <#fcc850>"
                    + String.format("%.0f", MIN_COINS) + " coins<#FF0000>!"));
            return true;
        }
        if (!coins && amount < MIN_TOKENS) {
            payer.sendMessage(mm.deserialize("<#FF0000>O mínimo para enviar tokens é <#fcc850>"
                    + String.format("%.0f", MIN_TOKENS) + " tokens<#FF0000>!"));
            return true;
        }

        EconomyManager eco = plugin.getEconomyManager();
        String moedaNome = coins ? "coins" : "tokens";
        String valor = String.format("%.0f", amount);

        // removeXxx já verifica saldo e debita de forma atômica.
        boolean ok = coins ? eco.removeCoins(payer.getUniqueId(), amount)
                           : eco.removeTokens(payer.getUniqueId(), amount);
        if (!ok) {
            payer.sendMessage(mm.deserialize("<#FF0000>Você não tem <#fcc850>" + valor + " " + moedaNome + " <#FF0000>para enviar!"));
            return true;
        }

        // Transferência entre jogadores: só move o SALDO. NÃO conta para o Top Coins
        // (addCoinsNoStat), impedindo inflar o ranking com /pay de ida e volta.
        if (coins) eco.addCoinsNoStat(target.getUniqueId(), target.getName(), amount);
        else       eco.addTokens(target.getUniqueId(), target.getName(), amount);

        payer.sendMessage(mm.deserialize("<#10fc46>Você enviou <#fcc850>" + valor + " " + moedaNome
                + " <#10fc46>para <#fcc850>" + target.getName() + "<#10fc46>!"));
        target.sendMessage(mm.deserialize("<#10fc46>Você recebeu <#fcc850>" + valor + " " + moedaNome
                + " <#10fc46>de <#fcc850>" + payer.getName() + "<#10fc46>!"));
        target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("coins", "tokens").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2) {
            String pref = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(pref)).collect(Collectors.toList());
        }
        return List.of();
    }
}
