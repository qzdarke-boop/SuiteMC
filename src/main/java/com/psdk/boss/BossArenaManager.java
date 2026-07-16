package com.psdk.boss;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Arena do boss: define uma região (cubo via /bossarena pos1/pos2), faz o regen
 * automático dos blocos e impede que jogadores saiam (quem sai fica imóvel 3s e
 * é levado ao spawn).
 *
 * O snapshot/regen usa o MESMO método otimizado da arena principal (ThePit):
 *  • snapshot fica no SQLite (tabela boss_arena_blocks) — SEM limite de RAM e
 *    sobrevive a reinício do servidor (não precisa re-salvar após restart);
 *  • salvamento por streaming (scanner main thread -> fila -> escritor async);
 *  • regen por streaming (leitor/parser async -> fila -> aplicador main thread
 *    por ORÇAMENTO DE TEMPO ~30ms/tick, pulando blocos já corretos). Sem lag.
 */
public class BossArenaManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // ============== AJUSTES (EDITE AQUI) ==============
    private static final int REGEN_MINUTOS = 30;   // regen automático a cada X minutos
    private static final int AVISO_MINUTOS = 5;    // avisa X minutos antes do regen
    private static final int FORA_TICKS    = 60;   // 3s imóvel ao sair da arena (20 ticks = 1s)
    // ==================================================

    // Otimização (igual à arena principal — streaming SQLite, sem limite de RAM).
    private static final int  SAVE_BLOCKS_PER_TICK    = 8000;
    private static final int  DB_BATCH_SIZE           = 10000;
    private static final long RESET_TICK_BUDGET_NANOS = 30_000_000L; // ~30ms/tick (adaptativo)
    private static final int  RESET_DB_FETCH_SIZE     = 20_000;
    private static final int  QUEUE_CAPACITY          = 50_000;      // RAM constante (streaming)
    private static final long MAX_VOLUME              = 50_000_000L; // 50 milhões de blocos (igual à principal)

    private final PSDK plugin;

    private World world;
    private int minX, minY, minZ, maxX, maxY, maxZ;
    private boolean defined = false;
    private boolean saving = false, resetting = false;
    private long blocksInDb = 0;                    // tamanho do snapshot salvo no SQLite

    private Location corner1, corner2;              // cantos temporários (pos1/pos2)
    private int minutesUntilRegen = REGEN_MINUTOS;

    // Boundary
    private final Set<UUID> insideArena = new HashSet<>();
    private final Map<UUID, Location> rooted = new HashMap<>();

    public BossArenaManager(PSDK plugin) {
        this.plugin = plugin;
        loadBounds();
        startBoundaryTask();
        startRegenTask();
    }

    // ───────────────────────── definição da região ─────────────────────────
    public void setCorner1(Location loc) { corner1 = loc.clone(); computeBounds(); }
    public void setCorner2(Location loc) { corner2 = loc.clone(); computeBounds(); }

    private void computeBounds() {
        if (corner1 == null || corner2 == null) return;
        if (corner1.getWorld() == null || corner1.getWorld() != corner2.getWorld()) return;
        world = corner1.getWorld();
        minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
        minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        maxY = Math.max(corner1.getBlockY(), corner2.getBlockY());
        maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());
        defined = true;
        saveBounds();
    }

    public boolean isDefined()   { return defined; }
    public boolean hasSnapshot() { return blocksInDb > 0; }
    public boolean isSaving()    { return saving; }
    public long volume() {
        if (!defined) return 0;
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    private boolean isInside(Location loc) {
        if (!defined || loc.getWorld() != world) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    private boolean nearArena(Location loc, int margin) {
        if (!defined || loc.getWorld() != world) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= minX - margin && x <= maxX + margin
                && y >= minY - margin && y <= maxY + margin
                && z >= minZ - margin && z <= maxZ + margin;
    }

    // ───────────────────────── snapshot (streaming -> SQLite) ─────────────────────────
    /** Captura o estado atual da arena pro SQLite (sem limite de RAM). Async. */
    public void saveSnapshot(Player requester) {
        if (saving) { msg(requester, "<#FF0000>Já existe um salvamento em andamento."); return; }
        if (!defined) { msg(requester, "<#FF0000>Defina os 2 cantos primeiro (/bossarena pos1 e pos2)."); return; }
        final long volume = volume();
        if (volume > MAX_VOLUME) {
            msg(requester, "<#FF0000>Arena grande demais: <#6817ff>" + String.format("%,d", volume)
                    + " <#FF0000>blocos (max <#6817ff>" + String.format("%,d", MAX_VOLUME) + "<#FF0000>).");
            return;
        }

        saving = true;
        final World w = world;
        final int aMinX = minX, aMinY = minY, aMinZ = minZ, aMaxX = maxX, aMaxY = maxY, aMaxZ = maxZ;
        msg(requester, "<#6817ff>Salvando a arena do boss (" + String.format("%,d", volume) + " blocos)...");

        final LinkedBlockingQueue<BlockRow> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        final AtomicBoolean scanDone = new AtomicBoolean(false);

        // ── Escritor assíncrono (conexão própria) ──
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long written = 0;
            try (Connection conn = plugin.getDatabaseManager().newConnection()) {
                conn.setAutoCommit(false);
                try {
                    try (Statement st = conn.createStatement()) { st.execute("DELETE FROM boss_arena_blocks"); }
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO boss_arena_blocks (x, y, z, block_data) VALUES (?, ?, ?, ?)")) {
                        int batch = 0;
                        while (true) {
                            BlockRow row = queue.poll();
                            if (row == null) {
                                if (scanDone.get() && queue.isEmpty()) break;
                                try { Thread.sleep(5); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                                continue;
                            }
                            ins.setInt(1, row.x()); ins.setInt(2, row.y()); ins.setInt(3, row.z()); ins.setString(4, row.data());
                            ins.addBatch();
                            written++;
                            if (++batch % DB_BATCH_SIZE == 0) { ins.executeBatch(); conn.commit(); }
                        }
                        ins.executeBatch();
                    }
                    conn.commit();
                } catch (SQLException e) {
                    try { conn.rollback(); } catch (SQLException ignored) {}
                    throw e;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "[BossArena] erro ao salvar snapshot", e);
            }
            final long total = written;
            Bukkit.getScheduler().runTask(plugin, () -> {
                blocksInDb = total;
                saving = false;
                placedBlocks.clear(); // o estado salvo passa a ser a "estrutura original"
                msg(requester, "<#10fc46>Arena do boss salva! <#6817ff>" + String.format("%,d", total) + " <#10fc46>blocos no SQLite.");
            });
        });

        // ── Scanner na main thread (acesso a blocos exige main thread) ──
        new BukkitRunnable() {
            int cx = aMinX, cy = aMinY, cz = aMinZ;
            final HashMap<String, String> intern = new HashMap<>();   // palette: deduplica strings
            @Override public void run() {
                int processed = 0;
                while (processed < SAVE_BLOCKS_PER_TICK) {
                    if (cx > aMaxX) break;
                    Block b = w.getBlockAt(cx, cy, cz);
                    String data = b.getBlockData().getAsString();
                    String canon = intern.putIfAbsent(data, data);
                    if (canon != null) data = canon;
                    if (!queue.offer(new BlockRow(cx, cy, cz, data))) return; // fila cheia: tenta no próximo tick
                    processed++;
                    if (++cz > aMaxZ) { cz = aMinZ; if (++cy > aMaxY) { cy = aMinY; cx++; } }
                }
                if (cx > aMaxX) { cancel(); scanDone.set(true); }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private record BlockRow(int x, int y, int z, String data) {}
    private record ParsedBlock(int x, int y, int z, BlockData data) {}

    // ───────────────────────── regen (streaming <- SQLite) ─────────────────────────
    /** Regenera a arena a partir do snapshot no SQLite (orçamento de tempo, sem lag). */
    public void regen() {
        if (resetting || !defined || blocksInDb == 0 || world == null) return;
        resetting = true;
        plugin.getServer().sendMessage(MM.deserialize(" "));
        plugin.getServer().sendMessage(MM.deserialize("<#fc9d1a>A arena do boss está sendo regenerada..."));
        plugin.getServer().sendMessage(MM.deserialize(" "));

        final World w = world;
        final LinkedBlockingQueue<ParsedBlock> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        final AtomicBoolean readDone = new AtomicBoolean(false);

        // ── Leitor + parser assíncrono (cursor único, palette cache) ──
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final HashMap<String, BlockData> palette = new HashMap<>();
            try (Connection conn = plugin.getDatabaseManager().newConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT x, y, z, block_data FROM boss_arena_blocks")) {
                ps.setFetchSize(RESET_DB_FETCH_SIZE);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String data = rs.getString(4);
                        BlockData bd = palette.get(data);
                        if (bd == null) {
                            try { bd = Bukkit.createBlockData(data); }
                            catch (IllegalArgumentException ex) { continue; } // estado inválido -> pula
                            palette.put(data, bd);
                        }
                        try { queue.put(new ParsedBlock(rs.getInt(1), rs.getInt(2), rs.getInt(3), bd)); }
                        catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "[BossArena] erro ao ler snapshot", e);
            } finally {
                readDone.set(true);
            }
        });

        // ── Aplicador na main thread (por orçamento de tempo) ──
        new BukkitRunnable() {
            @Override public void run() {
                final long deadline = System.nanoTime() + RESET_TICK_BUDGET_NANOS;
                int sinceCheck = 0;
                ParsedBlock row;
                while ((row = queue.poll()) != null) {
                    Block b = w.getBlockAt(row.x(), row.y(), row.z());
                    if (!b.getBlockData().equals(row.data())) b.setBlockData(row.data(), false); // sem física
                    if ((++sinceCheck & 1023) == 0 && System.nanoTime() >= deadline) break;
                }
                if (readDone.get() && queue.isEmpty()) {
                    cancel();
                    resetting = false;
                    placedBlocks.clear(); // arena restaurada -> não há mais blocos de jogador
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void startRegenTask() {
        new BukkitRunnable() {
            @Override public void run() {
                if (!defined || blocksInDb == 0) return;
                minutesUntilRegen--;
                if (minutesUntilRegen == AVISO_MINUTOS) {
                    plugin.getServer().sendMessage(MM.deserialize(" "));
                    plugin.getServer().sendMessage(MM.deserialize("<#e22c27>A arena vai reiniciar daqui <#6817ff>" + AVISO_MINUTOS + " minutos<#e22c27>! Cuidado!"));
                    plugin.getServer().sendMessage(MM.deserialize(" "));
                }
                if (minutesUntilRegen <= 0) {
                    regen();
                    minutesUntilRegen = REGEN_MINUTOS;
                }
            }
        }.runTaskTimer(plugin, 1200L, 1200L); // a cada 1 minuto
    }

    // ───────────────────────── boundary (sair da arena) ─────────────────────────
    private void startBoundaryTask() {
        new BukkitRunnable() {
            @Override public void run() {
                if (!defined || world == null) return;
                for (Player p : world.getPlayers()) {
                    UUID id = p.getUniqueId();
                    if (rooted.containsKey(id)) continue;          // já está saindo
                    if (isInside(p.getLocation())) {
                        insideArena.add(id);
                    } else if (insideArena.remove(id) && nearArena(p.getLocation(), 6)) {
                        handleLeave(p);                            // estava dentro, saiu andando -> pune
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 4L);
    }

    private void handleLeave(Player p) {
        if (p.hasPermission("psdk.boss.arenabypass")) return;
        final UUID id = p.getUniqueId();
        if (rooted.containsKey(id)) return;
        rooted.put(id, p.getLocation().clone());
        final boolean prevFlight = p.getAllowFlight();
        p.setAllowFlight(true);   // evita kick de "flying" enquanto travado
        new BukkitRunnable() {
            int t = FORA_TICKS;
            @Override public void run() {
                Player pl = plugin.getServer().getPlayer(id);
                if (pl == null || !pl.isOnline() || !rooted.containsKey(id)) {
                    rooted.remove(id);
                    cancel();
                    return;
                }
                pl.setFlying(false);
                if (t <= 0) {
                    rooted.remove(id);
                    pl.setAllowFlight(prevFlight);
                    Location spawn = plugin.getSpawnLocation();
                    if (spawn != null) pl.teleport(spawn);
                    pl.sendActionBar(MM.deserialize("<#FF0000>Você saiu da arena!"));
                    cancel();
                    return;
                }
                int seg = (t + 19) / 20;
                pl.sendActionBar(MM.deserialize("<#FF0000>Fora da arena! Voltando ao spawn em <bold>" + seg + "</bold>s"));
                t--;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // Trava a posição (deixa só olhar) de quem saiu da arena.
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location lock = rooted.get(event.getPlayer().getUniqueId());
        if (lock == null) return;
        Location to = event.getTo();
        if (to == null) return;
        if (to.getX() != lock.getX() || to.getY() != lock.getY() || to.getZ() != lock.getZ()) {
            Location held = lock.clone();
            held.setYaw(to.getYaw());
            held.setPitch(to.getPitch());
            event.setTo(held);
        }
    }

    // ───────────────────────── proteção dos blocos da arena ─────────────────────────
    // Blocos colocados por jogadores dentro da arena (podem ser quebrados/explodidos);
    // a estrutura original (snapshot) é protegida. Limpa no save e no regen.
    private final Set<Long> placedBlocks = new HashSet<>();

    private static long packKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF))
                | ((long) (z & 0x3FFFFFF) << 26)
                | ((long) (y & 0xFFF) << 52);
    }
    private void markPlaced(int x, int y, int z)        { placedBlocks.add(packKey(x, y, z)); }
    private void unmarkPlaced(int x, int y, int z)      { placedBlocks.remove(packKey(x, y, z)); }
    private boolean isPlayerPlaced(int x, int y, int z) { return placedBlocks.contains(packKey(x, y, z)); }

    private boolean canBuild(Player p) {
        return p.isOp() || p.hasPermission("psdk.boss.admin");
    }

    // Quebrar: pode quebrar bloco que VOCÊ colocou; a estrutura original é protegida.
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        Block b = event.getBlock();
        if (!isInside(b.getLocation())) return;
        if (canBuild(p)) return;
        if (isPlayerPlaced(b.getX(), b.getY(), b.getZ())) {
            unmarkPlaced(b.getX(), b.getY(), b.getZ()); // bloco colocado por jogador -> liberado
            return;
        }
        event.setCancelled(true);
        p.sendActionBar(MM.deserialize("<#e22c27>Você não pode quebrar a estrutura da arena do boss!"));
    }

    // Colocar: liberado dentro da arena; o bloco é marcado pra poder ser quebrado/explodido
    // e some no próximo regen (volta ao snapshot original).
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block b = event.getBlock();
        if (!isInside(b.getLocation())) return;
        markPlaced(b.getX(), b.getY(), b.getZ());
    }

    // Explosões só destroem blocos COLOCADOS por jogadores; a estrutura original é protegida.
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::protectFromExplosion);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::protectFromExplosion);
    }

    /** true = remove da lista (protege). Mantém apenas blocos colocados por jogadores. */
    private boolean protectFromExplosion(Block b) {
        if (!isInside(b.getLocation())) return false; // fora da arena: explode normal
        if (isPlayerPlaced(b.getX(), b.getY(), b.getZ())) {
            unmarkPlaced(b.getX(), b.getY(), b.getZ());
            return false; // bloco do jogador: pode explodir
        }
        return true; // estrutura original: protegida
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        insideArena.remove(id);
        if (rooted.remove(id) != null
                && p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
            p.setFlying(false);
            p.setAllowFlight(false);
        }
    }

    private void msg(Player p, String mini) {
        if (p != null && p.isOnline()) p.sendMessage(MM.deserialize(mini));
    }

    // ───────────────────────── persistência ─────────────────────────
    private void saveBounds() {
        String val = world.getName() + "," + minX + "," + minY + "," + minZ + "," + maxX + "," + maxY + "," + maxZ;
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "INSERT OR REPLACE INTO settings (key, value) VALUES ('boss_arena_bounds', ?)")) {
            ps.setString(1, val);
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("[BossArena] erro ao salvar limites: " + e.getMessage());
        }
    }

    private void loadBounds() {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT value FROM settings WHERE key = 'boss_arena_bounds'")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String[] p = rs.getString("value").split(",");
                    if (p.length >= 7) {
                        World w = plugin.getServer().getWorld(p[0]);
                        if (w != null) {
                            world = w;
                            minX = Integer.parseInt(p[1]); minY = Integer.parseInt(p[2]); minZ = Integer.parseInt(p[3]);
                            maxX = Integer.parseInt(p[4]); maxY = Integer.parseInt(p[5]); maxZ = Integer.parseInt(p[6]);
                            defined = true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[BossArena] erro ao carregar limites: " + e.getMessage());
        }
        // Conta o snapshot já salvo no SQLite (sobrevive a restart — não precisa re-salvar).
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT COUNT(*) FROM boss_arena_blocks");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) blocksInDb = rs.getLong(1);
        } catch (Exception ignored) {
            // tabela ainda não existe (primeira migração) -> snapshot vazio
        }
    }
}
