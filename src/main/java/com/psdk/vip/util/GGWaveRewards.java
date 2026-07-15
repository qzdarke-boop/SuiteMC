package com.psdk.vip.util;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public final class GGWaveRewards {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private GGWaveRewards() {}

    public static void rewardParticipants(PSDK plugin, Set<UUID> participants, int totalGgs) {
        if (participants == null || participants.isEmpty()) return;

        String line = "<#10fc46>Onda de GG finalizada! <#cbd1d7>(" + totalGgs + " GGs) "
                + "<#10fc46>Você recebeu um token por participar!";
        var component = MM.deserialize(line);

        for (UUID id : participants) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) continue;
            plugin.getEconomyManager().addTokens(id, player.getName(), 1);
            player.sendMessage(component);
        }
    }

    public static java.util.function.BiConsumer<java.util.Set<UUID>, Integer> tokenReward(PSDK plugin) {
        return (participants, count) -> rewardParticipants(plugin, participants, count);
    }
}
