package com.psdk.thepit;

import com.psdk.PSDK;
import com.psdk.thepit.topboard.TopBoardFormat;
import com.psdk.thepit.topboard.TopBoardType;
import com.psdk.thepit.topboard.TopEntry;
import com.psdk.thepit.topboard.TopPeriod;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Detalhe de uma categoria do /tops: top 10 do período atual, com ciclo
 * Semanal → Mensal → Global no clique (igual ao topboard físico).
 */
public final class TopsDetailGUI implements InventoryHolder {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int INV_SIZE = 27;
    private static final int TOP_LIMIT = 10;

    public static final int PERIOD_SLOT = 13;
    public static final int BACK_SLOT = 22;

    private final Inventory inventory;
    private final TopBoardType category;
    private final TopPeriod period;

    private TopsDetailGUI(TopBoardType category, TopPeriod period) {
        this.category = category;
        this.period = period;
        this.inventory = Bukkit.createInventory(this, INV_SIZE,
                MM.deserialize("<gray>Top " + TopsGUI.displayName(category) + " — " + period.getDisplay()));
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public TopBoardType getCategory() { return category; }
    public TopPeriod getPeriod() { return period; }

    public static void open(@NotNull Player player, @NotNull TopBoardType category, @NotNull TopPeriod period) {
        if (!category.supportsPeriods()) period = TopPeriod.GLOBAL;
        TopsDetailGUI holder = new TopsDetailGUI(category, period);

        holder.inventory.setItem(PERIOD_SLOT, holder.buildRankingItem());
        holder.inventory.setItem(BACK_SLOT, backButton());

        player.openInventory(holder.inventory);
    }

    /** Próximo período no ciclo (categorias sem períodos ficam em Global). */
    public TopPeriod nextPeriod() {
        if (!category.supportsPeriods()) return TopPeriod.GLOBAL;
        return period.next();
    }

    private @NotNull ItemStack buildRankingItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(MM.deserialize("<!italic>" + category.getColor() + "<bold>"
                + TopsGUI.displayName(category) + "</bold> <#848c94>— " + period.getDisplay()));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        List<Component> lore = new ArrayList<>();
        List<TopEntry> entries = PSDK.getInstance().getTopQueryService().getPage(category, period, 0);
        for (int i = 1; i <= TOP_LIMIT; i++) {
            if (i <= entries.size() && entries.get(i - 1).value() > 0) {
                TopEntry entry = entries.get(i - 1);
                lore.add(TopBoardFormat.buildRankLine(i, entry,
                        TopBoardFormat.formatValue(category, entry.value())));
            } else {
                lore.add(TopBoardFormat.buildEmptyLine(i));
            }
        }
        lore.add(Component.empty());
        if (category.supportsPeriods()) {
            lore.add(MM.deserialize("<!italic>" + category.getColor()
                    + "ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴀʟᴛᴇʀɴᴀʀ ᴏ ᴘᴇʀíᴏᴅᴏ"));
            lore.add(MM.deserialize("<!italic><#848c94>Semanal → Mensal → Global"));
        } else {
            lore.add(MM.deserialize("<!italic><#848c94>Apenas ranking global."));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        TopsGUI.applyItemModel(item, TopsGUI.itemModel(category));
        return item;
    }

    private static @NotNull ItemStack backButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MM.deserialize("<!italic><#b1fcb6>Voltar"));
            meta.lore(List.of(MM.deserialize("<!italic><#848c94>Voltar às categorias")));
            item.setItemMeta(meta);
        }
        return item;
    }
}
