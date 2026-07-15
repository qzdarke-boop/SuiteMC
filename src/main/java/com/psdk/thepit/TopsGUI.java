package com.psdk.thepit;

import com.psdk.PSDK;
import com.psdk.thepit.topboard.TopBoardFormat;
import com.psdk.thepit.topboard.TopBoardType;
import com.psdk.thepit.topboard.TopEntry;
import com.psdk.thepit.topboard.TopPeriod;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
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
 * Menu de categorias do /tops. Lê SOMENTE o cache do TopQueryService
 * (nenhuma query na main thread); o preview mostra o top 3 global.
 * Clicar numa categoria abre {@link TopsDetailGUI} com ciclo de período.
 */
public final class TopsGUI implements InventoryHolder {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int PREVIEW_LIMIT = 3;
    private static final int INV_SIZE = 36;

    public static final int[] CATEGORY_SLOTS = {10, 11, 12, 13, 14, 15, 16, 22};
    public static final int CLOSE_SLOT = 31;

    /** Categorias exibidas, na ordem dos slots. */
    public static final TopBoardType[] CATEGORIES = {
            TopBoardType.KILLS,
            TopBoardType.DEATHS,
            TopBoardType.LEVEL,
            TopBoardType.COINS,
            TopBoardType.TOKENS,
            TopBoardType.BLOCKS_BROKEN,
            TopBoardType.BLOCKS_PLACED,
            TopBoardType.HOURS
    };

    private final Inventory inventory;

    private TopsGUI() {
        this.inventory = Bukkit.createInventory(this, INV_SIZE, MM.deserialize("<gray>Top Jogadores"));
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /** Categoria correspondente ao slot clicado, ou null. */
    public static TopBoardType categoryAt(int slot) {
        for (int i = 0; i < CATEGORY_SLOTS.length; i++) {
            if (CATEGORY_SLOTS[i] == slot) return CATEGORIES[i];
        }
        return null;
    }

    public static void open(@NotNull Player player) {
        TopsGUI holder = new TopsGUI();

        for (int i = 0; i < CATEGORIES.length; i++) {
            holder.inventory.setItem(CATEGORY_SLOTS[i], buildCategoryItem(CATEGORIES[i]));
        }
        holder.inventory.setItem(CLOSE_SLOT, closeButton());

        player.openInventory(holder.inventory);
    }

    private static @NotNull ItemStack buildCategoryItem(@NotNull TopBoardType type) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(MM.deserialize("<!italic>" + type.getColor() + "<bold>" + displayName(type)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<!italic><#848c94>Destaques da categoria"));
        lore.add(MM.deserialize("<!italic><#848c94><#cbd1d7>" + type.getUnit() + "<#848c94> (global)."));
        lore.add(Component.empty());

        List<TopEntry> entries = PSDK.getInstance().getTopQueryService()
                .getPage(type, TopPeriod.GLOBAL, 0);
        for (int i = 1; i <= PREVIEW_LIMIT; i++) {
            if (i <= entries.size()) {
                TopEntry entry = entries.get(i - 1);
                lore.add(TopBoardFormat.buildRankLine(i, entry, TopBoardFormat.formatValue(type, entry.value())));
            } else {
                lore.add(TopBoardFormat.buildEmptyLine(i));
            }
        }

        lore.add(Component.empty());
        lore.add(MM.deserialize("<!italic>" + type.getColor() + "ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴠᴇʀ ᴏ ᴛᴏᴘ 10"));

        meta.lore(lore);
        item.setItemMeta(meta);
        applyItemModel(item, itemModel(type));
        return item;
    }

    static String displayName(TopBoardType type) {
        return switch (type) {
            case KILLS -> "Kills";
            case DEATHS -> "Deaths";
            case LEVEL -> "Level";
            case COINS -> "Coins";
            case TOKENS -> "Tokens";
            case BLOCKS_BROKEN -> "Blocos Quebrados";
            case BLOCKS_PLACED -> "Blocos Colocados";
            case HOURS -> "Horas";
        };
    }

    static String itemModel(TopBoardType type) {
        return switch (type) {
            case KILLS -> "diamond_sword";
            case DEATHS -> "wither_skeleton_skull";
            case LEVEL -> "experience_bottle";
            case COINS -> "gold_ingot";
            case TOKENS -> "sunflower";
            case BLOCKS_BROKEN -> "diamond_pickaxe";
            case BLOCKS_PLACED -> "bricks";
            case HOURS -> "clock";
        };
    }

    static void applyItemModel(@NotNull ItemStack item, @NotNull String itemModel) {
        try {
            item.setData(DataComponentTypes.ITEM_MODEL, Key.key(itemModel));
        } catch (IllegalArgumentException ignored) {
            PSDK.getInstance().getLogger().warning("Item model invalido no /tops: " + itemModel);
        }
    }

    static @NotNull ItemStack closeButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MM.deserialize("<!italic><red>Fechar"));
            item.setItemMeta(meta);
        }
        return item;
    }
}
