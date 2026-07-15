package com.psdk.settings;

import com.psdk.PSDK;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class SettingsManager {

    private final PSDK plugin;
    private final Map<UUID, Map<String, Boolean>> cache = new ConcurrentHashMap<>();

    public SettingsManager(PSDK plugin) {
        this.plugin = plugin;
        try (Statement stmt = plugin.getDatabaseManager().getConnection().createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS player_settings (uuid TEXT NOT NULL, setting TEXT NOT NULL, value INTEGER NOT NULL DEFAULT 1, PRIMARY KEY (uuid, setting))");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao criar tabela player_settings", e);
        }
    }

    public boolean getSetting(UUID uuid, String setting) {
        Map<String, Boolean> playerSettings = cache.get(uuid);
        if (playerSettings != null && playerSettings.containsKey(setting)) {
            return playerSettings.get(setting);
        }
        // Fora da thread principal e sem cache: NÃO toca o banco (a conexão é só da main thread).
        if (!plugin.getServer().isPrimaryThread()) return true;
        return loadSetting(uuid, setting);
    }

    public void setSetting(UUID uuid, String setting, boolean value) {
        cache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(setting, value);
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "INSERT OR REPLACE INTO player_settings (uuid, setting, value) VALUES (?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, setting);
            ps.setInt(3, value ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao salvar setting", e);
        }
    }

    public void toggle(UUID uuid, String setting) {
        setSetting(uuid, setting, !getSetting(uuid, setting));
    }

    private boolean loadSetting(UUID uuid, String setting) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT value FROM player_settings WHERE uuid = ? AND setting = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, setting);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    boolean val = rs.getInt("value") == 1;
                    cache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(setting, val);
                    return val;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao carregar setting", e);
        }
        return true;
    }

    public void loadPlayer(UUID uuid) {
        Map<String, Boolean> settings = new ConcurrentHashMap<>();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT setting, value FROM player_settings WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    settings.put(rs.getString("setting"), rs.getInt("value") == 1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao carregar settings do jogador", e);
        }
        cache.put(uuid, settings);
    }

    public void unloadPlayer(UUID uuid) {
        cache.remove(uuid);
    }
}
