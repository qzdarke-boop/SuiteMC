package com.psdk.crates;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Rastreia o tempo online de cada jogador e entrega chaves de crate automaticamente:
 * - A cada 4 horas online → 1 chave "rara"
 * - A cada 48 horas online → 1 chave "especial"
 */
public class PlaytimeKeyManager implements Listener {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private static final long RARA_THRESHOLD_MS     = 4L * 60 * 60 * 1000;   // 4 horas
    private static final long ESPECIAL_THRESHOLD_MS = 48L * 60 * 60 * 1000;  // 2 dias (48h)

    private static final String CRATE_RARA     = "rara";
    private static final String CRATE_ESPECIAL = "especial";

    private final PSDK plugin;

    /** Timestamp (System.currentTimeMillis) de quando o jogador começou a sessão ou último flush. */
    private final Map<UUID, Long> sessionStart = new ConcurrentHashMap<>();

    /** Tempo acumulado em memória desde o último flush ao DB (por jogador+crate). */
    private final Map<UUID, long[]> accumulated = new ConcurrentHashMap<>(); // [0]=rara, [1]=especial

    /** Keys pendentes (inventário cheio) em memória. */
    private final Map<UUID, int[]> pending = new ConcurrentHashMap<>(); // [0]=rara, [1]=especial

    public PlaytimeKeyManager(PSDK plugin) {
        this.plugin = plugin;
    }

    public void startTask() {
        // Carrega jogadores que já estejam online (caso de reload).
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!sessionStart.containsKey(uuid)) {
                sessionStart.put(uuid, System.currentTimeMillis());
                loadFromDB(uuid);
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L * 60, 20L * 60); // a cada 60 segundos
    }

    // ─── Eventos ────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        sessionStart.put(uuid, System.currentTimeMillis());
        loadFromDB(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        flushPlayer(uuid);
        sessionStart.remove(uuid);
        accumulated.remove(uuid);
        pending.remove(uuid);
    }

    // ─── Tick periódico ─────────────────────────────────────────────────────────

    private void tick() {
        long now = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();

            Long start = sessionStart.get(uuid);
            if (start == null) continue;

            long elapsed = now - start;
            sessionStart.put(uuid, now);

            long[] acc = accumulated.computeIfAbsent(uuid, k -> new long[2]);
            acc[0] += elapsed;
            acc[1] += elapsed;

            plugin.getTopStatsTracker().addPlaytime(uuid, player.getName(), elapsed);

            checkThreshold(player, uuid, acc);
            deliverPending(player, uuid);
        }
    }

    private void checkThreshold(Player player, UUID uuid, long[] acc) {
        while (acc[0] >= RARA_THRESHOLD_MS) {
            if (!awardKey(player, uuid, CRATE_RARA, 0)) break;
            acc[0] -= RARA_THRESHOLD_MS;
        }

        while (acc[1] >= ESPECIAL_THRESHOLD_MS) {
            if (!awardKey(player, uuid, CRATE_ESPECIAL, 1)) break;
            acc[1] -= ESPECIAL_THRESHOLD_MS;
        }
    }

    /**
     * Tenta entregar a chave. Retorna true se conseguiu (entregou ou colocou como pending).
     * Retorna false se a crate/key não existe (config inválida) — nesse caso o progresso
     * NÃO deve ser consumido.
     */
    private boolean awardKey(Player player, UUID uuid, String crateName, int idx) {
        Crate crate = plugin.getCrateManager().getCrate(crateName);
        if (crate == null) return false;

        ItemStack key = plugin.getKeyManager().createKeyItem(crate, 1);
        if (key == null) return false;

        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(key);
            sendRewardMessage(player, crateName);
        } else {
            int[] pend = pending.computeIfAbsent(uuid, k -> new int[2]);
            pend[idx]++;
        }

        saveToDB(uuid);
        return true;
    }

    private void deliverPending(Player player, UUID uuid) {
        int[] pend = pending.get(uuid);
        if (pend == null) return;

        for (int i = 0; i < 2; i++) {
            while (pend[i] > 0 && player.getInventory().firstEmpty() != -1) {
                String crateName = i == 0 ? CRATE_RARA : CRATE_ESPECIAL;
                Crate crate = plugin.getCrateManager().getCrate(crateName);
                if (crate == null) break;

                ItemStack key = plugin.getKeyManager().createKeyItem(crate, 1);
                if (key == null) break;

                player.getInventory().addItem(key);
                pend[i]--;
                sendRewardMessage(player, crateName);
            }
        }

        if (pend[0] == 0 && pend[1] == 0) {
            pending.remove(uuid);
            saveToDB(uuid);
        } else {
            saveToDB(uuid);
        }
    }

    private void sendRewardMessage(Player player, String crateName) {
        String timeLabel;
        String cor;
        if (CRATE_RARA.equals(crateName)) {
            timeLabel = "4 horas";
            cor = "<#55cdfc>";
        } else {
            timeLabel = "2 dias";
            cor = "<#b06dff>";
        }

        String nomeCapital = Character.toUpperCase(crateName.charAt(0)) + crateName.substring(1);
        player.sendMessage(mm.deserialize(
                cor + "Você ganhou uma chave " + nomeCapital +
                " <#848c94>por jogar " + timeLabel + "!"));
    }

    // ─── Persistência ───────────────────────────────────────────────────────────

    private void loadFromDB(UUID uuid) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT crate_name, accumulated_ms, pending FROM playtime_key_rewards WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();

            long[] acc = new long[2];
            int[] pend = new int[2];

            while (rs.next()) {
                String name = rs.getString("crate_name");
                int idx = CRATE_RARA.equals(name) ? 0 : CRATE_ESPECIAL.equals(name) ? 1 : -1;
                if (idx < 0) continue;
                acc[idx] = rs.getLong("accumulated_ms");
                pend[idx] = rs.getInt("pending");
            }

            accumulated.put(uuid, acc);
            if (pend[0] > 0 || pend[1] > 0) pending.put(uuid, pend);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao carregar playtime rewards", e);
        }
    }

    private void flushPlayer(UUID uuid) {
        Long start = sessionStart.get(uuid);
        if (start != null) {
            long elapsed = System.currentTimeMillis() - start;
            long[] acc = accumulated.computeIfAbsent(uuid, k -> new long[2]);
            acc[0] += elapsed;
            acc[1] += elapsed;
            plugin.getTopStatsTracker().addPlaytime(uuid, null, elapsed);
        }
        saveToDB(uuid);
    }

    private void saveToDB(UUID uuid) {
        long[] acc = accumulated.getOrDefault(uuid, new long[2]);
        int[] pend = pending.getOrDefault(uuid, new int[2]);

        String sql = """
                INSERT INTO playtime_key_rewards (player_uuid, crate_name, accumulated_ms, pending)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(player_uuid, crate_name) DO UPDATE SET accumulated_ms = excluded.accumulated_ms, pending = excluded.pending""";

        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, CRATE_RARA);
                ps.setLong(3, acc[0]);
                ps.setInt(4, pend[0]);
                ps.addBatch();

                ps.setString(1, uuid.toString());
                ps.setString(2, CRATE_ESPECIAL);
                ps.setLong(3, acc[1]);
                ps.setInt(4, pend[1]);
                ps.addBatch();

                ps.executeBatch();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao salvar playtime rewards", e);
        }
    }

    /** Salva todos os jogadores online (chamado no onDisable). */
    public void saveAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            flushPlayer(player.getUniqueId());
        }
    }
}
