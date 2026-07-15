package com.psdk.thepit.topboard;

import com.psdk.PSDK;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class TopBoardManager {

    public static final String TAG = "psdk_topboard";
    private static final float VIEW_RANGE = 64f;
    private static final double HOLO_RANGE_SQ = 48.0 * 48.0;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PSDK plugin;
    private final TopQueryService queryService;
    private final Map<String, TopBoard> boards = new ConcurrentHashMap<>();
    private final Map<UUID, TopBoard> byInteraction = new ConcurrentHashMap<>();
    /** playerUuid -> boardId -> display entity uuid */
    private final Map<UUID, Map<String, UUID>> playerDisplays = new ConcurrentHashMap<>();
    /** playerUuid -> boardId -> view state */
    private final Map<UUID, Map<String, PlayerBoardView>> playerViews = new ConcurrentHashMap<>();

    public TopBoardManager(PSDK plugin, TopQueryService queryService) {
        this.plugin = plugin;
        this.queryService = queryService;
        startRefreshTask();
        startProximityTask();
    }

    public void loadAll() {
        boards.clear();
        byInteraction.clear();
        try (Connection conn = plugin.getDatabaseManager().newConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM top_boards");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                TopBoardType type = TopBoardType.fromId(rs.getString("type"));
                if (type == null) continue;
                World world = Bukkit.getWorld(rs.getString("world"));
                if (world == null) continue;

                Location loc = new Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"));
                TopBoard board = new TopBoard(rs.getString("id"), type, loc);
                String interactionUuid = rs.getString("interaction_uuid");
                if (interactionUuid != null && !interactionUuid.isBlank()) {
                    board.setInteractionUuid(UUID.fromString(interactionUuid));
                }
                boards.put(board.getId(), board);
                if (board.getInteractionUuid() != null) {
                    byInteraction.put(board.getInteractionUuid(), board);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao carregar top boards", e);
        }
    }

    public void spawnAll() {
        clearOrphans();
        for (TopBoard board : boards.values()) {
            spawnSharedInteraction(board);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerHolograms(player);
        }
    }

    public void despawnAll() {
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            removeAllPlayerHolograms(player);
        }
        for (TopBoard board : boards.values()) {
            removeEntity(board.getInteractionUuid());
        }
    }

    public Collection<TopBoard> getBoards() {
        return Collections.unmodifiableCollection(boards.values());
    }

    public TopBoard getByInteraction(UUID interactionUuid) {
        return byInteraction.get(interactionUuid);
    }

    /** Resolve o board pelo TextDisplay pessoal do jogador. */
    public TopBoard getByPlayerDisplay(UUID playerUuid, UUID displayUuid) {
        Map<String, UUID> displays = playerDisplays.get(playerUuid);
        if (displays == null) return null;
        for (Map.Entry<String, UUID> entry : displays.entrySet()) {
            if (displayUuid.equals(entry.getValue())) {
                return boards.get(entry.getKey());
            }
        }
        return null;
    }

    public boolean isTopBoardEntity(org.bukkit.entity.Entity entity) {
        return entity.getScoreboardTags().contains(TAG);
    }

    public TopBoard findNearest(Location loc, double maxDistSq) {
        TopBoard nearest = null;
        double best = maxDistSq;
        for (TopBoard board : boards.values()) {
            Location bl = board.getLocation();
            if (bl == null || !bl.getWorld().equals(loc.getWorld())) continue;
            double d = bl.distanceSquared(loc);
            if (d <= best) {
                best = d;
                nearest = board;
            }
        }
        return nearest;
    }

    public TopBoard createBoard(TopBoardType type, Location loc) {
        String id = UUID.randomUUID().toString();
        TopBoard board = new TopBoard(id, type, loc);
        boards.put(id, board);
        spawnSharedInteraction(board);
        saveBoard(board);
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerHolograms(player);
        }
        return board;
    }

    public boolean removeBoard(TopBoard board) {
        if (board == null) return false;
        removeEntity(board.getInteractionUuid());
        if (board.getInteractionUuid() != null) byInteraction.remove(board.getInteractionUuid());

        for (Map<String, UUID> displays : playerDisplays.values()) {
            UUID displayId = displays.remove(board.getId());
            removeEntity(displayId);
        }
        for (Map<String, PlayerBoardView> views : playerViews.values()) {
            views.remove(board.getId());
        }

        boards.remove(board.getId());
        try (Connection conn = plugin.getDatabaseManager().newConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM top_boards WHERE id = ?")) {
            ps.setString(1, board.getId());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao remover top board", e);
            return false;
        }
    }

    public void cyclePeriod(Player player, TopBoard board) {
        // Categorias sem rastreio semanal/mensal ficam travadas em Global.
        if (!board.getType().supportsPeriods()) return;
        getViewState(player, board).cyclePeriod();
        refreshPlayerHologram(player, board);
    }

    public void nextPage(Player player, TopBoard board) {
        getViewState(player, board).nextPage();
        refreshPlayerHologram(player, board);
    }

    public void maintainBoardEntities() {
        for (TopBoard board : boards.values()) {
            Location loc = board.getLocation();
            if (loc == null || loc.getWorld() == null) continue;
            if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;
            ensureBoardInteraction(board);
        }
    }

    public void updatePlayerHolograms(Player player) {
        if (!player.isOnline()) return;
        Location ploc = player.getLocation();

        for (TopBoard board : boards.values()) {
            Location bloc = board.getLocation();
            if (bloc == null || !bloc.getWorld().equals(ploc.getWorld())
                    || ploc.distanceSquared(bloc) > HOLO_RANGE_SQ) {
                removePlayerHologram(player, board);
                continue;
            }
            ensurePlayerHologram(player, board);
        }
    }

    public void removeAllPlayerHolograms(Player player) {
        Map<String, UUID> displays = playerDisplays.remove(player.getUniqueId());
        if (displays != null) {
            for (UUID displayId : displays.values()) {
                removeEntity(displayId);
            }
        }
        playerViews.remove(player.getUniqueId());
    }

    public void refreshAllPlayerHolograms() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Map<String, UUID> displays = playerDisplays.get(player.getUniqueId());
            if (displays == null || displays.isEmpty()) continue;
            for (String boardId : displays.keySet()) {
                TopBoard board = boards.get(boardId);
                if (board != null) refreshPlayerHologram(player, board);
            }
        }
    }

    private PlayerBoardView getViewState(Player player, TopBoard board) {
        return playerViews
                .computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(board.getId(), k -> {
                    PlayerBoardView view = new PlayerBoardView();
                    if (!board.getType().supportsPeriods()) view.setPeriod(TopPeriod.GLOBAL);
                    return view;
                });
    }

    private void saveBoard(TopBoard board) {
        String sql = """
                INSERT INTO top_boards (id, type, world, x, y, z, display_uuid, interaction_uuid, period, page)
                VALUES (?, ?, ?, ?, ?, ?, '', ?, 'weekly', 0)
                ON CONFLICT(id) DO UPDATE SET
                    type = excluded.type,
                    world = excluded.world,
                    x = excluded.x,
                    y = excluded.y,
                    z = excluded.z,
                    interaction_uuid = excluded.interaction_uuid""";
        Location loc = board.getLocation();
        if (loc == null) return;
        try (Connection conn = plugin.getDatabaseManager().newConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, board.getId());
            ps.setString(2, board.getType().getId());
            ps.setString(3, loc.getWorld().getName());
            ps.setDouble(4, loc.getX());
            ps.setDouble(5, loc.getY());
            ps.setDouble(6, loc.getZ());
            ps.setString(7, board.getInteractionUuid() != null ? board.getInteractionUuid().toString() : "");
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao salvar top board", e);
        }
    }

    private void ensureBoardInteraction(TopBoard board) {
        UUID uuid = board.getInteractionUuid();
        if (uuid != null) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity instanceof Interaction interaction && interaction.isValid()) {
                byInteraction.putIfAbsent(uuid, board);
                return;
            }
            byInteraction.remove(uuid);
        }
        spawnSharedInteraction(board);
    }

    private void spawnSharedInteraction(TopBoard board) {
        Location loc = board.getLocation();
        if (loc == null || loc.getWorld() == null) return;

        removeEntity(board.getInteractionUuid());

        // Hitbox no centro do holograma (na altura do texto), nao no chao
        Location interactLoc = loc.clone().add(0, 1.2, 0);
        Interaction interaction = loc.getWorld().spawn(interactLoc, Interaction.class, e -> {
            e.setInteractionWidth(3.2f);
            e.setInteractionHeight(3.0f);
            e.setResponsive(true);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.addScoreboardTag(TAG);
        });
        board.setInteractionUuid(interaction.getUniqueId());
        byInteraction.put(interaction.getUniqueId(), board);
        saveBoard(board);
    }

    private void ensurePlayerHologram(Player player, TopBoard board) {
        Map<String, UUID> displays = playerDisplays.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
        UUID existing = displays.get(board.getId());
        if (existing != null) {
            Entity entity = Bukkit.getEntity(existing);
            if (entity instanceof TextDisplay td && td.isValid()) {
                refreshPlayerHologram(player, board);
                return;
            }
            displays.remove(board.getId());
        }
        spawnPlayerHologram(player, board);
    }

    private void spawnPlayerHologram(Player player, TopBoard board) {
        Location loc = board.getLocation();
        if (loc == null || loc.getWorld() == null) return;

        Location displayLoc = loc.clone().add(0, 1.8, 0);
        TextDisplay display = loc.getWorld().spawn(displayLoc, TextDisplay.class, e -> {
            e.text(Component.empty());
            e.setBillboard(Display.Billboard.VERTICAL);
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            e.setSeeThrough(false);
            e.setShadowed(false);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setLineWidth(320);
            e.setViewRange(VIEW_RANGE);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setVisibleByDefault(false);
            e.addScoreboardTag(TAG);
        });

        player.showEntity(plugin, display);
        playerDisplays.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(board.getId(), display.getUniqueId());
        refreshPlayerHologram(player, board);
    }

    private void removePlayerHologram(Player player, TopBoard board) {
        Map<String, UUID> displays = playerDisplays.get(player.getUniqueId());
        if (displays == null) return;
        UUID displayId = displays.remove(board.getId());
        removeEntity(displayId);
    }

    private void refreshPlayerHologram(Player player, TopBoard board) {
        PlayerBoardView view = getViewState(player, board);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<TopEntry> page = queryService.getPage(board.getType(), view.getPeriod(), view.getPage());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                Component text = buildDisplayText(board, view, page);
                applyPlayerDisplayText(player, board, text);
            });
        });
    }

    private void applyPlayerDisplayText(Player player, TopBoard board, Component text) {
        if (!player.isOnline()) return;
        Map<String, UUID> displays = playerDisplays.get(player.getUniqueId());
        if (displays == null) return;
        UUID displayId = displays.get(board.getId());
        if (displayId == null) return;
        Entity entity = Bukkit.getEntity(displayId);
        if (entity instanceof TextDisplay td && td.isValid()) {
            td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            td.text(text);
        }
    }

    private Component buildDisplayText(TopBoard board, PlayerBoardView view, List<TopEntry> entries) {
        TopBoardType type = board.getType();
        Component result = MM.deserialize("<!italic>" + type.getColor() + "<bold>" + type.getTitle() + "</bold><reset>\n")
                .append(MM.deserialize("<!italic><reset><#848c94>" + view.getPeriod().getDisplay() + "\n\n"));

        int startRank = view.rankStart();
        for (int i = 0; i < TopQueryService.PAGE_SIZE; i++) {
            int rank = startRank + i;
            if (i < entries.size() && entries.get(i).value() > 0) {
                TopEntry entry = entries.get(i);
                String value = TopBoardFormat.formatValue(type, entry.value());
                result = result.append(TopBoardFormat.buildHologramRankLine(rank, entry, value));
            } else {
                result = result.append(TopBoardFormat.buildHologramEmptyLine(rank));
            }
        }

        result = result.append(MM.deserialize(
                "\n<!italic><reset><#848c94>" + view.rankStart() + "–" + view.rankEnd()
                        + "  <#cbd1d7>|  " + view.getPeriod().getDisplay()));
        return result;
    }

    private void startRefreshTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                refreshAllPlayerHolograms();
            }
        }.runTaskTimer(plugin, 300L, 300L);
    }

    private void startProximityTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                maintainBoardEntities();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updatePlayerHolograms(player);
                }
            }
        }.runTaskTimer(plugin, 40L, 40L);
    }

    public void clearOrphans() {
        Set<UUID> owned = new HashSet<>();
        for (TopBoard board : boards.values()) {
            if (board.getInteractionUuid() != null) owned.add(board.getInteractionUuid());
        }
        for (Map<String, UUID> map : playerDisplays.values()) {
            owned.addAll(map.values());
        }

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!entity.getScoreboardTags().contains(TAG)) continue;
                if (!owned.contains(entity.getUniqueId())) {
                    entity.remove();
                }
            }
        }
    }

    private void removeEntity(UUID uuid) {
        if (uuid == null) return;
        Entity entity = Bukkit.getEntity(uuid);
        if (entity != null) entity.remove();
    }
}
