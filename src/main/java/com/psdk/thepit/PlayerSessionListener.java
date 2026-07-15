package com.psdk.thepit;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerSessionListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PSDK plugin;

    public PlayerSessionListener(PSDK plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getPlayerDataManager().loadPlayer(player);
        plugin.getEconomyManager().ensureAccount(player.getUniqueId(), player.getName());

        // Reaplica cooldowns de itens especiais persistidos (relog não burla).
        plugin.getAbilityCooldownManager().handleJoin(player);

        // Destino de reconexão: arena de PvP (sem Combat Log) volta ao mesmo lugar;
        // área segura vai ao spawn; posição inválida cai no spawn (fallback).
        Location target = plugin.getReconnectManager().resolveTarget(player.getUniqueId());
        if (target != null) {
            player.teleport(target);
        }

        CombatInventorySaveManager combatSave = plugin.getCombatInventorySaveManager();
        boolean restored = combatSave != null && combatSave.tryRestore(player);

        if (!restored && combatSave != null && combatSave.isRestoreMode()) {
            if (CombatInventorySaveManager.hasItems(player)) {
                plugin.getLogger().info("[CombatSave] " + player.getName()
                        + " entrou após crash sem backup PSDK — inventário veio do save do mundo.");
                player.sendMessage(MM.deserialize(
                        "<#fcc850>Inventário carregado do save do mundo <gray>— nenhum backup extra foi encontrado."));
            } else {
                plugin.getLogger().warning("[CombatSave] " + player.getName()
                        + " entrou após crash sem backup nem itens no save do mundo — kit será dado.");
                player.sendMessage(MM.deserialize(
                        "<#FF5555>Não foi possível recuperar seus itens após o crash. <gray>Um kit básico será entregue."));
            }
        }

        if (!restored) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) plugin.getKitManager().give(player);
                }
            }.runTaskLater(plugin, 10L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        plugin.getAfkManager().removeAfk(event.getEntity());
        CombatInventorySaveManager combatSave = plugin.getCombatInventorySaveManager();
        if (combatSave != null) {
            combatSave.clearAllSnapshotsForPlayer(event.getEntity().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        CombatInventorySaveManager combatSave = plugin.getCombatInventorySaveManager();
        boolean inCombat = plugin.getCombatManager().isInCombat(player);

        // No reinício/parada do servidor NÃO pune combat log (senão todo mundo em combate morre e perde tudo).
        if (!plugin.getServer().isStopping() && plugin.getCombatManager().shouldKillOnQuit(player)) {
            player.setHealth(0);
        } else if (plugin.getServer().isStopping() && combatSave != null) {
            combatSave.saveSync(player);
        } else if (!plugin.getServer().isStopping() && combatSave != null
                && !inCombat) {
            combatSave.clearAllSnapshotsForPlayerSync(player.getUniqueId());
        }

        // Sem Combat Log: salva a posição p/ restaurar na reconexão (arena de PvP volta
        // ao mesmo lugar; área segura vai ao spawn). Com Combat Log: NÃO salva — as
        // regras de combate seguem valendo e a restauração não pode burlar a punição.
        if (!inCombat) {
            plugin.getReconnectManager().save(player);
        } else {
            plugin.getReconnectManager().clear(player.getUniqueId());
        }

        // Persiste os cooldowns ativos do jogador (relog não burla) e libera memória.
        plugin.getAbilityCooldownManager().handleQuit(player);

        plugin.getCombatManager().clear(player.getUniqueId());
        plugin.getPlayerDataManager().unloadPlayer(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        Location deathSpawn = plugin.getDeathSpawnLocation();
        if (deathSpawn != null) {
            event.setRespawnLocation(deathSpawn);
        } else {
            Location spawn = plugin.getSpawnLocation();
            if (spawn != null) {
                event.setRespawnLocation(spawn);
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) plugin.getKitManager().give(player);
            }
        }.runTaskLater(plugin, 2L);
    }
}
