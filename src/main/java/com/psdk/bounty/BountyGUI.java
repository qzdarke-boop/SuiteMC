package com.psdk.bounty;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** GUI paginada com as cabeças dos jogadores que têm recompensa, do MAIOR valor para o MENOR. */
public class BountyGUI implements InventoryHolder {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    public static final Component TITLE = mm.deserialize("<#a4a4a4>Recompensas por abate");

    /** Slots de conteúdo (28 por página): interior de 4 linhas × 7 colunas (borda de 1 slot). */
    static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    static final int PREV_SLOT = 48;
    static final int NEXT_SLOT = 50;
    static final int PER_PAGE = CONTENT_SLOTS.length;

    private final Inventory inventory;
    private final int page;
    private final int totalPages;

    public BountyGUI(List<BountyManager.Bounty> sorted, int page) {
        this.inventory = Bukkit.createInventory(this, 54, TITLE);
        int tp = Math.max(1, (int) Math.ceil(sorted.size() / (double) PER_PAGE));
        this.totalPages = tp;
        this.page = Math.max(0, Math.min(page, tp - 1));
        build(sorted);
    }

    private void build(List<BountyManager.Bounty> sorted) {
        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = start + i;
            if (idx >= sorted.size()) break;
            BountyManager.Bounty b = sorted.get(idx);
            inventory.setItem(CONTENT_SLOTS[i], buildHead(b));
        }

        if (page > 0)               inventory.setItem(PREV_SLOT, arrow("<#10fc46>Página anterior"));
        if (page < totalPages - 1)  inventory.setItem(NEXT_SLOT, arrow("<#10fc46>Próxima página"));
    }

    private ItemStack buildHead(BountyManager.Bounty b) {
        ItemStack head = BountyHeadFactory.head(b.target(), b.name());
        ItemMeta meta = head.getItemMeta();
        if (meta == null) return head;

        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<#a4a4a4>Dynamic").decoration(TextDecoration.ITALIC, false));
        lore.add(mm.deserialize("<#efa600>Recompensa: <#10fc46>" + BountyManager.fmt(b.amount()))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack arrow(String name) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(mm.deserialize(name).decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    public int getPage() { return page; }
    public int getTotalPages() { return totalPages; }
    public boolean isPrevSlot(int slot) { return slot == PREV_SLOT && page > 0; }
    public boolean isNextSlot(int slot) { return slot == NEXT_SLOT && page < totalPages - 1; }

    @Override public Inventory getInventory() { return inventory; }
}
