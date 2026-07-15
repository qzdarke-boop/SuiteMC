    package com.psdk.social.marriage;

import com.psdk.PSDK;
import com.psdk.vip.VipConfig;
import com.psdk.vip.util.GGWaveRewards;
import com.psdk.vip.util.GGWaveManager;
import com.psdk.vip.util.SkinRenderer;
import com.psdk.vip.util.SoundUtil;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class MarriageManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final boolean HAS_PAPI = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

    private static final int PROPOSAL_EXPIRE_S = 60;
    private static final long REMARRY_COOLDOWN_MS = 12L * 60 * 60 * 1000;
    private static final int    FWAVE_DURATION   = 20;
    private static final String FWAVE_ACTION_BAR =
            "<#cbd1d7>Comemore o casamento com a <b><#F5F528>ONDA</b> <#cbd1d7>de <#F5F528><bold>GG!</bold> <#848c94>(%time%s)";

    /** Uma linha por fileira da cabeça 8x8 (cabeça de quem solicitou o casamento). */
    private static final List<String> MARRIAGE_BROADCAST_LINES = List.of(
            "",
            "%tagProposer% <#cbd1d7>se casou com <reset>%tagTarget% <#cbd1d7>!",
            "",
            "<gray>▸ <#cbd1d7>Mande <#F5F528>GG <#cbd1d7>para parabenizar o casal!",
            "",
            "<#cbd1d7>Quer se casar também? Utilize <#fcc850>/casar <jogador>",
            "",
            ""
    );

    private final PSDK plugin;
    private final GGWaveManager fWaveManager;

    private record PendingProposal(UUID proposerId, String proposerName, long expiresAt) {}

    private final Map<UUID, PendingProposal> pending  = new ConcurrentHashMap<>();
    private final Map<UUID, UUID>            marriages = new ConcurrentHashMap<>();
    private final Map<UUID, Long>            remarryAvailableAt = new ConcurrentHashMap<>();

    public MarriageManager(PSDK plugin) {
        this.plugin       = plugin;
        this.fWaveManager = new GGWaveManager(plugin);
        loadMarriages();
        loadRemarryCooldowns();
        startCleanupTask();
    }

    private void loadRemarryCooldowns() {
        long now = System.currentTimeMillis();
        try (Connection conn = plugin.getDatabaseManager().newConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT player_uuid, available_at FROM marriage_remarry_cooldown WHERE available_at > ?")) {
            ps.setLong(1, now);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    remarryAvailableAt.put(
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getLong("available_at"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao carregar cooldown de casamento", e);
        }
        purgeExpiredRemarryCooldowns(now);
    }

    private void purgeExpiredRemarryCooldowns(long now) {
        remarryAvailableAt.entrySet().removeIf(e -> e.getValue() <= now);
        new BukkitRunnable() {
            @Override public void run() {
                try (Connection conn = plugin.getDatabaseManager().newConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "DELETE FROM marriage_remarry_cooldown WHERE available_at <= ?")) {
                    ps.setLong(1, now);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Erro ao limpar cooldown de casamento", e);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void setRemarryCooldown(UUID playerId, long availableAt) {
        remarryAvailableAt.put(playerId, availableAt);
        try (Connection conn = plugin.getDatabaseManager().newConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO marriage_remarry_cooldown (player_uuid, available_at) VALUES (?, ?) "
                             + "ON CONFLICT(player_uuid) DO UPDATE SET available_at = excluded.available_at")) {
            ps.setString(1, playerId.toString());
            ps.setLong(2, availableAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao salvar cooldown de casamento", e);
        }
    }

    private boolean canRemarry(Player player) {
        if (player.isOp() || player.hasPermission("psdk.marriage.bypass")) return true;
        return canRemarry(player.getUniqueId());
    }

    private boolean canRemarry(UUID playerId) {
        Long availableAt = remarryAvailableAt.get(playerId);
        return availableAt == null || System.currentTimeMillis() >= availableAt;
    }

    private String formatRemarryRemaining(UUID playerId) {
        Long availableAt = remarryAvailableAt.get(playerId);
        if (availableAt == null) return "";
        long ms = availableAt - System.currentTimeMillis();
        if (ms <= 0) return "";
        long hours = ms / 3_600_000;
        long minutes = (ms % 3_600_000) / 60_000;
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    // ── DB ────────────────────────────────────────────────────────────────────

    private void loadMarriages() {
        try (Connection conn = plugin.getDatabaseManager().newConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT uuid1, uuid2 FROM marriages");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID u1 = UUID.fromString(rs.getString("uuid1"));
                UUID u2 = UUID.fromString(rs.getString("uuid2"));
                marriages.put(u1, u2);
                marriages.put(u2, u1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao carregar casamentos", e);
        }
    }

    private void saveMarriageAsync(UUID u1, UUID u2) {
        new BukkitRunnable() {
            @Override public void run() {
                String sql = "INSERT OR REPLACE INTO marriages (uuid1, uuid2, married_at) VALUES (?, ?, ?)";
                try (Connection conn = plugin.getDatabaseManager().newConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, u1.toString());
                    ps.setString(2, u2.toString());
                    ps.setLong(3, System.currentTimeMillis());
                    ps.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Erro ao salvar casamento", e);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void deleteMarriageAsync(UUID u1, UUID u2) {
        new BukkitRunnable() {
            @Override public void run() {
                try (Connection conn = plugin.getDatabaseManager().newConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "DELETE FROM marriages WHERE uuid1=? OR uuid1=?")) {
                    ps.setString(1, u1.toString());
                    ps.setString(2, u2.toString());
                    ps.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Erro ao apagar casamento", e);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    // ── Proposals ─────────────────────────────────────────────────────────────

    public void sendProposal(Player proposer, Player target) {
        UUID pId = proposer.getUniqueId();
        UUID tId = target.getUniqueId();

        if (isMarried(pId)) {
            proposer.sendMessage(MM.deserialize("<#e22c27>Você já é casado(a)! Use /divorcio antes."));
            return;
        }
        if (!canRemarry(proposer)) {
            proposer.sendMessage(MM.deserialize("<#e22c27>Aguarde <#cbd1d7>" + formatRemarryRemaining(pId)
                    + " <#e22c27>para casar novamente."));
            return;
        }
        if (isMarried(tId)) {
            proposer.sendMessage(MM.deserialize("<#e22c27>" + target.getName() + " já é casado(a)!"));
            return;
        }
        if (!canRemarry(target)) {
            proposer.sendMessage(MM.deserialize("<#e22c27>" + target.getName()
                    + " <#e22c27>precisa aguardar <#cbd1d7>" + formatRemarryRemaining(tId)
                    + " <#e22c27>para casar novamente."));
            return;
        }
        if (pending.containsKey(tId)) {
            proposer.sendMessage(MM.deserialize("<#e22c27>" + target.getName() + " já tem um pedido pendente."));
            return;
        }

        long expires = System.currentTimeMillis() + PROPOSAL_EXPIRE_S * 1000L;
        pending.put(tId, new PendingProposal(pId, proposer.getName(), expires));

        String tag = resolveTag(proposer);
        String msg = tag + "<#fcc850>solicitou para casar com você! "
                + "Clique <click:run_command:'/casar aceitar " + pId + "'>"
                + "<#fcc850><bold>AQUI</bold></click>"
                + " <#fcc850>para aceitar o pedido de casamento!";

        target.sendMessage(MM.deserialize(msg));
        proposer.sendMessage(MM.deserialize("<#fcc850>Pedido de casamento enviado para <white>"
                + target.getName() + "<#fcc850>! Aguardando resposta..."));
    }

    public void acceptProposal(Player target, UUID proposerId) {
        UUID tId = target.getUniqueId();
        PendingProposal proposal = pending.get(tId);

        if (proposal == null || !proposal.proposerId().equals(proposerId)) {
            target.sendMessage(MM.deserialize("<#e22c27>Pedido de casamento não encontrado ou expirado."));
            return;
        }
        if (System.currentTimeMillis() > proposal.expiresAt()) {
            pending.remove(tId);
            target.sendMessage(MM.deserialize("<#e22c27>O pedido de casamento expirou."));
            return;
        }

        pending.remove(tId);

        Player proposer = Bukkit.getPlayer(proposerId);
        if (proposer != null) {
            if (!canRemarry(proposer)) {
                target.sendMessage(MM.deserialize("<#e22c27>O pedidor não pode casar agora (cooldown de 12h)."));
                return;
            }
        } else if (!canRemarry(proposerId)) {
            target.sendMessage(MM.deserialize("<#e22c27>O pedidor não pode casar agora (cooldown de 12h)."));
            return;
        }
        if (!canRemarry(target)) {
            target.sendMessage(MM.deserialize("<#e22c27>Aguarde <#cbd1d7>" + formatRemarryRemaining(tId)
                    + " <#e22c27>para casar novamente."));
            return;
        }

        marriages.put(tId, proposerId);
        marriages.put(proposerId, tId);
        saveMarriageAsync(tId, proposerId);

        String tagTarget   = resolveTag(target);
        String tagProposer = proposer != null ? resolveTag(proposer) : proposal.proposerName();
        String proposerSkinName = proposer != null ? proposer.getName() : proposal.proposerName();

        announceMarriage(proposerSkinName, tagProposer, tagTarget);
    }

    // ── FWave ─────────────────────────────────────────────────────────────────

    public void handleFWaveGG(Player player) {
        if (!fWaveManager.isActive()) return;
        if (fWaveManager.hasAlreadySentGG(player.getUniqueId())) {
            player.sendMessage(MM.deserialize(VipConfig.GG_ALREADY_SENT));
            return;
        }
        fWaveManager.markGGSent(player.getUniqueId());

        String color  = fWaveManager.getRandomColor(VipConfig.GG_COLORS);
        String msg    = VipConfig.GG_WAVE_FORMAT.replace("%gg%", color);

        if (HAS_PAPI) {
            try { msg = PlaceholderAPI.setPlaceholders(player, msg); } catch (Exception ignored) {}
        }
        msg = VipConfig.fillName(msg, player.getName());

        var comp = MM.deserialize(msg);
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(comp));
    }

    // ── Divorce ───────────────────────────────────────────────────────────────

    public void divorce(Player player) {
        UUID pId = player.getUniqueId();
        UUID partnerId = marriages.get(pId);
        if (partnerId == null) {
            player.sendMessage(MM.deserialize("<#e22c27>Você não está casado(a)."));
            return;
        }

        marriages.remove(pId);
        marriages.remove(partnerId);
        deleteMarriageAsync(pId, partnerId);

        long availableAt = System.currentTimeMillis() + REMARRY_COOLDOWN_MS;
        setRemarryCooldown(pId, availableAt);
        setRemarryCooldown(partnerId, availableAt);

        player.sendMessage(MM.deserialize("<#e22c27>Você se divorciou..."));
        player.sendMessage(MM.deserialize("<#cbd1d7>Aguarde <#fcc850>12 horas <#cbd1d7>para casar novamente."));
        Player partner = Bukkit.getPlayer(partnerId);
        if (partner != null) {
            partner.sendMessage(MM.deserialize("<#e22c27>" + player.getName()
                    + " pediu divórcio. Infelizmente, vocês não são mais casados."));
            partner.sendMessage(MM.deserialize("<#cbd1d7>Aguarde <#fcc850>12 horas <#cbd1d7>para casar novamente."));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public boolean isMarried(UUID uuid) { return marriages.containsKey(uuid); }
    public UUID getPartner(UUID uuid)   { return marriages.get(uuid); }
    public GGWaveManager getFWaveManager() { return fWaveManager; }

    public String resolveTag(Player player) {
        if (HAS_PAPI) {
            String prefix = PlaceholderAPI.setPlaceholders(player, "%luckperms_prefix%");
            String suffix = PlaceholderAPI.setPlaceholders(player, "%luckperms_suffix%");
            String name = player.getName();
            prefix = prefix.replace("%player_name%", name).replace("%player%", name);
            suffix = suffix.replace("%player_name%", name).replace("%player%", name);
            String tag = prefix + suffix;
            if (!tag.contains(name)) tag = tag + name;
            return tag;
        }
        return player.getName();
    }

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                pending.entrySet().removeIf(e -> now > e.getValue().expiresAt());
            }
        }.runTaskTimer(plugin, 600L, 600L);
    }

    /** Anúncio completo de casamento: som, title e chat com cabeça de quem solicitou. */
    private void announceMarriage(String proposerSkinName, String tagProposer, String tagTarget) {
        SoundUtil.sendGlobalSound("entity.lightning_bolt.thunder;1.0;1.0");

        Title.Times times = Title.Times.times(
                Duration.ofMillis(10 * 50L),
                Duration.ofMillis(70 * 50L),
                Duration.ofMillis(20 * 50L));

        Title title = Title.title(
                MM.deserialize(tagProposer),
                MM.deserialize("<#cbd1d7>Casou se com <reset>" + tagTarget + " <#cbd1d7>!"),
                times);

        Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(title));

        SkinRenderer.getSkinLines(proposerSkinName).thenAccept(lines ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (VipConfig.HEAD_TOP_MARGIN) sendBlankLine();

                    for (int i = 0; i < 8; i++) {
                        String headLine = (lines != null && lines.size() > i)
                                ? VipConfig.HEAD_SPACE_AFTER + lines.get(i)
                                : VipConfig.HEAD_SPACE_AFTER + "        ";
                        String textLine = i < MARRIAGE_BROADCAST_LINES.size()
                                ? MARRIAGE_BROADCAST_LINES.get(i)
                                        .replace("%tagProposer%", tagProposer)
                                        .replace("%tagTarget%", tagTarget)
                                : "";
                        var component = MM.deserialize(headLine + textLine);
                        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(component));
                    }

                    if (VipConfig.HEAD_BOTTOM_MARGIN) sendBlankLine();
                    fWaveManager.startWave(FWAVE_DURATION, FWAVE_ACTION_BAR,
                            GGWaveRewards.tokenReward(plugin));
                }));
    }

    private void sendBlankLine() {
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(MM.deserialize("")));
    }
}
