package com.psdk.adminabuse;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /adminabuse} — abre o painel de controle.
 * {@code /adminabuse stop} — encerra tudo (atalho, funciona do console).
 * {@code /adminabuse auto ...} — controla o EVENTO RELÂMPAGO automático.
 */
public class AdminAbuseCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public AdminAbuseCommand(PSDK plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("psdk.adminabuse")) {
            sender.sendMessage(mm.deserialize("<#FF0000>Você não tem permissão para isso."));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("stop")) {   // atalho de emergência (funciona do console)
            plugin.getAdminAbuseManager().stop();
            sender.sendMessage(mm.deserialize("<#10fc46>Tudo encerrado."));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("auto")) {
            return handleAuto(sender, args);
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Use no jogo (ou /adminabuse stop)."));
            return true;
        }
        player.openInventory(new AdminAbuseGUI(plugin.getAdminAbuseManager()).getInventory());
        return true;
    }

    private boolean handleAuto(CommandSender sender, String[] args) {
        AdminAbuseScheduler sched = plugin.getAdminAbuseScheduler();
        if (args.length == 1) { sendAutoStatus(sender, sched); return true; }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "on" -> {
                sched.setEnabled(true);
                sender.sendMessage(mm.deserialize("<#10fc46>Evento Relâmpago automático <#fcc850>LIGADO<#10fc46>."));
            }
            case "off" -> {
                sched.setEnabled(false);
                sender.sendMessage(mm.deserialize("<#fcc850>Evento Relâmpago automático <#FF0000>DESLIGADO<#fcc850>."));
            }
            case "now" -> {
                boolean ok = plugin.getAdminAbuseManager().startAutoEvent(sched.getDurationMinutes());
                sender.sendMessage(ok
                        ? mm.deserialize("<#10fc46>Evento Relâmpago disparado agora!")
                        : mm.deserialize("<#FF0000>Já tem um Evento Relâmpago em andamento."));
            }
            case "stop" -> {
                plugin.getAdminAbuseManager().stopAutoEvent();
                sender.sendMessage(mm.deserialize("<#fcc850>Evento Relâmpago encerrado."));
            }
            case "duration" -> {
                if (args.length < 3) { sender.sendMessage(mm.deserialize("<#FF0000>Uso: /adminabuse auto duration <minutos>")); return true; }
                try {
                    int min = Integer.parseInt(args[2]);
                    if (min < 1) { sender.sendMessage(mm.deserialize("<#FF0000>A duração deve ser de pelo menos 1 minuto.")); return true; }
                    sched.setDuration(min);
                    sender.sendMessage(mm.deserialize("<#10fc46>Duração definida para <#fcc850>" + min + " minutos<#10fc46>."));
                } catch (NumberFormatException e) {
                    sender.sendMessage(mm.deserialize("<#FF0000>Minutos inválido: " + args[2]));
                }
            }
            case "addtime" -> {
                if (args.length < 3) { sender.sendMessage(mm.deserialize("<#FF0000>Uso: /adminabuse auto addtime HH:mm")); return true; }
                LocalTime t = parse(args[2]);
                if (t == null) { sender.sendMessage(mm.deserialize("<#FF0000>Horário inválido (use HH:mm, ex.: 18:30).")); return true; }
                if (sched.addTime(t.getHour(), t.getMinute()))
                    sender.sendMessage(mm.deserialize("<#10fc46>Horário <#fcc850>" + fmt(t) + " <#10fc46>adicionado."));
                else
                    sender.sendMessage(mm.deserialize("<#FF0000>Esse horário já está na lista."));
            }
            case "removetime", "remtime", "deltime" -> {
                if (args.length < 3) { sender.sendMessage(mm.deserialize("<#FF0000>Uso: /adminabuse auto removetime HH:mm")); return true; }
                LocalTime t = parse(args[2]);
                if (t == null) { sender.sendMessage(mm.deserialize("<#FF0000>Horário inválido (use HH:mm).")); return true; }
                if (sched.removeTime(t.getHour(), t.getMinute()))
                    sender.sendMessage(mm.deserialize("<#10fc46>Horário <#fcc850>" + fmt(t) + " <#10fc46>removido."));
                else
                    sender.sendMessage(mm.deserialize("<#FF0000>Esse horário não estava na lista."));
            }
            default -> sendAutoStatus(sender, sched);
        }
        return true;
    }

    private void sendAutoStatus(CommandSender sender, AdminAbuseScheduler sched) {
        StringBuilder horarios = new StringBuilder();
        List<LocalTime> times = sched.getTimes();
        if (times.isEmpty()) horarios.append("<#a4a4a4>(nenhum)");
        else for (int i = 0; i < times.size(); i++) {
            if (i > 0) horarios.append("<#a4a4a4>, ");
            horarios.append("<#fcc850>").append(fmt(times.get(i)));
        }
        LocalTime next = sched.nextTime();

        sender.sendMessage(mm.deserialize("<#fcc850><bold>⚡ Evento Relâmpago — Automático"));
        sender.sendMessage(mm.deserialize("<white>Estado: " + (sched.isEnabled()
                ? "<#10fc46>LIGADO" : "<#FF0000>DESLIGADO")));
        sender.sendMessage(mm.deserialize("<white>Duração: <#fcc850>" + sched.getDurationMinutes() + " min"));
        sender.sendMessage(mm.deserialize("<white>Horários: " + horarios));
        if (next != null && sched.isEnabled())
            sender.sendMessage(mm.deserialize("<white>Próximo: <#fcc850>" + fmt(next)));
        if (plugin.getAdminAbuseManager().isAutoEventActive())
            sender.sendMessage(mm.deserialize("<#10fc46>Há um evento ACONTECENDO agora."));
        sender.sendMessage(mm.deserialize("<#a4a4a4>Use: on | off | now | stop | duration <min> | addtime HH:mm | removetime HH:mm"));
    }

    private LocalTime parse(String s) {
        try {
            String[] hm = s.split(":");
            if (hm.length != 2) return null;
            int h = Integer.parseInt(hm[0]);
            int m = Integer.parseInt(hm[1]);
            if (h < 0 || h > 23 || m < 0 || m > 59) return null;
            return LocalTime.of(h, m);
        } catch (Exception e) {
            return null;
        }
    }

    private String fmt(LocalTime t) {
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String s : List.of("stop", "auto")) if (s.startsWith(args[0].toLowerCase())) out.add(s);
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("auto")) {
            List<String> out = new ArrayList<>();
            for (String s : List.of("on", "off", "now", "stop", "duration", "addtime", "removetime"))
                if (s.startsWith(args[1].toLowerCase())) out.add(s);
            return out;
        }
        return List.of();
    }
}
