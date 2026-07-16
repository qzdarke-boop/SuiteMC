package com.psdk.boss;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code /pboss} — comandos de admin do boss: spawn, kill, testsound.
 * O spawn usa o local de {@code /setbossspawn} (ou a sua posição se não houver).
 */
public class PBossCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public PBossCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("psdk.boss.admin")) {
            sender.sendMessage(mm.deserialize("<#FF0000>Você não tem permissão para isso."));
            return true;
        }
        if (args.length == 0) {
            int min = plugin.getBossManager().getAutoSpawnMinutes();
            int life = plugin.getBossManager().getLifetimeMinutes();
            boolean alive = plugin.getBossManager().isActive();
            long now = System.currentTimeMillis();
            sender.sendMessage(mm.deserialize("<#efa600><bold>Boss (admin):"));
            if (alive) {
                long exp = plugin.getBossManager().getBossExpireMillis() - now;
                sender.sendMessage(mm.deserialize("  <#10fc46>Boss VIVO agora<#a4a4a4>"
                        + (exp > 0 ? " — vai embora em <#6817ff>" + formatDuration(exp) : "")));
            } else {
                long next = plugin.getBossManager().getNextAutoSpawnMillis() - now;
                sender.sendMessage(mm.deserialize("  <#a4a4a4>Sem boss vivo — próximo automático em <#6817ff>"
                        + (next > 0 ? formatDuration(next) : "instantes")));
            }
            sender.sendMessage(mm.deserialize("  <#6817ff>/pboss spawn <cavaleiro|spooky|tower> <#a4a4a4>- Invoca o boss escolhido"));
            sender.sendMessage(mm.deserialize("  <#6817ff>/pboss kill <#a4a4a4>- Remove o boss ativo"));
            sender.sendMessage(mm.deserialize("  <#6817ff>/pboss interval <minutos> <#a4a4a4>- Intervalo do auto-spawn (atual: <#6817ff>" + min + " min<#a4a4a4>)"));
            sender.sendMessage(mm.deserialize("  <#6817ff>/pboss lifetime <minutos> <#a4a4a4>- Tempo de vida do boss (atual: <#6817ff>" + life + " min<#a4a4a4>)"));
            sender.sendMessage(mm.deserialize("  <#6817ff>/pboss testsound <#a4a4a4>- Testa os sons custom"));
            sender.sendMessage(mm.deserialize("  <#a4a4a4>Defina os locais com <#6817ff>/setbossarena <#a4a4a4>e <#6817ff>/setbossspawn"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "interval", "intervalo" -> {
                if (args.length < 2) {
                    sender.sendMessage(mm.deserialize("<#FF0000>Uso: /pboss interval <minutos> <#a4a4a4>(atual: "
                            + plugin.getBossManager().getAutoSpawnMinutes() + " min)"));
                    return true;
                }
                try {
                    int min = Integer.parseInt(args[1]);
                    if (min < 1) { sender.sendMessage(mm.deserialize("<#FF0000>O intervalo deve ser de pelo menos 1 minuto.")); return true; }
                    plugin.getBossManager().setAutoSpawnMinutes(min);
                    sender.sendMessage(mm.deserialize("<#10fc46>Boss agora nasce sozinho a cada <#6817ff>" + min + " minutos<#10fc46>."));
                } catch (NumberFormatException e) {
                    sender.sendMessage(mm.deserialize("<#FF0000>Minutos inválido: " + args[1]));
                }
            }
            case "lifetime", "vida", "tempo" -> {
                if (args.length < 2) {
                    sender.sendMessage(mm.deserialize("<#FF0000>Uso: /pboss lifetime <minutos> <#a4a4a4>(atual: "
                            + plugin.getBossManager().getLifetimeMinutes() + " min)"));
                    return true;
                }
                try {
                    int min = Integer.parseInt(args[1]);
                    if (min < 1) { sender.sendMessage(mm.deserialize("<#FF0000>O tempo de vida deve ser de pelo menos 1 minuto.")); return true; }
                    plugin.getBossManager().setLifetimeMinutes(min);
                    sender.sendMessage(mm.deserialize("<#10fc46>O boss agora vive por <#6817ff>" + min
                            + " minutos<#10fc46> antes de ir embora. <#a4a4a4>(vale a partir do próximo spawn)"));
                } catch (NumberFormatException e) {
                    sender.sendMessage(mm.deserialize("<#FF0000>Minutos inválido: " + args[1]));
                }
            }
            case "spawn" -> {
                Location at = plugin.getBossManager().getBossSpawn();
                if (at == null) {
                    if (sender instanceof Player p) at = p.getLocation();
                    else {
                        sender.sendMessage(mm.deserialize("<#FF0000>Defina o local com /setbossspawn (ou use no jogo)."));
                        return true;
                    }
                }
                String type = args.length >= 2 ? args[1].toLowerCase() : "cavaleiro";
                if (!type.equals("cavaleiro") && !type.equals("spooky") && !type.equals("tower")) {
                    sender.sendMessage(mm.deserialize("<#FF0000>Tipo inválido. Use: <#6817ff>/pboss spawn <cavaleiro|spooky|tower>"));
                    return true;
                }
                plugin.getBossManager().spawn(at, type);
                String nice = type.equals("spooky") ? "Spooky"
                        : type.equals("tower") ? "Tower Skeleton" : "Cavaleiro das Sombras";
                sender.sendMessage(mm.deserialize("<#10fc46>Invocando boss: <#6817ff>" + nice + "<#10fc46>!"));
            }
            case "kill" -> {
                if (!plugin.getBossManager().isActive()) {
                    sender.sendMessage(mm.deserialize("<#FF0000>Não há nenhum boss ativo."));
                    return true;
                }
                plugin.getBossManager().despawn();
                sender.sendMessage(mm.deserialize("<#10fc46>Boss removido."));
            }
            case "testsound" -> testSounds(sender);
            default -> sender.sendMessage(mm.deserialize("<#FF0000>Use /pboss spawn, kill, interval, lifetime ou testsound."));
        }
        return true;
    }

    private String formatDuration(long ms) {
        long totalSec = ms / 1000L;
        long h = totalSec / 3600L;
        long m = (totalSec % 3600L) / 60L;
        long s = totalSec % 60L;
        if (h > 0) return h + "h " + m + "min";
        if (m > 0) return m + "min " + s + "s";
        return s + "s";
    }

    /** Toca alguns sons custom do pack pro jogador testar se o resourcepack os tem. */
    private void testSounds(CommandSender sender) {
        if (!(sender instanceof Player pl)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Use no jogo."));
            return;
        }
        String[] keys = {
                "awakened_warrior_sounds:samus.awakened_warrior.warrior_slash",
                "awakened_warrior_sounds:samus.awakened_warrior.warrior_pierce",
                "awakened_warrior_sounds:samus.awakened_warrior.ult_stomp",
                "universal_sounds:samus.universal.rupture_big"
        };
        for (int i = 0; i < keys.length; i++) {
            final String k = keys[i];
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> pl.playSound(net.kyori.adventure.sound.Sound.sound(
                            net.kyori.adventure.key.Key.key(k),
                            net.kyori.adventure.sound.Sound.Source.MASTER, 1f, 1f)), i * 12L);
        }
        pl.sendMessage(mm.deserialize("<#6817ff>Tocando 4 sons custom do pack..."));
        pl.sendMessage(mm.deserialize("<#a4a4a4>Se ficou <#FF0000>MUDO<#a4a4a4>, o resourcepack aplicado NÃO tem esses sons (é o pack, não o código)."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("psdk.boss.admin")) return List.of();
        if (args.length == 1) {
            return List.of("spawn", "kill", "interval", "lifetime", "testsound").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            return List.of("cavaleiro", "spooky", "tower").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }
        return List.of();
    }
}
