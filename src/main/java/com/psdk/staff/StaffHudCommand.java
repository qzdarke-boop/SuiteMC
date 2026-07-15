package com.psdk.staff;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StaffHudCommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final NetworkTrafficMonitor netMonitor;
    private final com.sun.management.OperatingSystemMXBean osBean =
            (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    private final Set<UUID> ativos = ConcurrentHashMap.newKeySet();

    public StaffHudCommand(JavaPlugin plugin, NetworkTrafficMonitor netMonitor) {
        this.plugin     = plugin;
        this.netMonitor = netMonitor;
        iniciarTask();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MM.deserialize("<#FF0000>Apenas jogadores podem usar este comando."));
            return true;
        }

        if (!player.hasPermission("psdk.staff.hud")) {
            player.sendMessage(MM.deserialize("<#FF0000>Sem permissão!"));
            return true;
        }

        if (ativos.remove(player.getUniqueId())) {
            player.sendActionBar(Component.empty());
            player.sendMessage(MM.deserialize("<#848c94>HUD de staff <#FF0000>desativada<#848c94>."));
        } else {
            ativos.add(player.getUniqueId());
            player.sendMessage(MM.deserialize("<#848c94>HUD de staff <#10fc46>ativada<#848c94>."));
        }
        return true;
    }

    private void iniciarTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (ativos.isEmpty()) return;

            // ── Valores globais — calculados uma vez por ciclo ────────────────
            double tps    = Math.min(20.0, Bukkit.getTPS()[0]);
            double mspt   = Bukkit.getServer().getAverageTickTime();
            int    online = Bukkit.getOnlinePlayers().size();

            double cpuLoad = osBean.getProcessCpuLoad();   // 0..1, ou <0 se indisponível
            int    cpuPct  = (int) Math.round((cpuLoad < 0 ? 0 : cpuLoad) * 100);

            Runtime rt    = Runtime.getRuntime();
            long usedHeap = rt.totalMemory() - rt.freeMemory();
            long maxHeap  = rt.maxMemory();
            int  heapPct  = maxHeap <= 0 ? 0 : (int) Math.round((double) usedHeap / maxHeap * 100);

            long[] net   = netMonitor.snapshotRates();     // {rx, tx} em bytes/s
            String rxStr = formatRate(net[0]);
            String txStr = formatRate(net[1]);

            String tpsStr  = String.format(Locale.US, "%.1f", tps);
            String msptStr = String.format(Locale.US, "%.1f", mspt);

            String linha = "<#848c94>TPS: <#b1fcb6>" + tpsStr
                    + " <#848c94>MSPT: <#71b0ec>" + msptStr
                    + " <#848c94>CPU: " + corPercent(cpuPct) + cpuPct + "%"
                    + " <#848c94>Heap: " + corPercent(heapPct) + heapPct + "%"
                    + " <#848c94>Net: <#71b03c>⬇" + rxStr + " <#10fc46>⬆" + txStr
                    + " <#848c94>Online: <#cbd1d7>" + online
                    + " <#848c94>Ping: ";

            // Ping é por jogador.
            ativos.removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
            for (UUID uuid : ativos) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) continue;
                player.sendActionBar(MM.deserialize(linha + "<#b1fcb6>" + player.getPing() + "ms"));
            }
        }, 0L, 20L);
    }

    /** Cor por faixa de porcentagem. */
    private String corPercent(int pct) {
        if (pct <= 25) return "<#cbd1d7>";   // 0-25%
        if (pct <= 50) return "<#10fc46>";   // 26-50%
        if (pct <= 75) return "<#ffd250>";   // 51-75%
        return "<#e22c27>";                  // 76-100%
    }

    /** Formata bytes/s em B/s, KB/s ou MB/s. */
    private String formatRate(long bytesPerSec) {
        if (bytesPerSec < 1024) return bytesPerSec + "B/s";
        double kb = bytesPerSec / 1024.0;
        if (kb < 1024) return Math.round(kb) + "KB/s";
        return String.format(Locale.US, "%.1fMB/s", kb / 1024.0);
    }
}
