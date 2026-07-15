package com.psdk.vip;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

/**
 * Bônus por cargo VIP (grupos do LuckPerms).
 * A tag é lida AO VIVO via API do LuckPerms — assim, ao perder/trocar a tag,
 * o jogador passa a ganhar o valor do novo cargo imediatamente.
 * Ordem de prioridade: suite &gt; eternal &gt; elite &gt; premium (maior cargo vence).
 */
public final class VipBonus {

    private VipBonus() {}

    public enum Tier {
        SUITE(100, 2.25),
        ETERNAL(75, 1.75),
        ELITE(50, 1.50),
        PREMIUM(25, 1.25),
        NONE(0, 1.0);

        private final int bonusCoins;
        private final double miningMultiplier;

        Tier(int bonusCoins, double miningMultiplier) {
            this.bonusCoins = bonusCoins;
            this.miningMultiplier = miningMultiplier;
        }

        public int getBonusCoins() { return bonusCoins; }
        public double getMiningMultiplier() { return miningMultiplier; }
    }

    private static final String[] GROUPS = {"suite", "eternal", "elite", "premium"};
    private static final Tier[] TIERS = {Tier.SUITE, Tier.ETERNAL, Tier.ELITE, Tier.PREMIUM};

    public static Tier getTier(Player player) {
        if (player == null) return Tier.NONE;

        // 1) Via API do LuckPerms — reflete a tag atual (grupos herdados) com precisão.
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getPlayerAdapter(Player.class).getUser(player);
            if (user != null) {
                QueryOptions qo = lp.getContextManager().getQueryOptions(player);
                if (qo == null) qo = lp.getContextManager().getStaticQueryOptions();
                Set<String> groups = new HashSet<>();
                for (Group g : user.getInheritedGroups(qo)) {
                    groups.add(g.getName().toLowerCase());
                }
                for (int i = 0; i < GROUPS.length; i++) {
                    if (groups.contains(GROUPS[i])) return TIERS[i];
                }
                // Nenhum grupo casou pela API -> ainda tenta o fallback de permissão
                // (cobre contextos/edge cases em que getInheritedGroups não retornou o cargo).
            }
        } catch (Exception ignored) {
            // LuckPerms ausente ou erro de API -> cai no fallback de permissão.
        }

        // 2) Fallback: nó de permissão group.<nome>.
        for (int i = 0; i < GROUPS.length; i++) {
            if (player.hasPermission("group." + GROUPS[i])) return TIERS[i];
        }
        return Tier.NONE;
    }

    public static int getBonusCoins(Player player) {
        return getTier(player).getBonusCoins();
    }

    public static double getMiningMultiplier(Player player) {
        return getTier(player).getMiningMultiplier();
    }
}
