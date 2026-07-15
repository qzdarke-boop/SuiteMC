package com.psdk.lixeiro;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * "Lixeiro": a cada intervalo remove todos os itens dropados no chão (de todos os
 * mundos), avisando antes. O {@code /lixeiro} mostra quanto falta. Mensagens
 * hand-coded direto no código (concatenadas com +).
 */
public class LixeiroManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** De quanto em quanto tempo o lixeiro passa, em segundos (5 * 60 = 5 minutos). */
    private static final int INTERVALO_SEGUNDOS = 5 * 60;
    /** Em quais segundos restantes mandar o aviso no chat. */
    private static final int[] AVISOS_SEGUNDOS = { 60, 30, 10, 5, 3, 2, 1 };

    private final PSDK plugin;
    private int secondsLeft = INTERVALO_SEGUNDOS;

    public LixeiroManager(PSDK plugin) {
        this.plugin = plugin;
    }

    public void start() {
        secondsLeft = INTERVALO_SEGUNDOS;
        new BukkitRunnable() {
            @Override public void run() {
                secondsLeft--;
                if (secondsLeft <= 0) {
                    clearGround();
                } else if (ehSegundoDeAviso(secondsLeft)) {
                    String tempo = formatarTempo(secondsLeft);
                    plugin.getServer().sendMessage(MM.deserialize(""));
                    plugin.getServer().sendMessage(MM.deserialize("<#a4a4a4>O <#fcc850>lixeiro <#a4a4a4>passa em <#FF0000>" + tempo
                            + "<#a4a4a4>! Recolha seus itens do chão."));
                    plugin.getServer().sendMessage(MM.deserialize("<#FF0000>Cuidado! <#a4a4a4>Você pode acabar perdendo seus itens..."));
                    plugin.getServer().sendMessage(MM.deserialize(""));
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // a cada 1s
    }

    /** Remove todos os itens dropados no chão de todos os mundos. */
    public void clearGround() {
        int removidos = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Item item : w.getEntitiesByClass(Item.class)) {
                if (!item.isValid()) continue;
                item.remove();
                removidos++;
            }
        }
        // ── MENSAGEM AO PASSAR (edite o texto à mão) ──
        plugin.getServer().sendMessage(MM.deserialize("<#a4a4a4>O <#fcc850>lixeiro <#a4a4a4>passou e removeu <#fcc850>" + removidos
                + " <#a4a4a4>item(ns) do chão."));
        secondsLeft = INTERVALO_SEGUNDOS;
    }

    /** Segundos até a próxima passada do lixeiro. */
    public int getSecondsLeft() {
        return secondsLeft;
    }

    /** Texto de status exibido pelo /lixeiro (edite o texto à mão). */
    public String statusMessage() {
        return "<#a4a4a4>O <#fcc850>lixeiro <#a4a4a4>passa em <#fcc850>" + formatarTempo(secondsLeft) + "<#a4a4a4>.";
    }

    private static boolean ehSegundoDeAviso(int segundos) {
        for (int s : AVISOS_SEGUNDOS) if (s == segundos) return true;
        return false;
    }

    /** Formata os segundos em "Xm Ys" / "Xm" / "Ys". */
    private static String formatarTempo(int totalSeg) {
        int m = totalSeg / 60, s = totalSeg % 60;
        if (m > 0 && s > 0) return m + "m " + s + "s";
        if (m > 0) return m + "m";
        return s + "s";
    }
}
