package com.psdk.thepit;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class CombatCommandListener implements Listener {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public CombatCommandListener(PSDK plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;
        if (player.hasPermission("psdk.combat.bypass")) return;
        if (!plugin.getCombatManager().isInCombat(player)) return;

        event.setCancelled(true);
        player.sendMessage(mm.deserialize("<#a4a4a4>Você está em combate! Aguarde sair de combate para usar este comando."));
    }
}
