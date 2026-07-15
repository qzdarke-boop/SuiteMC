package com.psdk.chat;

import com.psdk.PSDK;
import com.psdk.util.TextUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.UUID;

public class TellCommand implements CommandExecutor {

    private final MiniMessage mm = MiniMessage.miniMessage();
    // Estático: /tell e /reply são instâncias separadas e precisam compartilhar a última conversa.
    private static final HashMap<UUID, UUID> lastMessenger = new HashMap<>();
    private final SpyCommand spyCommand;

    public TellCommand(SpyCommand spyCommand) {
        this.spyCommand = spyCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        Player target;
        String message;

        if (label.equalsIgnoreCase("r") || label.equalsIgnoreCase("reply")) {
            if (args.length < 1) {
                player.sendMessage(mm.deserialize("<#FF0000>Uso correto: /r <mensagem>"));
                return true;
            }
            UUID last = lastMessenger.get(player.getUniqueId());
            target = (last != null) ? Bukkit.getPlayer(last) : null;

            if (target == null || !target.isOnline()) {
                player.sendMessage(mm.deserialize("<#FF0000>Não há ninguém para responder ou o jogador deslogou."));
                return true;
            }
            message = String.join(" ", args);
        } else {
            if (args.length < 2) {
                player.sendMessage(mm.deserialize("<#FF0000>Uso correto: /tell <jogador> <mensagem>"));
                return true;
            }
            target = Bukkit.getPlayer(args[0]);

            if (target == null || !target.isOnline()) {
                player.sendMessage(mm.deserialize("<#FF0000>Esse jogador não foi encontrado ou está offline."));
                return true;
            }

            if (target.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage(mm.deserialize("<#FF0000>Você não pode enviar mensagens para si mesmo!"));
                return true;
            }

            message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        }

        // Alvo desativou PMs -> bloqueia, EXCETO se o remetente for OP ou tiver bypass (staff alcança qualquer um).
        if (!player.isOp() && !player.hasPermission("psdk.tell.bypass")
                && !PSDK.getInstance().getSettingsManager().getSetting(target.getUniqueId(), "tell")) {
            player.sendMessage(mm.deserialize("<#FF0000>Este jogador desativou mensagens privadas."));
            return true;
        }

        // Sem permissão de cores, escapa tags MiniMessage; hex sempre bloqueado sem bypass.
        if (!player.hasPermission("psdk.chat.hexbypass")) {
            message = TextUtil.stripAdvancedColors(message);
        }
        if (!player.hasPermission("psdk.colors")) {
            message = message.replace("\\", "\\\\").replace("<", "\\<");
        }

        player.sendMessage(mm.deserialize("<#F0C039>Mensagem para <white>" + target.getName() + "<#F0C039>: <white>" + message));
        target.sendMessage(mm.deserialize("<#F0C039>Mensagem de <white>" + player.getName() + "<#F0C039>: <white>" + message));
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.5f);

        for (UUID spyId : spyCommand.getSpies()) {
            Player spy = Bukkit.getPlayer(spyId);
            if (spy != null && !spyId.equals(player.getUniqueId()) && !spyId.equals(target.getUniqueId())) {
                spy.sendMessage(mm.deserialize("<dark_gray>[SPY] <gray>" + player.getName() + " → " + target.getName() + ": <white>" + message));
            }
        }

        lastMessenger.put(target.getUniqueId(), player.getUniqueId());
        lastMessenger.put(player.getUniqueId(), target.getUniqueId());
        return true;
    }
}
