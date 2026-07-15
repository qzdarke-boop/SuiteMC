package com.psdk.lootchest;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Instância em runtime de um baú de loot no mapa.
 *
 * É o {@link InventoryHolder} do menu compartilhado (todos os jogadores pegam
 * itens do mesmo inventário — estilo airdrop). Guarda também as entidades de
 * display/holograma/interação e as tasks de giro e contagem.
 */
public class LootChest implements InventoryHolder {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int MENU_SIZE = 27;

    /** Slots de conteúdo: duas fileiras centrais (sem bordas), order centro→fora para distribuição simétrica. */
    private static final int[] LOOT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25
    };

    private final LootRarity rarity;
    private final Location loc;
    private final Inventory inventory;

    private UUID blockDisplayUuid;
    private UUID hologramUuid;
    private UUID infoUuid;
    private UUID interactionUuid;
    private UUID collisionUuid;

    /** Entidade do meteoro enquanto cai (null depois que pousa). */
    private UUID meteorUuid;
    /** Momento em que o baú foi reservado — usado pelo watchdog da queda. */
    private final long spawnedAtMs = System.currentTimeMillis();
    /** True após o impacto/settle (baú já no chão). */
    private boolean landed = false;

    private BukkitTask spinTask;
    private BukkitTask countdownTask;
    private float spinAngle = 0f;
    private boolean leaving = false;
    /** Momento em que começou a voar embora — usado pelo watchdog de saída. */
    private long leavingAtMs = 0L;

    public LootChest(LootRarity rarity, Location loc) {
        this.rarity = rarity;
        this.loc = loc;
        this.inventory = Bukkit.createInventory(this, MENU_SIZE,
                MM.deserialize(rarity.getColor() + rarity.getDisplayName()));
        fillLoot();
    }

    private void fillLoot() {
        List<ItemStack> loot = LootTable.generate(rarity);
        List<Integer> disponivel = new java.util.ArrayList<>();
        for (int s : LOOT_SLOTS) disponivel.add(s);
        Collections.shuffle(disponivel);
        for (int i = 0; i < loot.size() && i < disponivel.size(); i++) {
            inventory.setItem(disponivel.get(i), loot.get(i));
        }
    }

    public LootRarity getRarity() { return rarity; }
    public Location getLoc() { return loc; }

    public UUID getBlockDisplayUuid() { return blockDisplayUuid; }
    public void setBlockDisplayUuid(UUID u) { this.blockDisplayUuid = u; }

    public UUID getHologramUuid() { return hologramUuid; }
    public void setHologramUuid(UUID u) { this.hologramUuid = u; }

    public UUID getInfoUuid() { return infoUuid; }
    public void setInfoUuid(UUID u) { this.infoUuid = u; }

    public UUID getInteractionUuid() { return interactionUuid; }
    public void setInteractionUuid(UUID u) { this.interactionUuid = u; }

    public UUID getCollisionUuid() { return collisionUuid; }
    public void setCollisionUuid(UUID u) { this.collisionUuid = u; }

    public UUID getMeteorUuid() { return meteorUuid; }
    public void setMeteorUuid(UUID u) { this.meteorUuid = u; }

    public long getSpawnedAtMs() { return spawnedAtMs; }

    public boolean isLanded() { return landed; }
    public void setLanded(boolean landed) { this.landed = landed; }

    public BukkitTask getSpinTask() { return spinTask; }
    public void setSpinTask(BukkitTask t) { this.spinTask = t; }

    public BukkitTask getCountdownTask() { return countdownTask; }
    public void setCountdownTask(BukkitTask t) { this.countdownTask = t; }

    public float getSpinAngle() { return spinAngle; }
    public void setSpinAngle(float a) { this.spinAngle = a; }

    public boolean isLeaving() { return leaving; }
    public void setLeaving(boolean leaving) {
        this.leaving = leaving;
        if (leaving) this.leavingAtMs = System.currentTimeMillis();
    }
    public long getLeavingAtMs() { return leavingAtMs; }

    @Override
    public Inventory getInventory() { return inventory; }
}
