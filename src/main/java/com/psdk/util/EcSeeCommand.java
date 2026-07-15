package com.psdk.util;

import com.psdk.PSDK;
import com.psdk.ec.EnderChestGUI;
import com.psdk.ec.EnderChestManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code /ecsee <jogador>} — abre a ender chest de outro jogador (staff), EDITÁVEL.
 *
 * <p>A ender chest vive 100% no BANCO (a vanilla fica vazia, anti-dupe). Por isso lemos
 * e gravamos no banco pelo UUID — funciona igual com o alvo online ou offline, e o staff
 * pode pegar/colocar itens. As alterações são salvas ao fechar o inventário.
 *
 * <p>Anti-dupe: ao abrir num alvo ONLINE, a EC que ele tiver aberta é fechada antes
 * (forçando o save dela), para não existir uma cópia em memória que sobrescreveria as
 * mudanças do staff ao ser fechada depois.
 */
public class EcSeeCommand implements CommandExecutor, TabCompleter, Listener {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public EcSeeCommand(PSDK plugin) { this.plugin = plugin; }

    /** Holder da EC de staff (editável). Guarda quem é o alvo para salvar no banco ao fechar. */
    private final class StaffEcHolder implements InventoryHolder {
        final UUID targetId;
        final String targetName;
        Inventory inv;
        StaffEcHolder(UUID targetId, String targetName) { this.targetId = targetId; this.targetName = targetName; }
        @Override public Inventory getInventory() { return inv; }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Apenas jogadores podem usar este comando."));
            return true;
        }
        if (!player.hasPermission("psdk.ecsee")) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não tem permissão para isso."));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(mm.deserialize("<#fcc850>Uso: /ecsee <jogador>"));
            return true;
        }

        Player online = Bukkit.getPlayerExact(args[0]);
        UUID targetId;
        String targetName;
        if (online != null) {
            // Fecha a EC que o alvo tiver aberta (flush + libera a trava) para não haver
            // cópia em memória sobrescrevendo as mudanças do staff depois.
            if (online.getOpenInventory().getTopInventory().getHolder() instanceof EnderChestGUI) {
                online.closeInventory();
            }
            targetId = online.getUniqueId();
            targetName = online.getName();
        } else {
            OfflinePlayer off = resolveOffline(args[0]);
            if (off == null || off.getName() == null || !off.hasPlayedBefore()) {
                player.sendMessage(mm.deserialize("<#FF0000>Esse jogador nunca entrou no servidor."));
                return true;
            }
            targetId = off.getUniqueId();
            targetName = off.getName();
        }

        // Anti-concorrência: trava a EC do alvo para este staff. Impede que o dono
        // (ou outro staff) abra uma 2ª cópia e sobrescreva/duplique itens.
        if (!plugin.getEnderChestManager().acquireLock(targetId, player.getUniqueId())) {
            player.sendMessage(mm.deserialize("<#FF0000>A ender chest de <#fcc850>" + targetName
                    + " <#FF0000>já está sendo acessada por outra pessoa. Aguarde."));
            return true;
        }

        ItemStack[] contents = plugin.getEnderChestManager().load(targetId);
        StaffEcHolder holder = new StaffEcHolder(targetId, targetName);
        Inventory inv = Bukkit.createInventory(holder, EnderChestManager.SLOTS,
                mm.deserialize("<dark_gray>EC de " + targetName
                        + (online != null ? "" : " <gray>(offline)")));
        holder.inv = inv;
        for (int i = 0; i < contents.length && i < EnderChestManager.SLOTS; i++) {
            if (contents[i] != null) inv.setItem(i, contents[i]);
        }
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1f, online != null ? 1f : 0.85f);
        player.sendMessage(mm.deserialize("<#10fc46>Editando a ender chest de <#fcc850>" + targetName
                + (online != null ? "" : " <gray>(offline)") + "<#10fc46>."));
        return true;
    }

    /** Salva no banco ao fechar (e também se o staff sair com ela aberta). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof StaffEcHolder holder)) return;
        persist(holder, e.getInventory());
        plugin.getEnderChestManager().releaseLock(holder.targetId, e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Inventory top = e.getPlayer().getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof StaffEcHolder holder) {
            persist(holder, top);
            plugin.getEnderChestManager().releaseLock(holder.targetId, e.getPlayer().getUniqueId());
        }
    }

    private void persist(StaffEcHolder holder, Inventory inv) {
        ItemStack[] contents = new ItemStack[EnderChestManager.SLOTS];
        for (int i = 0; i < EnderChestManager.SLOTS; i++) contents[i] = inv.getItem(i);
        plugin.getEnderChestManager().save(holder.targetId, contents);

        // Se o alvo está online e (re)abre a EC, ela carrega do banco já atualizado.
        // Se ele estiver com a nossa cópia? Não há — fechamos a dele ao abrir. Mas por
        // segurança, esvazia a EC vanilla dele (nunca pode ser 2ª cópia).
        Player target = Bukkit.getPlayer(holder.targetId);
        if (target != null && target.isOnline()) {
            target.getEnderChest().clear();
        }
    }

    /** Acha o OfflinePlayer pelo nome (entre os que já entraram), sem lookup bloqueante na web. */
    private OfflinePlayer resolveOffline(String name) {
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(name)) return op;
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("psdk.ecsee")) return List.of();
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
