package com.psdk.bounty;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code /bounty} abre a GUI de recompensas (maior → menor).
 * {@code /bounty add <jogador> <quantia>} abre a confirmação para colocar recompensa.
 */
public class BountyCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final double MIN_AMOUNT = 1;

    private final PSDK plugin;

    public BountyCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Apenas jogadores podem usar este comando."));
            return true;
        }

        if (args.length == 0) {
            openList(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("add")) {
            handleAdd(player, args);
            return true;
        }

        player.sendMessage(mm.deserialize("<#fcc850>Uso: <#a4a4a4>/bounty <#fcc850>ou <#a4a4a4>/bounty add <jogador> <quantia>"));
        return true;
    }

    private void openList(Player player) {
        BountyGUI gui = new BountyGUI(plugin.getBountyManager().getSortedDescending(), 0);
        player.openInventory(gui.getInventory());
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }

    private void handleAdd(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(mm.deserialize("<#fcc850>Uso: /bounty add <jogador> <quantia>"));
            return;
        }

        String targetName = args[1];

        double amount;
        try {
            amount = Double.parseDouble(args[2].replace(",", "."));
        } catch (NumberFormatException e) {
            player.sendMessage(mm.deserialize("<#FF0000>Quantia inválida!"));
            return;
        }
        if (!Double.isFinite(amount) || amount < MIN_AMOUNT) {
            player.sendMessage(mm.deserialize("<#FF0000>A quantia mínima de recompensa é <#fcc850>"
                    + BountyManager.fmt(MIN_AMOUNT) + "<#FF0000>!"));
            return;
        }

        BountyManager.ResolvedPlayer target = plugin.getBountyManager().resolveByName(targetName);
        if (target == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Jogador não encontrado."));
            return;
        }

        if (target.uuid().equals(player.getUniqueId())) {
            player.sendMessage(mm.deserialize("<#FF0000>É impossível colocar uma recompensa em você mesmo, cê?"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }

        if (!plugin.getEconomyManager().hasCoins(player.getUniqueId(), amount)) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não tem <#fcc850>" + BountyManager.fmt(amount)
                    + " <#FF0000>em coins para essa recompensa!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }

        double current = plugin.getBountyManager().getAmount(target.uuid());
        BountyConfirmGUI gui = new BountyConfirmGUI(target.uuid(), target.name(), amount, current);
        player.openInventory(gui.getInventory());
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("add").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            String pref = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(pref)).collect(Collectors.toList());
        }
        return List.of();
    }
}
