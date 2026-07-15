package com.psdk.thepit;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Reset de lançamento CONFIÁVEL para o inventário/ender chest/xp VANILLA (que vive no
 * NBT do jogador, não no banco). Apagar os arquivos {@code .dat} com o servidor rodando
 * é frágil (timing/permissão/re-save), por isso usamos o padrão "resetar no login":
 *
 * <p>O comando {@code /thepit resetall} grava um "epoch" de reset em {@code settings}.
 * Quando QUALQUER jogador entra (online que reconecta, offline, ou quem nunca mais
 * voltou — quando voltar), se ele ainda não foi resetado nesse epoch, limpamos tudo e
 * marcamos no PDC dele que já foi resetado. Funciona pra absolutamente todos.
 */
public class LaunchResetListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String SETTING_KEY = "launch_reset_epoch";

    private final PSDK plugin;
    private final NamespacedKey pdcKey;

    public LaunchResetListener(PSDK plugin) {
        this.plugin = plugin;
        this.pdcKey = new NamespacedKey(plugin, SETTING_KEY);
    }

    /** Marca AGORA como o instante do reset — todos serão limpos no próximo login. */
    public void markResetNow() {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "INSERT OR REPLACE INTO settings (key, value) VALUES ('" + SETTING_KEY + "', ?)")) {
            ps.setString(1, Long.toString(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("[RESET] Falha ao gravar epoch de reset: " + e.getMessage());
        }
    }

    /** Loga no console (no enable) se o reset-no-login está armado, pra você confirmar. */
    public void logArmedStatus() {
        long epoch = readEpoch();
        if (epoch > 0) {
            plugin.getLogger().info("[RESET] Reset de lançamento ARMADO (epoch=" + epoch
                    + "). Cada jogador que entrar e ainda não foi limpo terá inventário/EC/XP zerados.");
        } else {
            plugin.getLogger().info("[RESET] Reset de lançamento NÃO armado (nenhum /thepit resetall pendente).");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        long epoch = readEpoch();
        if (epoch <= 0) return;

        Player p = event.getPlayer();
        Long done = p.getPersistentDataContainer().get(pdcKey, PersistentDataType.LONG);
        if (done != null && done >= epoch) {
            return; // já resetado neste epoch
        }

        wipePlayer(p);

        // Marca como resetado neste epoch (não limpa de novo nos próximos logins).
        p.getPersistentDataContainer().set(pdcKey, PersistentDataType.LONG, epoch);
        p.sendMessage(MM.deserialize("<#fcc850>Seus dados foram resetados para o lançamento do servidor."));
        plugin.getLogger().info("[RESET] Inventário/EC/XP de " + p.getName() + " zerados no login (epoch=" + epoch + ").");

        // Re-limpa alguns ticks depois pra derrotar qualquer plugin/lógica que tente
        // restaurar o inventário logo após o join (kit do pit só vem em +10 ticks).
        new BukkitRunnable() {
            @Override public void run() {
                if (p.isOnline()) wipePlayer(p);
            }
        }.runTaskLater(plugin, 4L);
    }

    /** Zera tudo que é vanilla (NBT): inventário, armadura, off-hand, ender chest, xp e estado. */
    private void wipePlayer(Player p) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[4]);
        p.getInventory().setItemInOffHand(null);
        try { p.getOpenInventory().setCursor(null); } catch (Throwable ignored) {}
        p.getEnderChest().clear();
        p.setLevel(0);
        p.setExp(0f);
        p.setTotalExperience(0);
        for (PotionEffect ef : p.getActivePotionEffects()) p.removePotionEffect(ef.getType());
        p.setFireTicks(0);
        try { p.setHealth(p.getMaxHealth()); } catch (Throwable ignored) {}
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.updateInventory();
    }

    /** Epoch de reset atual (0 = nenhum reset pendente). */
    public long currentEpoch() {
        return readEpoch();
    }

    /** True se este jogador ainda NÃO foi limpo no epoch atual. */
    public boolean isPending(Player p, long epoch) {
        Long done = p.getPersistentDataContainer().get(pdcKey, PersistentDataType.LONG);
        return done == null || done < epoch;
    }

    private long readEpoch() {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT value FROM settings WHERE key = '" + SETTING_KEY + "'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return Long.parseLong(rs.getString("value"));
        } catch (Exception ignored) {}
        return 0L;
    }
}
