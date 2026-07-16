package com.psdk.chat;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Mensagens automáticas do Skill Pit. Alterna entre os anúncios cadastrados a cada
 * {@link #INTERVAL_TICKS}, respeitando a preferência por jogador ({@code announcements}).
 *
 * <p>Só envia aos jogadores online do Skill Pit, no padrão de cores/formatação do
 * projeto. Como é uma {@link BukkitRunnable} agendada no onEnable e o Bukkit cancela
 * as tasks do plugin no onDisable, não há duplicação após reload.
 */
public class AnnouncementManager extends BukkitRunnable {

    // ============= AJUSTES DAS MENSAGENS AUTOMÁTICAS (EDITE AQUI) =============
    /** Liga/desliga TODAS as mensagens automáticas. */
    public static final boolean ENABLED = true;
    /** Intervalo entre cada mensagem (ticks). 7200 = 6 min. */
    public static final long INTERVAL_TICKS = 6 * 60 * 20L;
    /** Liga/desliga só o lembrete de /report. */
    private static final boolean REPORT_ENABLED = true;
    // =========================================================================

    private final PSDK plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    public AnnouncementManager(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!ENABLED) return;
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        sendReportReminder();
    }

    private void sendReportReminder() {
        if (!REPORT_ENABLED) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.getSettingsManager().getSetting(player.getUniqueId(), "announcements")) continue;
            player.sendMessage(mm.deserialize(""));
            player.sendMessage(mm.deserialize("   <#e22c27><bold>Regras do Skill Pit!"));
            player.sendMessage(mm.deserialize("<#cbd1d7>Use <#6817ff>/report <#cbd1d7>para denunciar quem estiver infringindo as regras."));
            player.sendMessage(mm.deserialize(""));
        }
    }
}
