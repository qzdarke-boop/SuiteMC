package com.psdk.cage;

import com.psdk.PSDK;
import com.psdk.region.RegionFlag;
import com.psdk.thepit.ArenaManager;
import com.psdk.region.RegionManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gerenciador central das Jaulas (item especial).
 *
 * <p>Responsável por: criação com validação de volume completo, casca temporária com
 * rollback, índices espaciais (por mundo/chunk/bloco), contador compartilhado de
 * tentativas, destruição/limpeza, persistência para recuperação em crash/reload e
 * as consultas de fronteira reutilizáveis por outros itens especiais
 * ({@link #getCageAt}, {@link #getCageByBlock}, {@link #isInsideCage},
 * {@link #wouldCrossCageBoundary}, {@link #canTeleportBetween}).
 *
 * <p>Tudo roda na main thread (eventos), então mapas simples bastam; usamos
 * {@link ConcurrentHashMap} apenas por robustez.
 */
public class CageManager {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    /** Aresta externa do cubo (8x8x8). */
    private static final int SIZE = 8;
    /** Distribuição fixa e previsível ao redor do bloco clicado: 3 para -, 4 para +. */
    private static final int NEG = 3;
    private static final int POS = 4;
    private static final int HITS_TO_BREAK = 5;
    /**
     * Duração MÁXIMA da estrutura, INDEPENDENTE do cooldown do item: 3 minutos.
     * (O cooldown de 5 min para reutilizar o item fica no {@code AbilityCooldownManager.Ability.JAULA}.
     * A estrutura some primeiro; o cooldown continua correndo os 5 min mesmo assim.)
     */
    private static final int CAGE_MAX_DURATION_MINUTES = 3;
    private static final long CAGE_MAX_DURATION_TICKS = CAGE_MAX_DURATION_MINUTES * 60L * 20L;
    /** Dedupe de tentativas de quebra (anti botão-pressionado / eventos duplicados). */
    private static final long ATTEMPT_DEBOUNCE_MS = 250L;
    private static final long OUTSIDE_MSG_COOLDOWN_MS = 1500L;

    private final PSDK plugin;
    private final BlockData glassData = Material.RED_STAINED_GLASS.createBlockData();

    private final Map<UUID, Cage> cages = new ConcurrentHashMap<>();
    // world -> (packedBlock -> cageId): só blocos da casca. Consulta O(1) por bloco.
    private final Map<String, Map<Long, UUID>> blockIndex = new ConcurrentHashMap<>();
    // world -> (packedChunk -> cageIds): candidatos por chunk para consultas por local.
    private final Map<String, Map<Long, Set<UUID>>> chunkIndex = new ConcurrentHashMap<>();
    // jogador -> jaula em que está preso (mantido em criação/move/quit/death/destroy).
    private final Map<UUID, UUID> playerCage = new ConcurrentHashMap<>();

    private final Map<UUID, Long> attemptDebounce = new HashMap<>();
    private final Map<UUID, Long> outsideMsgCooldown = new HashMap<>();
    // Timer de duração máxima por Jaula (cada instância tem o seu, independente).
    private final Map<UUID, org.bukkit.scheduler.BukkitTask> durationTasks = new HashMap<>();

    public CageManager(PSDK plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────────────── Empacotamento ────────────────────────────
    // Mesmo esquema do ArenaManager: 26 bits p/ x e z, 12 bits p/ y.
    private static long packBlock(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF))
                | ((long) (z & 0x3FFFFFF) << 26)
                | ((long) (y & 0xFFF) << 52);
    }

    private static long packChunk(int cx, int cz) {
        return ((long) cx & 0xFFFFFFFFL) | ((long) cz << 32);
    }

    // ─────────────────────────────── Criação ──────────────────────────────────

    /**
     * Tenta criar uma Jaula ao redor do bloco clicado. Não causa nenhum efeito
     * colateral se inválido (não coloca vidro, não consome item).
     *
     * @return {@code null} em caso de sucesso (Jaula criada e presos avisados);
     *         caso contrário uma mensagem MiniMessage explicando o motivo da falha.
     */
    public String tryCreateCage(Player player, Block clicked) {
        World world = clicked.getWorld();
        ArenaManager arena = plugin.getArenaManager();
        RegionManager rm = plugin.getRegionManager();

        if (arena.getCachedWorld() == null || !world.equals(arena.getCachedWorld())) {
            return "<#e22c27>Você só pode usar a Jaula na arena de PvP!";
        }

        final int cx = clicked.getX();
        final int cy = clicked.getY();
        final int cz = clicked.getZ();

        final int minX = cx - NEG, maxX = cx + POS;
        final int minZ = cz - NEG, maxZ = cz + POS;
        final int minY = cy,        maxY = cy + (SIZE - 1);

        if (minY < world.getMinHeight() || maxY >= world.getMaxHeight()) {
            return "<#e22c27>Não há espaço vertical para a Jaula aqui!";
        }

        // 1) Volume COMPLETO precisa estar dentro da arena de PvP ativa.
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (!arena.isInsideArena(x, y, z)
                            || !rm.isAllowed(new Location(world, x, y, z), RegionFlag.PVP)) {
                        return "<#e22c27>A Jaula precisa caber inteira na arena de PvP!";
                    }
                }
            }
        }

        // 2) Sem sobreposição com outra Jaula ativa.
        for (Cage other : cages.values()) {
            if (other.isActive()
                    && other.intersectsBox(world.getName(), minX, minY, minZ, maxX, maxY, maxZ)) {
                return "<#e22c27>A Jaula não pode sobrepor outra Jaula!";
            }
        }

        // 3) Coleta a casca e valida que cada bloco pode ser substituído com segurança.
        List<int[]> shell = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    boolean isShell = (x == minX || x == maxX
                            || y == minY || y == maxY
                            || z == minZ || z == maxZ);
                    if (!isShell) continue;
                    Block b = world.getBlockAt(x, y, z);
                    if (!isSafeToReplace(b)) {
                        return "<#e22c27>A Jaula não pode ser criada sobre blocos especiais!";
                    }
                    shell.add(new int[]{x, y, z});
                }
            }
        }

        // 4) Resolve jogadores dentro do cubo: quem cair numa posição de casca é
        //    movido para o interior antes da construção (evita sufocamento).
        Set<Player> trapped = new HashSet<>();
        Map<Player, Location> nudges = new HashMap<>();
        for (Player p : world.getPlayers()) {
            Location pl = p.getLocation();
            int px = pl.getBlockX(), py = pl.getBlockY(), pz = pl.getBlockZ();
            boolean inFootprint = px >= minX && px <= maxX
                    && pz >= minZ && pz <= maxZ
                    && py >= minY && py <= maxY;
            if (!inFootprint) continue;

            boolean feetShell = isShellPlane(px, py, pz, minX, minY, minZ, maxX, maxY, maxZ);
            boolean headShell = isShellPlane(px, py + 1, pz, minX, minY, minZ, maxX, maxY, maxZ);
            if (feetShell || headShell) {
                Location safe = findSafeInterior(world, minX, minY, minZ, maxX, maxY, maxZ, px, pz, pl);
                if (safe == null) {
                    return "<#e22c27>Há jogadores em posição insegura para a Jaula!";
                }
                nudges.put(p, safe);
            }
            trapped.add(p);
        }

        // 5) Commit: move os jogadores em risco e ergue a casca (com rollback).
        for (Map.Entry<Player, Location> e : nudges.entrySet()) {
            e.getKey().teleport(e.getValue());
        }

        UUID id = UUID.randomUUID();
        List<Cage.TempBlock> temps = new ArrayList<>(shell.size());
        try {
            for (int[] pos : shell) {
                Block b = world.getBlockAt(pos[0], pos[1], pos[2]);
                String orig = b.getBlockData().getAsString();
                temps.add(new Cage.TempBlock(pos[0], pos[1], pos[2], orig));
                b.setBlockData(glassData, false); // sem física
            }
        } catch (Throwable t) {
            // Rollback imediato de tudo que já foi alterado.
            for (Cage.TempBlock tb : temps) {
                try {
                    world.getBlockAt(tb.x(), tb.y(), tb.z())
                            .setBlockData(Bukkit.createBlockData(tb.originalData()), false);
                } catch (Throwable ignored) { }
            }
            plugin.getLogger().log(Level.WARNING, "Falha ao erguer Jaula, rollback executado", t);
            return "<#e22c27>Falha ao criar a Jaula!";
        }

        Cage cage = new Cage(id, player.getUniqueId(), world.getName(),
                minX, minY, minZ, maxX, maxY, maxZ, System.currentTimeMillis(), temps);
        register(cage);
        persist(cage);

        for (Player p : trapped) {
            playerCage.put(p.getUniqueId(), id);
        }
        announceTrapped(cage);
        creationEffect(cage);
        scheduleDuration(cage);
        return null;
    }

    /**
     * Agenda a remoção automática desta Jaula após {@link #CAGE_MAX_DURATION_MINUTES} min.
     * A tarefa é vinculada exclusivamente a esta instância; é cancelada em {@link #destroy}
     * caso a Jaula seja destruída antes (5 tentativas / limpeza), evitando dupla remoção.
     */
    private void scheduleDuration(Cage cage) {
        org.bukkit.scheduler.BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            durationTasks.remove(cage.getId());
            Cage current = cages.get(cage.getId());
            if (current != null && current.isActive()) {
                destroy(current, true); // tempo esgotado → destrói e libera os presos
            }
        }, CAGE_MAX_DURATION_TICKS);
        durationTasks.put(cage.getId(), task);
    }

    /** Um bloco só é substituível se não guardar dados que a restauração possa perder. */
    private boolean isSafeToReplace(Block b) {
        Material m = b.getType();
        if (m.isAir()) return true;
        switch (m) {
            case NETHER_PORTAL:
            case END_PORTAL:
            case END_GATEWAY:
            case END_PORTAL_FRAME:
                return false;
            default:
                break;
        }
        BlockState state = b.getState();
        // Baús/funis/etc. (perderiam conteúdo) e tile-entities com NBT (placas, spawners...).
        if (state instanceof InventoryHolder) return false;
        if (state instanceof TileState) return false;
        return true;
    }

    private boolean isShellPlane(int x, int y, int z,
                                 int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean inCube = x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        if (!inCube) return false;
        return x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
    }

    /** Acha uma coluna interior segura (2 blocos de ar) mantendo x/z próximos do jogador. */
    private Location findSafeInterior(World world,
                                      int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                      int px, int pz, Location keepAngles) {
        int ix = Math.min(Math.max(px, minX + 1), maxX - 1);
        int iz = Math.min(Math.max(pz, minZ + 1), maxZ - 1);
        for (int iy = minY + 1; iy <= maxY - 1; iy++) {
            Block feet = world.getBlockAt(ix, iy, iz);
            Block head = world.getBlockAt(ix, iy + 1, iz);
            if (isPassable(feet) && (iy + 1 > maxY - 1 || isPassable(head))) {
                Location loc = new Location(world, ix + 0.5, iy, iz + 0.5);
                loc.setYaw(keepAngles.getYaw());
                loc.setPitch(keepAngles.getPitch());
                return loc;
            }
        }
        return null;
    }

    private boolean isPassable(Block b) {
        return b.isPassable() || b.getType().isAir();
    }

    // ─────────────────────────────── Índices ──────────────────────────────────

    private void register(Cage cage) {
        cages.put(cage.getId(), cage);

        Map<Long, UUID> bi = blockIndex.computeIfAbsent(cage.getWorld(), k -> new HashMap<>());
        for (Cage.TempBlock tb : cage.getTempBlocks()) {
            bi.put(packBlock(tb.x(), tb.y(), tb.z()), cage.getId());
        }

        Map<Long, Set<UUID>> ci = chunkIndex.computeIfAbsent(cage.getWorld(), k -> new HashMap<>());
        for (int cxk = cage.getMinX() >> 4; cxk <= cage.getMaxX() >> 4; cxk++) {
            for (int czk = cage.getMinZ() >> 4; czk <= cage.getMaxZ() >> 4; czk++) {
                ci.computeIfAbsent(packChunk(cxk, czk), k -> new HashSet<>()).add(cage.getId());
            }
        }
    }

    private void unregister(Cage cage) {
        Map<Long, UUID> bi = blockIndex.get(cage.getWorld());
        if (bi != null) {
            for (Cage.TempBlock tb : cage.getTempBlocks()) {
                bi.remove(packBlock(tb.x(), tb.y(), tb.z()));
            }
            if (bi.isEmpty()) blockIndex.remove(cage.getWorld());
        }
        Map<Long, Set<UUID>> ci = chunkIndex.get(cage.getWorld());
        if (ci != null) {
            for (int cxk = cage.getMinX() >> 4; cxk <= cage.getMaxX() >> 4; cxk++) {
                for (int czk = cage.getMinZ() >> 4; czk <= cage.getMaxZ() >> 4; czk++) {
                    long key = packChunk(cxk, czk);
                    Set<UUID> set = ci.get(key);
                    if (set != null) {
                        set.remove(cage.getId());
                        if (set.isEmpty()) ci.remove(key);
                    }
                }
            }
            if (ci.isEmpty()) chunkIndex.remove(cage.getWorld());
        }
    }

    // ───────────────────────────── Consultas ──────────────────────────────────

    /** Jaula cujo bloco de casca é este {@code block} (O(1)); {@code null} se nenhum. */
    public Cage getCageByBlock(Block block) {
        Map<Long, UUID> bi = blockIndex.get(block.getWorld().getName());
        if (bi == null || bi.isEmpty()) return null;
        UUID id = bi.get(packBlock(block.getX(), block.getY(), block.getZ()));
        return id == null ? null : cages.get(id);
    }

    /** Jaula cujo INTERIOR contém a localização; {@code null} se nenhuma. */
    public Cage getCageAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        Map<Long, Set<UUID>> ci = chunkIndex.get(loc.getWorld().getName());
        if (ci == null || ci.isEmpty()) return null;
        Set<UUID> ids = ci.get(packChunk(loc.getBlockX() >> 4, loc.getBlockZ() >> 4));
        if (ids == null || ids.isEmpty()) return null;
        for (UUID id : ids) {
            Cage c = cages.get(id);
            if (c != null && c.isActive() && c.isInsideInterior(loc)) return c;
        }
        return null;
    }

    public boolean isInsideCage(Location loc) {
        return getCageAt(loc) != null;
    }

    /**
     * True se um deslocamento de {@code from} para {@code to} atravessaria a fronteira
     * de alguma Jaula (dentro→fora, fora→dentro ou entre jaulas diferentes).
     * Mudança de mundo é considerada fluxo administrativo e nunca é bloqueada.
     */
    public boolean wouldCrossCageBoundary(Location from, Location to) {
        if (from == null || to == null) return false;
        if (from.getWorld() == null || to.getWorld() == null) return false;
        if (!from.getWorld().equals(to.getWorld())) return false;
        Cage a = getCageAt(from);
        Cage b = getCageAt(to);
        return a != b;
    }

    /** Inverso de {@link #wouldCrossCageBoundary} — para outros itens especiais consultarem. */
    public boolean canTeleportBetween(Location from, Location to) {
        return !wouldCrossCageBoundary(from, to);
    }

    public Cage getCage(UUID id) {
        return id == null ? null : cages.get(id);
    }

    public UUID getTrackedCage(Player player) {
        return playerCage.get(player.getUniqueId());
    }

    public void untrack(Player player) {
        playerCage.remove(player.getUniqueId());
    }

    // ─────────────────────── Sistema de tentativas ────────────────────────────

    /**
     * Registra uma tentativa de quebra vinda de {@code p} contra a Jaula {@code cage}.
     * Só conta se o jogador estiver fisicamente dentro daquela mesma Jaula. Trata
     * dedupe (botão pressionado / eventos duplicados), progresso e destruição em 5/5.
     */
    public void tryRegisterAttempt(Cage cage, Player p) {
        if (cage == null || !cage.isActive()) return;

        Cage at = getCageAt(p.getLocation());
        if (at != cage) {
            // Jogador de fora tentando quebrar: sem contagem, mensagem com throttle.
            long now = System.currentTimeMillis();
            Long last = outsideMsgCooldown.get(p.getUniqueId());
            if (last == null || now - last > OUTSIDE_MSG_COOLDOWN_MS) {
                outsideMsgCooldown.put(p.getUniqueId(), now);
                p.sendActionBar(mm.deserialize("<#e22c27>Você não pode quebrar a Jaula por fora!"));
            }
            return;
        }

        long now = System.currentTimeMillis();
        Long last = attemptDebounce.get(p.getUniqueId());
        if (last != null && now - last < ATTEMPT_DEBOUNCE_MS) return;
        attemptDebounce.put(p.getUniqueId(), now);

        cage.addHit();
        World world = Bukkit.getWorld(cage.getWorld());
        if (world != null) {
            world.playSound(p.getLocation(), Sound.BLOCK_GLASS_HIT, 1f, 1.2f);
        }
        showProgress(cage);

        if (cage.getHits() >= HITS_TO_BREAK) {
            destroy(cage, true);
        }
    }

    private void showProgress(Cage cage) {
        World world = Bukkit.getWorld(cage.getWorld());
        if (world == null) return;
        String bar = "<#e22c27><bold>Jaula: <#fcc850>" + cage.getHits() + "/" + HITS_TO_BREAK;
        for (Player p : world.getPlayers()) {
            if (getCageAt(p.getLocation()) == cage) {
                p.sendActionBar(mm.deserialize(bar));
            }
        }
    }

    private void announceTrapped(Cage cage) {
        World world = Bukkit.getWorld(cage.getWorld());
        if (world == null) return;
        for (Player p : world.getPlayers()) {
            if (getCageAt(p.getLocation()) == cage) {
                p.sendMessage(mm.deserialize(
                        "<#e22c27><bold>Jaula ▸ <reset><#cbd1d7>Você está preso em uma Jaula! "
                                + "<#a4a4a4>Tente quebrar os vidros <#fcc850>" + HITS_TO_BREAK
                                + " <#a4a4a4>vezes <#cbd1d7>ou aguarde <#fcc850>" + CAGE_MAX_DURATION_MINUTES
                                + " minutos <#a4a4a4>para escapar."));
                p.playSound(p.getLocation(), Sound.BLOCK_GLASS_PLACE, 1f, 0.8f);
            }
        }
    }

    private void creationEffect(Cage cage) {
        World world = Bukkit.getWorld(cage.getWorld());
        if (world == null) return;
        Location center = new Location(world, cage.centerX(), cage.centerY(), cage.centerZ());
        world.playSound(center, Sound.BLOCK_GLASS_PLACE, 1f, 0.6f);
    }

    // ─────────────────────────────── Destruição ───────────────────────────────

    /**
     * Destrói a Jaula: restaura os blocos originais, limpa índices/dados, libera os
     * presos (com anti-sufocamento) e reproduz efeitos discretos.
     *
     * @param natural {@code true} quando destruída pelas 5 tentativas; {@code false}
     *                em limpeza automática (sem jogadores) / shutdown.
     */
    public void destroy(Cage cage, boolean natural) {
        if (cage == null) return;
        if (cages.remove(cage.getId()) == null) return; // já removida
        cage.setActive(false);

        // Cancela o timer de duração desta Jaula (se as tentativas a destruíram antes,
        // isso impede uma segunda remoção pela tarefa automática).
        org.bukkit.scheduler.BukkitTask task = durationTasks.remove(cage.getId());
        if (task != null) task.cancel();

        World world = Bukkit.getWorld(cage.getWorld());
        if (world != null) {
            for (Cage.TempBlock tb : cage.getTempBlocks()) {
                Block b = world.getBlockAt(tb.x(), tb.y(), tb.z());
                // Só restaura se ainda for o nosso vidro — não sobrescreve mudanças alheias.
                if (b.getType() == Material.RED_STAINED_GLASS) {
                    try {
                        b.setBlockData(Bukkit.createBlockData(tb.originalData()), false);
                    } catch (Throwable ignored) { }
                }
            }
        }

        unregister(cage);
        deletePersist(cage);

        // Libera os presos e evita sufocamento pelos blocos naturais que voltaram.
        List<UUID> freed = new ArrayList<>();
        for (Map.Entry<UUID, UUID> e : playerCage.entrySet()) {
            if (e.getValue().equals(cage.getId())) freed.add(e.getKey());
        }
        for (UUID uid : freed) {
            playerCage.remove(uid);
            attemptDebounce.remove(uid);
            Player p = Bukkit.getPlayer(uid);
            if (p == null || !p.isOnline()) continue;
            ensureNotSuffocating(p, cage);
            if (natural) {
                p.sendMessage(mm.deserialize("<#10fc46><bold>Jaula ▸ <reset><#cbd1d7>A Jaula foi destruída! Você está livre."));
            }
        }

        destructionEffect(cage, world);
    }

    private void ensureNotSuffocating(Player p, Cage cage) {
        Block feet = p.getLocation().getBlock();
        Block head = feet.getRelative(0, 1, 0);
        if (!feet.getType().isSolid() && !head.getType().isSolid()) return;

        World world = p.getWorld();
        // Procura uma coluna segura próxima ao centro, ainda dentro do antigo interior.
        Location safe = findSafeInterior(world,
                cage.getMinX(), cage.getMinY(), cage.getMinZ(),
                cage.getMaxX(), cage.getMaxY(), cage.getMaxZ(),
                p.getLocation().getBlockX(), p.getLocation().getBlockZ(), p.getLocation());
        if (safe != null) p.teleport(safe);
    }

    private void destructionEffect(Cage cage, World world) {
        if (world == null) return;
        Location center = new Location(world, cage.centerX(), cage.centerY(), cage.centerZ());
        world.playSound(center, Sound.BLOCK_GLASS_BREAK, 1f, 1f);
        world.spawnParticle(Particle.BLOCK, center, 40, 2.5, 2.5, 2.5, glassData);
    }

    // ─────────────────── Saída de jogadores / limpeza automática ───────────────

    /**
     * Chamado quando um jogador some da Jaula (quit/death/mudança de mundo). Se a
     * Jaula ficar sem nenhum jogador dentro, é removida automaticamente.
     */
    public void handlePlayerRemoved(Player player) {
        UUID cageId = playerCage.remove(player.getUniqueId());
        attemptDebounce.remove(player.getUniqueId());
        outsideMsgCooldown.remove(player.getUniqueId());
        if (cageId == null) return;
        Cage cage = cages.get(cageId);
        if (cage == null) return;
        if (!hasAnyPlayerInside(cage)) {
            destroy(cage, false);
        }
    }

    private boolean hasAnyPlayerInside(Cage cage) {
        World world = Bukkit.getWorld(cage.getWorld());
        if (world == null) return false;
        for (Player p : world.getPlayers()) {
            if (getCageAt(p.getLocation()) == cage) return true;
        }
        return false;
    }

    // ─────────────────────────────── Persistência ─────────────────────────────

    private void persist(Cage cage) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO cage_blocks (cage_id, world, x, y, z, block_data) VALUES (?,?,?,?,?,?)")) {
                for (Cage.TempBlock tb : cage.getTempBlocks()) {
                    ps.setString(1, cage.getId().toString());
                    ps.setString(2, cage.getWorld());
                    ps.setInt(3, tb.x());
                    ps.setInt(4, tb.y());
                    ps.setInt(5, tb.z());
                    ps.setString(6, tb.originalData());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (SQLException e) {
            // Best-effort: a Jaula segue funcional em memória (destroy/shutdown restauram).
            plugin.getLogger().log(Level.WARNING, "Não foi possível persistir a Jaula " + cage.getId(), e);
        }
    }

    private void deletePersist(Cage cage) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM cage_blocks WHERE cage_id = ?")) {
                ps.setString(1, cage.getId().toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível limpar a Jaula " + cage.getId() + " do banco", e);
        }
    }

    /**
     * Recupera blocos de Jaulas deixados por um crash (onDisable não rodou): restaura
     * o estado ORIGINAL de cada bloco persistido e limpa a tabela. Chamado no onEnable.
     */
    public void recoverFromDatabase() {
        int restored = 0;
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT world, x, y, z, block_data FROM cage_blocks")) {
                while (rs.next()) {
                    World world = Bukkit.getWorld(rs.getString("world"));
                    if (world == null) continue;
                    Block b = world.getBlockAt(rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                    try {
                        b.setBlockData(Bukkit.createBlockData(rs.getString("block_data")), false);
                        restored++;
                    } catch (Throwable ignored) { }
                }
            }
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("DELETE FROM cage_blocks");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Falha na recuperação de Jaulas do banco", e);
        }
        if (restored > 0) {
            plugin.getLogger().info("[Jaula] Recuperados " + restored + " blocos de Jaulas de uma sessão anterior.");
        }
    }

    /** Restaura e limpa TODAS as Jaulas ativas. Chamado no onDisable (antes do DB fechar). */
    public void shutdown() {
        for (Cage cage : new ArrayList<>(cages.values())) {
            destroy(cage, false);
        }
        cages.clear();
        blockIndex.clear();
        chunkIndex.clear();
        playerCage.clear();
        attemptDebounce.clear();
        outsideMsgCooldown.clear();
        durationTasks.clear();
    }
}
