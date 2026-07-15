package com.psdk.shop;

import com.psdk.PSDK;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ShopGUI implements InventoryHolder {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    public static final Component TITLE = mm.deserialize("<#e22c27><bold>LOJA");

    private static final int[] CAT_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final String[] CAT_DISPLAY = {
            "<!italic><#e22c27><bold>Armas",
            "<!italic><#e22c27><bold>Armaduras",
            "<!italic><#e22c27><bold>Blocos",
            "<!italic><#e22c27><bold>Poções",
            "<!italic><#e22c27><bold>Comida",
            "<!italic><#e22c27><bold>Utilitários",
            "<!italic><#e22c27><bold>Misto"
    };

    private Inventory inventory;

    private ShopGUI() {}

    @Override
    public Inventory getInventory() { return inventory; }

    // ─────────────────────────────────────────────────────────────────────────
    //  Tela principal
    // ─────────────────────────────────────────────────────────────────────────

    public static Inventory buildMain(ShopManager sm) {
        ShopGUI holder = new ShopGUI();
        Inventory inv = Bukkit.createInventory(holder, 36, TITLE);
        holder.inventory = inv;

        List<String> cats = sm.getCategories();
        for (int i = 0; i < CAT_SLOTS.length && i < cats.size(); i++) {
            String cat = cats.get(i);
            ItemStack icon = new ItemStack(sm.getIcon(cat));
            // Aplica poção no ícone se a categoria usa poção (ex: pocoes → velocidade)
            ShopManager.applyPotion(icon, sm.getIconPotion(cat));
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(mm.deserialize(CAT_DISPLAY[i]));
                meta.lore(List.of(mm.deserialize("<!italic><#a4a4a4>Clique para ver")));
                icon.setItemMeta(meta);
            }
            inv.setItem(CAT_SLOTS[i], icon);
        }

        // Categoria destacada: Encantamentos (livros p/ PvP), logo acima do Fechar.
        ItemStack enchants = new ItemStack(sm.getIcon("encantamentos"));
        ItemMeta enchantsMeta = enchants.getItemMeta();
        if (enchantsMeta != null) {
            enchantsMeta.displayName(mm.deserialize("<!italic><#e22c27><bold>Encantamentos"));
            enchantsMeta.lore(List.of(mm.deserialize("<!italic><#a4a4a4>Clique para ver")));
            enchants.setItemMeta(enchantsMeta);
        }
        inv.setItem(22, enchants);

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        if (closeMeta != null) {
            closeMeta.displayName(mm.deserialize("<!italic><#e22c27>Fechar"));
            close.setItemMeta(closeMeta);
        }
        inv.setItem(31, close);

        return inv;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Sub-menu de tipos (Armas / Armaduras / Utilitários)
    // ─────────────────────────────────────────────────────────────────────────

    public static Inventory buildSubMenu(ShopManager sm, String parent) {
        ShopGUI holder = new ShopGUI();
        Inventory inv = Bukkit.createInventory(holder, 36, TITLE);
        holder.inventory = inv;

        switch (parent) {
            case "armas" -> {
                inv.setItem(10, subButton(Material.DIAMOND_SWORD,   "<!italic><#55FFFF><bold>Armas de Diamante"));
                inv.setItem(13, subButton(Material.NETHERITE_SWORD, "<!italic><#8B8B8B><bold>Armas de Netherita"));
                inv.setItem(16, subButton(Material.BOW,             "<!italic><#fcc850><bold>Arcos & Outros"));
            }
            case "armas_diamante" -> {
                inv.setItem(11, subButton(Material.DIAMOND_SWORD, "<!italic><#55FFFF><bold>Espadas de Diamante"));
                inv.setItem(15, subButton(Material.DIAMOND_AXE,   "<!italic><#55FFFF><bold>Machados de Diamante"));
            }
            case "armas_netherite" -> {
                inv.setItem(11, subButton(Material.NETHERITE_SWORD, "<!italic><#8B8B8B><bold>Espadas de Netherita"));
                inv.setItem(15, subButton(Material.NETHERITE_AXE,   "<!italic><#8B8B8B><bold>Machados de Netherita"));
            }
            case "armaduras" -> {
                inv.setItem(11, subButton(Material.DIAMOND_CHESTPLATE,   "<!italic><#55FFFF><bold>Armaduras de Diamante"));
                inv.setItem(15, subButton(Material.NETHERITE_CHESTPLATE, "<!italic><#8B8B8B><bold>Armaduras de Netherita"));
            }
            case "utilitarios" -> {
                inv.setItem(13, subButton(Material.DIAMOND_PICKAXE, "<!italic><#55FFFF><bold>Picaretas"));
            }
            case "misto" -> {
                // Seletor do Misto (mesmo padrão do seletor de Armas): duas opções
                // centralizadas — Variados (FIRE_CHARGE) e Itens Especiais (NETHER_STAR).
                inv.setItem(ShopManager.MISTO_VARIADOS_SLOT,
                        subButton(Material.FIRE_CHARGE, "<!italic><#e22c27><bold>Variados"));
                inv.setItem(ShopManager.MISTO_ESPECIAIS_SLOT,
                        subButton(Material.NETHER_STAR, "<!italic><#e22c27><bold>Itens Especiais"));
            }
            case "utilitarios_picaretas" -> {
                inv.setItem(11, subButton(Material.DIAMOND_PICKAXE,   "<!italic><#55FFFF><bold>Picaretas de Diamante"));
                inv.setItem(15, subButton(Material.NETHERITE_PICKAXE, "<!italic><#8B8B8B><bold>Picaretas de Netherita"));
            }
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.displayName(mm.deserialize("<!italic><#fcc850>Voltar"));
            back.setItemMeta(backMeta);
        }
        inv.setItem(31, back);

        return inv;
    }

    private static ItemStack subButton(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(mm.deserialize(name));
            meta.lore(List.of(mm.deserialize("<!italic><#a4a4a4>Clique para ver")));
            item.setItemMeta(meta);
        }
        return item;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Misto › Itens Especiais
    //
    //  Menu 4x9 (36 slots) com os três itens especiais reais (Troca de Posição,
    //  Cadeia, Jaula) centralizados na 2ª linha (slots 11/13/15, um vazio entre eles).
    //  Cada produto é a versão PERMANENTE produzida pela factory PSDKItems, então
    //  mantém aparência/lore/PDC/habilidade — só acrescentamos preço + instrução de
    //  compra na lore de exibição. São apenas botões: o clique nunca retira o item.
    // ─────────────────────────────────────────────────────────────────────────

    public static Inventory buildSpecial(ShopManager sm) {
        ShopGUI holder = new ShopGUI();
        Inventory inv = Bukkit.createInventory(holder, 36, TITLE);
        holder.inventory = inv;

        for (ShopManager.SpecialEntry entry : sm.getSpecialItems()) {
            inv.setItem(entry.slot(), specialDisplay(entry));
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.displayName(mm.deserialize("<!italic><#fcc850>Voltar"));
            back.setItemMeta(backMeta);
        }
        inv.setItem(31, back);

        return inv;
    }

    /**
     * Item de EXIBIÇÃO de um produto especial: parte do item real da factory
     * (aparência/lore/habilidade preservadas) e só acrescenta as linhas do shop
     * (preço + "Clique para comprar"). O item ENTREGUE na compra é uma instância
     * nova e limpa da factory, sem essas linhas.
     */
    private static ItemStack specialDisplay(ShopManager.SpecialEntry entry) {
        ItemStack display = com.psdk.pitems.PSDKItems.create(entry.type());
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Component.empty());
            lore.add(mm.deserialize("<!italic><#e22c27>\uD83D\uDCB2 " + String.format("%.0f", entry.price()) + " coins"));
            lore.add(mm.deserialize("<!italic><#a4a4a4>Clique para comprar"));
            meta.lore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Tela de categoria
    //
    //  Layout padrão (28 itens por página, slots contínuos):
    //  slots: 10-16, 19-25, 28-34, 37-43
    //
    //  Layout enfileirado por coluna (armaduras e picaretas):
    //  Nessas categorias os itens são gerados em ordem intercalada por coluna,
    //  então o layout padrão já posiciona cada peça na sua coluna corretamente.
    //  Ex: armaduras_diamante[0]=capacete_var0 → slot10, [1]=peitoral_var0 → slot11, etc.
    // ─────────────────────────────────────────────────────────────────────────

    // Layout das armaduras: cada COLUNA é um SET inteiro lido de cima pra baixo
    // (capacete, peitoral, calça, bota). As colunas ao lado são as outras variações,
    // do pior (esquerda) ao melhor (direita).
    public static final int ARMOR_COLS_PER_PAGE = 7;
    // Slot mais à esquerda de cada LINHA de peça: capacete, peitoral, calça, bota.
    private static final int[] ARMOR_PIECE_ROW_BASE = {10, 19, 28, 37};

    /** Categorias de armadura usam o layout em colunas (1 coluna = 1 set). */
    public static boolean isColumnArmor(String category) {
        return category != null && category.startsWith("armaduras_");
    }

    /** Última página da categoria (0-based). */
    public static int maxPage(String category, int totalEntries) {
        if (totalEntries <= 0) return 0;
        if (isColumnArmor(category)) {
            int varPerPiece = totalEntries / ShopManager.ARMOR_PIECES;
            return Math.max(0, (varPerPiece - 1) / ARMOR_COLS_PER_PAGE);
        }
        return Math.max(0, (totalEntries - 1) / 28);
    }

    public static Inventory buildCategory(ShopManager sm, String category, int page) {
        ShopGUI holder = new ShopGUI();
        Inventory inv = Bukkit.createInventory(holder, 54, TITLE);
        holder.inventory = inv;

        List<ShopManager.ShopEntry> entries = sm.getItems(category);
        boolean hasNext;

        if (isColumnArmor(category)) {
            int varPerPiece = entries.size() / ShopManager.ARMOR_PIECES;
            int base = page * ARMOR_COLS_PER_PAGE; // 1ª variação (coluna) desta página
            for (int c = 0; c < ARMOR_COLS_PER_PAGE; c++) {
                int varIdx = base + c;
                if (varIdx >= varPerPiece) continue;
                // Empilha o set inteiro na coluna c: capacete, peitoral, calça, bota.
                for (int pieceIdx = 0; pieceIdx < ShopManager.ARMOR_PIECES; pieceIdx++) {
                    int entryIdx = pieceIdx * varPerPiece + varIdx;
                    if (entryIdx >= entries.size()) continue;
                    int slot = ARMOR_PIECE_ROW_BASE[pieceIdx] + c;
                    inv.setItem(slot, createDisplay(entries.get(entryIdx)));
                }
            }
            hasNext = (page + 1) * ARMOR_COLS_PER_PAGE < varPerPiece;
        } else {
            int perPage = 28;
            int start   = page * perPage;
            int end     = Math.min(start + perPage, entries.size());
            int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
            };
            for (int i = start; i < end; i++) {
                inv.setItem(slots[i - start], createDisplay(entries.get(i)));
            }
            hasNext = end < entries.size();
        }

        // Botão Voltar
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.displayName(mm.deserialize("<!italic><#fcc850>Voltar"));
            back.setItemMeta(backMeta);
        }
        inv.setItem(49, back);

        // Página anterior
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            if (prevMeta != null) {
                prevMeta.displayName(mm.deserialize("<!italic><#fcc850>Página anterior"));
                prev.setItemMeta(prevMeta);
            }
            inv.setItem(48, prev);
        }

        // Próxima página
        if (hasNext) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            if (nextMeta != null) {
                nextMeta.displayName(mm.deserialize("<!italic><#fcc850>Próxima página"));
                next.setItemMeta(nextMeta);
            }
            inv.setItem(50, next);
        }

        return inv;
    }

    /** Cria o item de exibição (com encantamentos e preço no lore) de uma oferta. */
    private static ItemStack createDisplay(ShopManager.ShopEntry entry) {
        ItemStack display = new ItemStack(entry.material());
        ShopManager.applyPotion(display, entry.potion());
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>();
            if (!entry.enchantments().isEmpty()) {
                for (Map.Entry<Enchantment, Integer> ench : entry.enchantments().entrySet()) {
                    lore.add(mm.deserialize("<!italic><#a4a4a4>" + formatEnchantName(ench.getKey()) + " " + toRoman(ench.getValue())));
                }
                lore.add(Component.empty());
            }
            lore.add(mm.deserialize("<!italic><#e22c27>\uD83D\uDCB2 " + String.format("%.0f", entry.price()) + " coins"));
            meta.lore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Tela de quantidade
    // ─────────────────────────────────────────────────────────────────────────

    public static Inventory buildQuantity(ShopManager sm, String category, int index, Player player) {
        ShopGUI holder = new ShopGUI();
        Inventory inv = Bukkit.createInventory(holder, 36, TITLE);
        holder.inventory = inv;

        ShopManager.ShopEntry entry = sm.getEntry(category, index);
        if (entry == null) return inv;

        double playerCoins = PSDK.getInstance().getEconomyManager().getCoins(player.getUniqueId());

        ItemStack preview = new ItemStack(entry.material());
        ShopManager.applyPotion(preview, entry.potion());
        ItemMeta previewMeta = preview.getItemMeta();
        if (previewMeta != null) {
            List<Component> lore = new ArrayList<>();
            if (!entry.enchantments().isEmpty()) {
                for (Map.Entry<Enchantment, Integer> ench : entry.enchantments().entrySet()) {
                    lore.add(mm.deserialize("<!italic><#a4a4a4>" + formatEnchantName(ench.getKey()) + " " + toRoman(ench.getValue())));
                }
                lore.add(Component.empty());
            }
            lore.add(mm.deserialize("<!italic><#a4a4a4>Preço unitário: <#fcc850>" + String.format("%.0f", entry.price()) + " coins"));
            previewMeta.lore(lore);
            preview.setItemMeta(previewMeta);
        }
        inv.setItem(11, preview);

        int[] qtdSlots = {12, 13, 14, 15};
        int[] qtds     = {1, 5, 10, 32};
        for (int i = 0; i < qtdSlots.length; i++) {
            double total     = qtds[i] * entry.price();
            boolean canAfford = playerCoins >= total;
            Material glassMat = canAfford ? Material.GREEN_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
            ItemStack btn  = new ItemStack(glassMat);
            ItemMeta btnMeta = btn.getItemMeta();
            if (btnMeta != null) {
                if (canAfford) {
                    btnMeta.displayName(mm.deserialize("<!italic><#10fc46><bold>" + qtds[i] + "x <#a4a4a4>- <#fcc850>" + String.format("%.0f", total) + " coins"));
                } else {
                    btnMeta.displayName(mm.deserialize("<!italic><#e22c27><bold>" + qtds[i] + "x <#a4a4a4>- <#fcc850>" + String.format("%.0f", total) + " coins"));
                    btnMeta.lore(List.of(mm.deserialize("<!italic><#e22c27>Saldo insuficiente!")));
                }
                btn.setItemMeta(btnMeta);
            }
            inv.setItem(qtdSlots[i], btn);
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.displayName(mm.deserialize("<!italic><#fcc850>Voltar"));
            back.setItemMeta(backMeta);
        }
        inv.setItem(27, back);

        return inv;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static String formatEnchantName(Enchantment ench) {
        String key = ench.getKey().getKey();
        return switch (key) {
            case "sharpness"              -> "Afiação";
            case "protection"             -> "Proteção";
            case "projectile_protection"  -> "Proteção contra Projéteis";
            case "blast_protection"       -> "Proteção contra Explosões";
            case "fire_protection"        -> "Proteção contra Fogo";
            case "feather_falling"        -> "Queda Suave";
            case "thorns"                 -> "Espinhos";
            case "efficiency"             -> "Eficiência";
            case "power"                  -> "Força";
            case "punch"                  -> "Impacto";
            case "flame"                  -> "Chama";
            case "infinity"               -> "Infinidade";
            case "quick_charge"           -> "Recarga Rápida";
            case "piercing"               -> "Perfuração";
            case "unbreaking"             -> "Inquebrável";
            case "mending"                -> "Consertar";
            case "fire_aspect"            -> "Aspecto Flamejante";
            case "knockback"              -> "Repulsão";
            case "fortune"                -> "Fortuna";
            case "silk_touch"             -> "Toque de Seda";
            case "luck_of_the_sea"        -> "Sorte do Mar";
            case "lure"                   -> "Chamariz";
            default                       -> key.replace("_", " ");
        };
    }

    private static String toRoman(int num) {
        return switch (num) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(num);
        };
    }
}