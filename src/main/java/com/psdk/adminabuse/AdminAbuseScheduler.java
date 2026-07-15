package com.psdk.adminabuse;

import com.psdk.PSDK;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Agendador do EVENTO RELÂMPAGO (Admin Abuse automático).
 *
 * Dispara {@link AdminAbuseManager#startAutoEvent(int)} em horários fixos do dia
 * (hora do servidor). Toda a configuração (ligado/desligado, horários, duração) é
 * persistida na tabela {@code settings} (key-value), então sobrevive a restart.
 *
 * Checagem a cada 20s, com dedupe por "dia HH:mm" para não disparar duas vezes no
 * mesmo minuto.
 */
public class AdminAbuseScheduler {

    private static final String KEY_ENABLED  = "aa_auto_enabled";
    private static final String KEY_TIMES    = "aa_auto_times";
    private static final String KEY_DURATION = "aa_auto_duration";

    private static final List<LocalTime> DEFAULT_TIMES = List.of(
            LocalTime.of(12, 0), LocalTime.of(18, 0), LocalTime.of(21, 0));
    private static final int DEFAULT_DURATION = 15;

    private final PSDK plugin;
    private boolean enabled;
    private int durationMinutes;
    private final List<LocalTime> times = new ArrayList<>();
    /** Marca "yyyy-MM-dd HH:mm" do último disparo, pra não repetir no mesmo minuto. */
    private String lastFiredKey = "";

    public AdminAbuseScheduler(PSDK plugin) {
        this.plugin = plugin;
        load();
        startChecker();
    }

    // ════════════════════════════ checker ════════════════════════════
    private void startChecker() {
        new BukkitRunnable() {
            @Override public void run() { tick(); }
        }.runTaskTimer(plugin, 400L, 400L); // a cada 20s
    }

    private void tick() {
        if (!enabled || times.isEmpty()) return;
        LocalTime now = LocalTime.now();
        String minuteKey = LocalDate.now() + " " + String.format("%02d:%02d", now.getHour(), now.getMinute());
        if (minuteKey.equals(lastFiredKey)) return;

        for (LocalTime t : times) {
            if (t.getHour() == now.getHour() && t.getMinute() == now.getMinute()) {
                lastFiredKey = minuteKey;
                // Sem ninguém online não faz sentido (chuva de baús/coins pro nada): pula.
                if (plugin.getServer().getOnlinePlayers().isEmpty()) return;
                if (!plugin.getAdminAbuseManager().isAutoEventActive()) {
                    plugin.getAdminAbuseManager().startAutoEvent(durationMinutes);
                }
                return;
            }
        }
    }

    // ════════════════════════════ API (usada pelo comando) ════════════════════════════
    public boolean isEnabled() { return enabled; }
    public int getDurationMinutes() { return durationMinutes; }
    public List<LocalTime> getTimes() { return new ArrayList<>(times); }

    public void setEnabled(boolean value) {
        this.enabled = value;
        save();
    }

    public void setDuration(int minutes) {
        this.durationMinutes = Math.max(1, minutes);
        save();
    }

    public boolean addTime(int hour, int minute) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return false;
        LocalTime t = LocalTime.of(hour, minute);
        if (times.contains(t)) return false;
        times.add(t);
        times.sort(Comparator.naturalOrder());
        save();
        return true;
    }

    public boolean removeTime(int hour, int minute) {
        boolean removed = times.remove(LocalTime.of(hour, minute));
        if (removed) save();
        return removed;
    }

    /** Próximo horário agendado a partir de agora (ou o primeiro do dia seguinte). */
    public LocalTime nextTime() {
        if (times.isEmpty()) return null;
        LocalTime now = LocalTime.now();
        for (LocalTime t : times) {
            if (t.isAfter(now)) return t;
        }
        return times.get(0); // amanhã
    }

    // ════════════════════════════ persistência (settings) ════════════════════════════
    private void load() {
        String en = getSetting(KEY_ENABLED);
        String tm = getSetting(KEY_TIMES);
        String dr = getSetting(KEY_DURATION);

        // Defaults na primeira execução (grava pra ficar visível/editável).
        if (en == null && tm == null && dr == null) {
            enabled = true;
            durationMinutes = DEFAULT_DURATION;
            times.addAll(DEFAULT_TIMES);
            save();
            return;
        }

        enabled = "true".equalsIgnoreCase(en);
        durationMinutes = DEFAULT_DURATION;
        if (dr != null) {
            try { durationMinutes = Math.max(1, Integer.parseInt(dr.trim())); } catch (NumberFormatException ignored) {}
        }
        times.clear();
        if (tm != null && !tm.isBlank()) {
            for (String part : tm.split(",")) {
                LocalTime t = parseTime(part.trim());
                if (t != null && !times.contains(t)) times.add(t);
            }
            times.sort(Comparator.naturalOrder());
        }
    }

    private void save() {
        setSetting(KEY_ENABLED, String.valueOf(enabled));
        setSetting(KEY_DURATION, String.valueOf(durationMinutes));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times.size(); i++) {
            LocalTime t = times.get(i);
            if (i > 0) sb.append(',');
            sb.append(String.format("%02d:%02d", t.getHour(), t.getMinute()));
        }
        setSetting(KEY_TIMES, sb.toString());
    }

    private LocalTime parseTime(String s) {
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

    private String getSetting(String key) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection()
                .prepareStatement("SELECT value FROM settings WHERE key = ?")) {
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("value");
        } catch (Exception e) {
            plugin.getLogger().warning("[AdminAbuse] erro ao ler '" + key + "': " + e.getMessage());
        }
        return null;
    }

    private void setSetting(String key, String value) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection()
                .prepareStatement("INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("[AdminAbuse] erro ao salvar '" + key + "': " + e.getMessage());
        }
    }
}
