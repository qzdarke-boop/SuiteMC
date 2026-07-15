package com.psdk.thepit;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class ArenaManager {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private static final int SAVE_BLOCKS_PER_TICK = 8000;
    private static final int DB_BATCH_SIZE = 10000;

    // ── Regen (reset) ────────────────────────────────────────────────────────
    // Orçamento de tempo por tick para aplicar blocos: a main thread processa o
    // máximo que conseguir dentro deste limite e cede o resto do tick ao servidor.
    // Adaptativo => o mais rápido possível sem causar lag, independente do tamanho.
    private static final long RESET_TICK_BUDGET_NANOS = 30_000_000L; // ~30ms/tick
    private static final int RESET_DB_FETCH_SIZE = 20_000;           // cursor de leitura
    // Filas limitadas mantêm a RAM constante (streaming): o produtor bloqueia/pausa
    // quando a fila enche, em vez de carregar o volume inteiro de uma vez.
    private static final int RESET_QUEUE_CAPACITY = 50_000;  // refs de BlockData compartilhadas (palette)
    private static final int SAVE_QUEUE_CAPACITY  = 50_000;  // strings deduplicadas (palette)
    private static final long MAX_ARENA_VOLUME = 50_000_000L; // 50 milhões de blocos máximo
    private static final long WARN_ARENA_VOLUME = 5_000_000L; // Avisa acima de 5 milhões

    // ── Regen automático — tempos e mensagens hand-coded (mude à vontade) ──────
    /** A arena reinicia sozinha a cada X segundos (8 minutos). */
    private static final int AUTO_RESET_INTERVAL_SECONDS = 8 * 60;
    /** Em quais segundos restantes avisar antes de reiniciar. */
    private static final java.util.Set<Integer> AUTO_RESET_WARN_SECONDS = java.util.Set.of(30, 10, 5, 3, 2, 1);

    private final PSDK plugin;
    private ArenaData arenaData;
    private boolean resetting = false;
    private boolean saving = false;
    private int autoResetSecondsLeft = AUTO_RESET_INTERVAL_SECONDS;

    // Blocos colocados por jogadores DENTRO da arena. Só estes podem ser quebrados
    // (além dos minérios). Tudo é limpo no regen, que devolve a arena ao snapshot.
    private final java.util.Set<Long> placedBlocks = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // Cached bounds to avoid repeated Math.min/max calls in hot paths
    private int minX, maxX, minY, maxY, minZ, maxZ;
    private World cachedWorld;

    public ArenaManager(PSDK plugin) {
        this.plugin = plugin;
        this.arenaData = new ArenaData();
    }

    private void refreshBoundsCache() {
        if (!arenaData.hasBothPositions()) return;
        minX = arenaData.getMinX(); maxX = arenaData.getMaxX();
        minY = arenaData.getMinY(); maxY = arenaData.getMaxY();
        minZ = arenaData.getMinZ(); maxZ = arenaData.getMaxZ();
        cachedWorld = arenaData.getPos1().getWorld();
    }

    public void loadArena() {
        arenaData = new ArenaData();
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();

            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM arena_meta WHERE id = 'main'");
                 ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return;
                String worldName = rs.getString("world");
                if (worldName == null || worldName.isEmpty()) return;
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("[ThePit] Mundo da arena nao encontrado: " + worldName);
                    return;
                }
                arenaData.setPos1(new Location(world, rs.getInt("pos1_x"), rs.getInt("pos1_y"), rs.getInt("pos1_z")));
                arenaData.setPos2(new Location(world, rs.getInt("pos2_x"), rs.getInt("pos2_y"), rs.getInt("pos2_z")));
            }
            if (!arenaData.hasBothPositions()) return;

            // Conta registros primeiro para pré-alocar o HashMap
            int blockCount = 0;
            try (Statement countStmt = conn.createStatement();
                 ResultSet countRs = countStmt.executeQuery("SELECT COUNT(*) FROM arena_blocks")) {
                if (countRs.next()) blockCount = countRs.getInt(1);
            }

            if (blockCount > 0) {
                arenaData.setBlocksInDatabase(blockCount);
                arenaData.setDefined(true);
                refreshBoundsCache();
                plugin.getLogger().info("[ThePit] Arena definida com " + String.format("%,d", blockCount)
                        + " blocos no SQLite (sem carregar na RAM).");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[ThePit] Erro ao carregar arena", e);
        }
    }

    public void saveSnapshot(Location pos1, Location pos2, Player requester) {
        if (saving) {
            if (requester != null)
                requester.sendMessage(mm.deserialize("<#FF0000>Já existe um salvamento em progresso."));
            return;
        }
        if (pos1.getWorld() == null) return;

        arenaData.setPos1(pos1);
        arenaData.setPos2(pos2);

        long volume = arenaData.getVolume();

        if (volume > MAX_ARENA_VOLUME) {
            if (requester != null) {
                requester.sendMessage(mm.deserialize("<#FF0000>Arena muito grande!"));
                requester.sendMessage(mm.deserialize(
                        "<#FF0000>Volume: <#fcc850>" + String.format("%,d", volume)
                        + " <#FF0000>blocos (max: <#fcc850>" + String.format("%,d", MAX_ARENA_VOLUME) + "<#FF0000>)"));
                requester.sendMessage(mm.deserialize("<#a4a4a4>Reduza a seleção ou aumente MAX_ARENA_VOLUME no código."));
            }
            return;
        }

        if (volume > WARN_ARENA_VOLUME && requester != null) {
            long estimatedMB = (volume * 100) / (1024 * 1024); // ~100 bytes por bloco
            requester.sendMessage(mm.deserialize("<#fcc850>AVISO: Arena grande!"));
            requester.sendMessage(mm.deserialize("<#a4a4a4>Memoria estimada: <#fcc850>~" + estimatedMB + " MB"));
            requester.sendMessage(mm.deserialize("<#a4a4a4>Isso pode demorar alguns minutos..."));
        }

        saving = true;
        final World world = pos1.getWorld();
        final String worldName = world.getName();
        final int minX = arenaData.getMinX(), maxX = arenaData.getMaxX();
        final int minY = arenaData.getMinY(), maxY = arenaData.getMaxY();
        final int minZ = arenaData.getMinZ(), maxZ = arenaData.getMaxZ();
        final int p1x = arenaData.getPos1().getBlockX(), p1y = arenaData.getPos1().getBlockY(), p1z = arenaData.getPos1().getBlockZ();
        final int p2x = arenaData.getPos2().getBlockX(), p2y = arenaData.getPos2().getBlockY(), p2z = arenaData.getPos2().getBlockZ();

        if (requester != null)
            requester.sendMessage(mm.deserialize("<#fcc850>Salvando arena (" + volume + " blocos)..."));

        // Fila limitada: o scanner (thread principal) produz linhas e o escritor
        // assíncrono as grava no SQLite. A RAM fica limitada à capacidade da fila,
        // em vez de manter o volume inteiro de BlockData em memória.
        final LinkedBlockingQueue<BlockRow> queue = new LinkedBlockingQueue<>(SAVE_QUEUE_CAPACITY);
        final AtomicBoolean scanDone = new AtomicBoolean(false);

        // ---- Escritor assíncrono (dono de uma conexão própria) ----
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int written = 0;
            try (Connection conn = plugin.getDatabaseManager().newConnection()) {
                conn.setAutoCommit(false);
                try {
                    try (PreparedStatement ps = conn.prepareStatement("""
                            INSERT OR REPLACE INTO arena_meta (id, world, pos1_x, pos1_y, pos1_z, pos2_x, pos2_y, pos2_z)
                            VALUES ('main', ?, ?, ?, ?, ?, ?, ?)""")) {
                        ps.setString(1, worldName);
                        ps.setInt(2, p1x); ps.setInt(3, p1y); ps.setInt(4, p1z);
                        ps.setInt(5, p2x); ps.setInt(6, p2y); ps.setInt(7, p2z);
                        ps.executeUpdate();
                    }
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("DELETE FROM arena_blocks");
                    }
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO arena_blocks (x, y, z, block_data) VALUES (?, ?, ?, ?)")) {
                        int batch = 0;
                        while (true) {
                            BlockRow row = queue.poll();
                            if (row == null) {
                                if (scanDone.get() && queue.isEmpty()) break;
                                try { Thread.sleep(5); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); break; }
                                continue;
                            }
                            ins.setInt(1, row.x());
                            ins.setInt(2, row.y());
                            ins.setInt(3, row.z());
                            ins.setString(4, row.data());
                            ins.addBatch();
                            written++;
                            if (++batch % DB_BATCH_SIZE == 0) {
                                ins.executeBatch();
                                conn.commit();
                            }
                        }
                        ins.executeBatch();
                    }
                    conn.commit();
                } catch (SQLException e) {
                    try { conn.rollback(); } catch (SQLException ignored) {}
                    throw e;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "[ThePit] Erro ao salvar arena no SQLite", e);
            }

            final int total = written;
            // Finaliza o estado na thread principal.
            Bukkit.getScheduler().runTask(plugin, () -> {
                arenaData.setBlocksInDatabase(total);
                arenaData.setDefined(true);
                refreshBoundsCache();
                saving = false;
                if (requester != null && requester.isOnline()) {
                    requester.sendMessage(mm.deserialize(
                            "<#10fc46>Arena salva! <#fcc850>" + String.format("%,d", total) + " <#10fc46>blocos no SQLite."));
                }
            });
        });

        // ---- Scanner na thread principal (acesso a blocos exige main thread) ----
        new BukkitRunnable() {
            int cx = minX, cy = minY, cz = minZ;
            // Palette: deduplica as strings de block_data (muita repetição: stone, etc.),
            // então a fila guarda referências compartilhadas em vez de N cópias iguais.
            final java.util.HashMap<String, String> intern = new java.util.HashMap<>();

            @Override
            public void run() {
                int processed = 0;
                while (processed < SAVE_BLOCKS_PER_TICK) {
                    if (cx > maxX) break;

                    Block block = world.getBlockAt(cx, cy, cz);
                    String data = block.getBlockData().getAsString();
                    String canonical = intern.putIfAbsent(data, data);
                    if (canonical != null) data = canonical; // reaproveita a instância já vista
                    BlockRow row = new BlockRow(cx, cy, cz, data);
                    if (!queue.offer(row)) {
                        // Fila cheia: aguarda o escritor esvaziar e tenta o mesmo bloco no próximo tick.
                        return;
                    }
                    processed++;

                    cz++;
                    if (cz > maxZ) {
                        cz = minZ;
                        cy++;
                        if (cy > maxY) {
                            cy = minY;
                            cx++;
                        }
                    }
                }

                if (cx > maxX) {
                    cancel();
                    scanDone.set(true);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Linha compacta de um bloco da arena para o salvamento por streaming. */
    private record BlockRow(int x, int y, int z, String data) {}

    /** Bloco já parseado (x,y,z + BlockData compartilhado da palette) para o regen. */
    private record ParsedBlock(int x, int y, int z, BlockData data) {}

    /**
     * Reseta a arena reconstruindo-a INTEIRA a partir do snapshot persistido no
     * SQLite. Por ler do banco, SEMPRE volta ao último estado salvo e sobrevive a
     * reinícios — o snapshot é recarregado no boot por {@link #loadArena()}, então
     * NÃO é preciso re-salvar a arena após reiniciar o servidor.
     *
     * Otimizado para RAM e velocidade:
     *  • Um único cursor assíncrono lê a tabela (sem OFFSET, que é O(n²)).
     *  • O PARSE do block_data acontece no thread assíncrono e com PALETTE CACHE:
     *    cada estado distinto é parseado uma única vez (arenas têm dezenas de
     *    estados distintos para milhões de blocos), tirando o custo de parse da
     *    main thread; a fila guarda refs de BlockData compartilhadas (RAM mínima).
     *  • A main thread aplica por ORÇAMENTO DE TEMPO (adaptativo): processa o
     *    máximo que couber em ~30ms/tick e cede o resto ao servidor, pulando
     *    blocos que já estão corretos (sem update/relight/pacote). Sem lag.
     *
     * Em estado ocioso NÃO mantém nenhum bloco da arena na RAM — só as filas
     * temporárias e limitadas durante o próprio regen (liberadas ao terminar).
     */
    public void resetArena(Runnable onComplete) {
        if (resetting || !arenaData.isDefined()) return;
        resetting = true;
        World world = arenaData.getPos1().getWorld();
        if (world == null) { resetting = false; return; }

        // Remove entidades "soltas" que os jogadores deixam na arena (barcos,
        // carrinhos, itens no chão, blocos caindo, TNT). É o que mais lagava o
        // servidor: o regen devolve os blocos mas não tira essas entidades.
        removeLooseEntities(world);

        final LinkedBlockingQueue<ParsedBlock> queue = new LinkedBlockingQueue<>(RESET_QUEUE_CAPACITY);
        final AtomicBoolean readDone = new AtomicBoolean(false);

        // Leitor + parser assíncrono: cursor único sobre a tabela inteira.
        // createBlockData() é leitura no registry imutável (sem tocar no mundo),
        // então é seguro fora da main thread; o estado parseado é interno do MC.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final java.util.HashMap<String, BlockData> palette = new java.util.HashMap<>();
            try (Connection conn = plugin.getDatabaseManager().newConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT x, y, z, block_data FROM arena_blocks")) {
                ps.setFetchSize(RESET_DB_FETCH_SIZE);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String data = rs.getString(4);
                        BlockData bd = palette.get(data);
                        if (bd == null) {
                            try {
                                bd = Bukkit.createBlockData(data);
                            } catch (IllegalArgumentException ex) {
                                continue; // block_data inválido/desconhecido -> pula
                            }
                            palette.put(data, bd);
                        }
                        try {
                            queue.put(new ParsedBlock(rs.getInt(1), rs.getInt(2), rs.getInt(3), bd));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "[ThePit] Erro ao ler blocos da arena", e);
            } finally {
                readDone.set(true);
            }
        });

        // Aplicador na main thread (acesso a blocos exige main thread).
        new BukkitRunnable() {
            @Override
            public void run() {
                final long deadline = System.nanoTime() + RESET_TICK_BUDGET_NANOS;
                int sinceTimeCheck = 0;
                ParsedBlock row;
                while ((row = queue.poll()) != null) {
                    Block block = world.getBlockAt(row.x(), row.y(), row.z());
                    if (!block.getBlockData().equals(row.data())) {
                        block.setBlockData(row.data(), false); // sem física
                    }
                    // Checa o relógio só a cada 1024 blocos (nanoTime tem custo).
                    if ((++sinceTimeCheck & 1023) == 0 && System.nanoTime() >= deadline) break;
                }
                if (readDone.get() && queue.isEmpty()) {
                    cancel();
                    resetting = false;
                    // A arena voltou ao snapshot: blocos colocados por jogadores já
                    // foram removidos, então esquecemos o rastreio deles.
                    placedBlocks.clear();
                    if (onComplete != null) onComplete.run();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  REGEN AUTOMÁTICO (a cada X minutos, com aviso) — mensagens hand-coded.
    // ════════════════════════════════════════════════════════════════════════

    /** Inicia o regen automático da arena (a cada {@link #AUTO_RESET_INTERVAL_SECONDS}s). */
    public void startAutoReset() {
        autoResetSecondsLeft = AUTO_RESET_INTERVAL_SECONDS;
        new BukkitRunnable() {
            @Override public void run() {
                if (!arenaData.isDefined()) return; // sem snapshot salvo: não faz nada
                autoResetSecondsLeft--;
                if (autoResetSecondsLeft <= 0) {
                    autoResetSecondsLeft = AUTO_RESET_INTERVAL_SECONDS;
                    announceArena("<#fcc850><bold>A arena foi reiniciada!");
                    resetArena(null);
                } else if (AUTO_RESET_WARN_SECONDS.contains(autoResetSecondsLeft)) {
                    announceArena("<#a4a4a4>A arena reinicia em <#FF0000>" + autoResetSecondsLeft + "<#a4a4a4>s!");
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // a cada 1s
    }

    /** Segundos até o próximo regen automático. */
    public int getAutoResetSecondsLeft() { return autoResetSecondsLeft; }

    /** Envia uma mensagem (MiniMessage) aos jogadores do mundo da arena. */
    private void announceArena(String mini) {
        if (cachedWorld == null) return;
        for (Player p : cachedWorld.getPlayers()) p.sendMessage(mm.deserialize(mini));
    }

    public boolean isInsideArena(Location location) {
        if (!arenaData.isDefined() || cachedWorld == null) return false;
        if (location.getWorld() == null || !location.getWorld().equals(cachedWorld)) return false;
        return isInsideArena(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /** World-agnostic check — caller must ensure the block is in the arena's world. */
    public boolean isInsideArena(int x, int y, int z) {
        return arenaData.isDefined()
                && x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public World getCachedWorld() { return cachedWorld; }

    // ── Rastreio de blocos colocados por jogadores dentro da arena ─────────────
    // Empacota (x,y,z) num único long: 26 bits p/ x e z (±33M) e 12 bits p/ y.
    private static long packKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF))
                | ((long) (z & 0x3FFFFFF) << 26)
                | ((long) (y & 0xFFF) << 52);
    }

    /** Marca um bloco como colocado por jogador (pode ser quebrado depois). */
    public void markPlaced(int x, int y, int z) { placedBlocks.add(packKey(x, y, z)); }

    /** Esquece um bloco colocado (ao ser quebrado). */
    public void unmarkPlaced(int x, int y, int z) { placedBlocks.remove(packKey(x, y, z)); }

    /** True se o bloco foi colocado por um jogador (e não faz parte do snapshot). */
    public boolean isPlayerPlaced(int x, int y, int z) { return placedBlocks.contains(packKey(x, y, z)); }

    /**
     * Remove entidades soltas dentro da arena (barcos, carrinhos, itens no chão,
     * blocos caindo e TNT). Chamado no início do regen — deve rodar na main thread.
     */
    private void removeLooseEntities(World world) {
        if (!arenaData.isDefined()) return;
        for (org.bukkit.entity.Entity e : world.getEntities()) {
            if (!(e instanceof org.bukkit.entity.Vehicle
                    || e instanceof org.bukkit.entity.Item
                    || e instanceof org.bukkit.entity.FallingBlock
                    || e instanceof org.bukkit.entity.TNTPrimed)) continue;
            Location l = e.getLocation();
            if (isInsideArena(l.getBlockX(), l.getBlockY(), l.getBlockZ())) e.remove();
        }
    }

    public ArenaData getArenaData() { return arenaData; }
    public boolean isResetting()    { return resetting; }
    public boolean isSaving()       { return saving; }
}
