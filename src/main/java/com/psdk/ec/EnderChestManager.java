package com.psdk.ec;

import com.psdk.PSDK;
import com.psdk.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * Persistência da Ender Chest customizada (54 slots) — fonte ÚNICA da verdade no banco.
 *
 * <p>Anti-dupe: TODOS os 54 slots ficam no banco. A ender chest vanilla do jogador é
 * mantida SEMPRE vazia (ver {@link EnderChestGUI}), para nunca existir uma segunda
 * cópia dos itens (que era explorável via autosave/troca de servidor/reabertura rápida).
 */
public class EnderChestManager {

    public static final int SLOTS = 54;

    private final PSDK plugin;

    /**
     * Trava de edição por UUID-alvo: garante que só UMA visão (o dono OU um staff) edite
     * a ender chest de um jogador por vez. Sem isso, dono + /ecsee (ou dois staff) abrem
     * cópias em memória e quem fecha por último sobrescreve o outro = dupe/perda.
     */
    private final java.util.concurrent.ConcurrentHashMap<UUID, UUID> editLocks = new java.util.concurrent.ConcurrentHashMap<>();

    public EnderChestManager(PSDK plugin) { this.plugin = plugin; }

    /** Tenta travar a EC do alvo para o visualizador. true = pode editar. Reentrante para o mesmo viewer. */
    public boolean acquireLock(UUID target, UUID viewer) {
        UUID prev = editLocks.putIfAbsent(target, viewer);
        return prev == null || prev.equals(viewer);
    }

    /** Quem detém a trava (ou null). */
    public UUID lockHolder(UUID target) { return editLocks.get(target); }

    /** Libera a trava se for do viewer. */
    public void releaseLock(UUID target, UUID viewer) {
        editLocks.remove(target, viewer);
    }

    /** Carrega os 54 slots do jogador a partir do banco. */
    public ItemStack[] load(UUID uuid) {
        ItemStack[] items = new ItemStack[SLOTS];
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT slot, item_data FROM player_ec_extended WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int slot = rs.getInt("slot");
                    if (slot >= 0 && slot < SLOTS)
                        items[slot] = ItemSerializer.fromBase64(rs.getString("item_data"));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[EC] Erro ao carregar: " + e.getMessage());
        }
        return items;
    }

    /**
     * Salva os 54 slots de forma ATÔMICA (uma transação): apaga os antigos e insere os
     * novos no mesmo commit. Se algo falhar, faz rollback (nunca deixa estado parcial).
     */
    public boolean save(UUID uuid, ItemStack[] contents) {
        // Tenta até 3x: SQLite pode dar "database is locked" sob concorrência; engolir o
        // erro silenciosamente faria a alteração do jogador sumir (perda de item).
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try (Connection c = plugin.getDatabaseManager().getConnection()) {
                boolean prevAuto = c.getAutoCommit();
                c.setAutoCommit(false);
                try {
                    try (PreparedStatement del = c.prepareStatement(
                            "DELETE FROM player_ec_extended WHERE player_uuid = ?")) {
                        del.setString(1, uuid.toString());
                        del.executeUpdate();
                    }
                    try (PreparedStatement ins = c.prepareStatement(
                            "INSERT OR REPLACE INTO player_ec_extended (player_uuid, slot, item_data) VALUES (?,?,?)")) {
                        int n = Math.min(SLOTS, contents.length);
                        for (int i = 0; i < n; i++) {
                            ItemStack it = contents[i];
                            if (it == null || it.getType().isAir()) continue;
                            String data = ItemSerializer.toBase64(it);
                            if (data == null) continue;
                            ins.setString(1, uuid.toString());
                            ins.setInt(2, i);
                            ins.setString(3, data);
                            ins.addBatch();
                        }
                        ins.executeBatch();
                    }
                    c.commit();
                    return true;
                } catch (Exception ex) {
                    c.rollback();
                    throw ex;
                } finally {
                    c.setAutoCommit(prevAuto);
                }
            } catch (Exception e) {
                last = e;
                try { Thread.sleep(40L * attempt); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
        }
        plugin.getLogger().severe("[EC] FALHA ao salvar a ender chest de " + uuid
                + " após 3 tentativas: " + (last != null ? last.getMessage() : "?") + " — alteração NÃO persistida.");
        return false;
    }
}
