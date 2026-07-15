package com.psdk.thepit;

import com.psdk.PSDK;
import com.psdk.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class KitLootManager {

    public record LootEntry(int slot, int index, ItemStack item, int amount, int chance) {}

    private final PSDK plugin;
    private final Map<Integer, List<LootEntry>> bySlot = new HashMap<>();

    public KitLootManager(PSDK plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        bySlot.clear();
        try (Statement stmt = plugin.getDatabaseManager().getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM thepit_kit_loot ORDER BY slot, idx")) {
            while (rs.next()) {
                int slot   = rs.getInt("slot");
                int idx    = rs.getInt("idx");
                String b64 = rs.getString("item_data");
                int amount = rs.getInt("amount");
                int chance = rs.getInt("chance");
                ItemStack item = ItemSerializer.fromBase64(b64);
                if (item == null) continue;
                bySlot.computeIfAbsent(slot, k -> new ArrayList<>())
                        .add(new LootEntry(slot, idx, item, amount, chance));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao carregar kit_loot", e);
        }
    }

    public List<LootEntry> getEntries(int slot) {
        return bySlot.getOrDefault(slot, Collections.emptyList());
    }

    public Set<Integer> slotsWithLoot() { return bySlot.keySet(); }

    public void addEntry(int slot, ItemStack item, int amount, int chance) {
        int nextIdx = bySlot.getOrDefault(slot, Collections.emptyList()).size() + 1;
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "INSERT INTO thepit_kit_loot (slot, idx, item_data, amount, chance) VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, slot);
            ps.setInt(2, nextIdx);
            ps.setString(3, ItemSerializer.toBase64(item));
            ps.setInt(4, amount);
            ps.setInt(5, chance);
            ps.executeUpdate();
            bySlot.computeIfAbsent(slot, k -> new ArrayList<>())
                    .add(new LootEntry(slot, nextIdx, item.clone(), amount, chance));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao salvar kit_loot", e);
        }
    }

    public void clearSlot(int slot) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection()
                .prepareStatement("DELETE FROM thepit_kit_loot WHERE slot = ?")) {
            ps.setInt(1, slot);
            ps.executeUpdate();
            bySlot.remove(slot);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "kit_loot", e);
        }
    }

    public void clearAll() {
        try (Statement stmt = plugin.getDatabaseManager().getConnection().createStatement()) {
            stmt.execute("DELETE FROM thepit_kit_loot");
            bySlot.clear();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao limpar todo kit_loot", e);
        }
    }
}
