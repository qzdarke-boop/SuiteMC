package com.psdk.thepit;

import com.psdk.PSDK;
import com.psdk.economy.EconomyManager;
import com.psdk.vip.VipBonus;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class LevelManager {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    public static final int KILL_COINS_REWARD = 75;
    public static final int LEVEL_REWARD_BASE = 25;
    public static final int LEVEL_REWARD_STEP = 25;
    public static final int MAX_LEVEL = 100;
    public static final int BAR_LENGTH = 10;

    private final PSDK plugin;

    public LevelManager(PSDK plugin) {
        this.plugin = plugin;
    }

    public int getLevel(int kills) {
        int level = 1;
        int needed = 0;
        for (int i = 1; i <= MAX_LEVEL; i++) {
            needed += (i + 1);
            if (kills >= needed) level = i + 1;
            else return level;
        }
        return level;
    }

    public int killsForLevel(int level) {
        if (level <= 1) return 0;
        return (level * (level + 1) / 2) - 1;
    }

    public int killsForNextLevel(int level) {
        return killsForLevel(level + 1);
    }

    public String buildBar(int kills, int level) {
        int prev   = killsForLevel(level);
        int next   = killsForNextLevel(level);
        int prog   = Math.max(0, kills - prev);
        int needed = Math.max(1, next - prev);
        int filled = (int) Math.round((double) prog * BAR_LENGTH / needed);
        filled = Math.max(0, Math.min(BAR_LENGTH, filled));

        StringBuilder sb = new StringBuilder();
        sb.append("<#10fc46>");
        for (int i = 0; i < filled; i++) sb.append('▬');
        sb.append("<#a4a4a4>");
        for (int i = 0; i < (BAR_LENGTH - filled); i++) sb.append('▬');
        return sb.toString();
    }

    public void onKill(Player killer) {
        if (killer == null) return;

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(killer);
        if (data == null) return;

        int oldLevel = getLevel(data.getKills());
        data.addKill();
        plugin.getTopStatsTracker().addKill(killer.getUniqueId(), killer.getName());
        int newLevel = getLevel(data.getKills());
        if (newLevel != data.getLevel()) data.setLevel(newLevel);

        EconomyManager eco = plugin.getEconomyManager();
        int vipBonus = VipBonus.getBonusCoins(killer);
        int totalKillReward = KILL_COINS_REWARD + vipBonus;
        eco.addCoins(killer.getUniqueId(), killer.getName(), totalKillReward);

        killer.sendMessage(mm.deserialize("<#fcc850>+" + totalKillReward + " coins"));

        if (newLevel > oldLevel) {
            int reward = LEVEL_REWARD_BASE + ((newLevel - 1) * LEVEL_REWARD_STEP);
            eco.addCoins(killer.getUniqueId(), killer.getName(), reward);

            killer.sendMessage(mm.deserialize("<#fcc850>Você subiu de level! <bold>GG!"));
            killer.sendMessage(mm.deserialize(
                    "<#a4a4a4>Agora você é Level <#e22c27>" + newLevel
                    + " <#a4a4a4>| <#efa600>Você recebeu +" + reward + " coins de recompensa!"));
            killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        }
    }
}
