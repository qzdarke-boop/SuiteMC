package com.psdk.shop;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.*;

public class ShopManager {

    /**
     * Multiplicador GLOBAL de preço da loja — único lugar pra deixar tudo mais
     * caro/barato de uma vez. Aplicado a TODAS as ofertas no construtor do
     * {@link ShopEntry}. Ex.: 2.5 = 2,5× mais caro que os valores-base abaixo.
     */
    public static final double PRICE_MULTIPLIER = 2.0;

    /** Uma oferta da loja. {@code potion} != null aplica um efeito de poção real ao item. */
    public record ShopEntry(Material material, double price, Map<Enchantment, Integer> enchantments, PotionType potion) {
        public ShopEntry {
            // Aplica o multiplicador global e arredonda para múltiplos de 5.
            price = Math.max(5, Math.round((price * PRICE_MULTIPLIER) / 5.0) * 5.0);
        }
        public ShopEntry(Material material, double price) {
            this(material, price, Map.of(), null);
        }
        public ShopEntry(Material material, double price, Map<Enchantment, Integer> enchantments) {
            this(material, price, enchantments, null);
        }
        public ShopEntry(Material material, double price, PotionType potion) {
            this(material, price, Map.of(), potion);
        }
    }

    /** Aplica o tipo de poção (cor + nome localizado automático) ao item, se houver. */
    public static void applyPotion(ItemStack item, PotionType type) {
        if (type == null || item == null) return;
        if (item.getItemMeta() instanceof PotionMeta pm) {
            pm.setBasePotionType(type);
            item.setItemMeta(pm);
        }
    }

    private final Map<String, List<ShopEntry>> items = new LinkedHashMap<>();

    public static final List<String> CATEGORIES = List.of(
            "armas", "armaduras", "blocos", "pocoes", "comida", "utilitarios", "misto");

    // ── MISTO: seletor Variados / Itens Especiais ───────────────────────────
    // Slots dos dois botões do menu intermediário do Misto (centralizados aqui,
    // não espalhados pelos listeners), no estilo do seletor de Armas.
    public static final int MISTO_VARIADOS_SLOT  = 11;
    public static final int MISTO_ESPECIAIS_SLOT = 15;

    /**
     * Um produto especial da loja (Itens Especiais do Misto). Entrega a versão
     * PERMANENTE do item real, produzido pela factory {@link com.psdk.pitems.PSDKItems}
     * (ID interno/PDC/brilho/lore/habilidade corretos). O preço é FINAL — não passa
     * pelo {@link #PRICE_MULTIPLIER} — para bater exatamente com o valor pedido.
     */
    public record SpecialEntry(com.psdk.pitems.PSDKItems.ItemType type, double price, int slot) {}

    // Três itens centralizados na 2ª linha (slots 11/13/15), com um slot vazio entre eles.
    // Preços centralizados aqui (Troca de Posição = 600, conforme pedido).
    private final List<SpecialEntry> specialItems = List.of(
            new SpecialEntry(com.psdk.pitems.PSDKItems.ItemType.TROCA_POSICAO, 600,  11),
            new SpecialEntry(com.psdk.pitems.PSDKItems.ItemType.TRAP,          600,  13),
            new SpecialEntry(com.psdk.pitems.PSDKItems.ItemType.JAULA,         8000, 15)
    );

    public ShopManager() {
        // ── ARMADURAS ──────────────────────────────────────────────────────────
        // Layout: cada COLUNA = uma peça de armadura (capacete col0, peitoral col1, calça col2, bota col3)
        // Linha = nível de encantamento (Proteção 1-4, Inquebrável 1-3)
        // buildColumnArmor() produz entradas na ordem: col0_row0, col1_row0, col2_row0, col3_row0, col0_row1, ...
        items.put("armaduras_diamante", buildColumnArmor(
                Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
                Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS, 1.0));
        items.put("armaduras_netherite", buildColumnArmor(
                Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
                Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS, 1.8));

        // ── ARMAS ──────────────────────────────────────────────────────────────
        // Sub-menu por material (diamante/netherita) e depois por tipo (espada/machado).
        // O material é herdado da seleção anterior: espada e machado do mesmo metal.
        items.put("armas_diamante_espada",   buildSwordWeapons(Material.DIAMOND_SWORD,    1.0));
        items.put("armas_diamante_machado",  buildAxeWeapons(Material.DIAMOND_AXE,        1.0));
        items.put("armas_netherite_espada",  buildSwordWeapons(Material.NETHERITE_SWORD,  1.8));
        items.put("armas_netherite_machado", buildAxeWeapons(Material.NETHERITE_AXE,      1.8));

        // Arco, besta, tridente
        items.put("armas_outros", List.of(
                new ShopEntry(Material.BOW,      150, ench(Enchantment.POWER, 1, Enchantment.UNBREAKING, 1)),
                new ShopEntry(Material.BOW,      220, ench(Enchantment.POWER, 2, Enchantment.UNBREAKING, 1)),
                new ShopEntry(Material.BOW,      300, ench(Enchantment.POWER, 3, Enchantment.UNBREAKING, 2)),
                new ShopEntry(Material.BOW,      400, ench(Enchantment.POWER, 4, Enchantment.UNBREAKING, 2)),
                new ShopEntry(Material.BOW,      550, ench(Enchantment.POWER, 5, Enchantment.UNBREAKING, 3)),
                new ShopEntry(Material.CROSSBOW, 200, ench(Enchantment.QUICK_CHARGE, 1, Enchantment.UNBREAKING, 1)),
                new ShopEntry(Material.CROSSBOW, 300, ench(Enchantment.QUICK_CHARGE, 2, Enchantment.UNBREAKING, 2)),
                new ShopEntry(Material.CROSSBOW, 450, ench(Enchantment.QUICK_CHARGE, 3, Enchantment.UNBREAKING, 3)),
                new ShopEntry(Material.TRIDENT,  600),
                new ShopEntry(Material.ARROW,      5),
                new ShopEntry(Material.SPECTRAL_ARROW, 8)
        ));

        // ── BLOCOS ─────────────────────────────────────────────────────────────
        items.put("blocos", List.of(
                new ShopEntry(Material.OAK_LOG,       5),
                new ShopEntry(Material.SPRUCE_LOG,    5),
                new ShopEntry(Material.BIRCH_LOG,     5),
                new ShopEntry(Material.DARK_OAK_LOG,  5),
                new ShopEntry(Material.JUNGLE_LOG,    5),
                new ShopEntry(Material.ACACIA_LOG,    5),
                new ShopEntry(Material.CHERRY_LOG,    8),
                new ShopEntry(Material.MANGROVE_LOG,  6),
                new ShopEntry(Material.OAK_PLANKS,    3),
                new ShopEntry(Material.SPRUCE_PLANKS, 3),
                new ShopEntry(Material.COBBLESTONE,   2),
                new ShopEntry(Material.STONE,         4),
                new ShopEntry(Material.STONE_BRICKS,  5),
                new ShopEntry(Material.DEEPSLATE,     6),
                new ShopEntry(Material.COBBLED_DEEPSLATE, 5),
                new ShopEntry(Material.SANDSTONE,     4),
                new ShopEntry(Material.GRAVEL,        3),
                new ShopEntry(Material.DIRT,          2),
                new ShopEntry(Material.GLASS,         5),
                new ShopEntry(Material.TINTED_GLASS, 12),
                new ShopEntry(Material.BRICKS,        8),
                new ShopEntry(Material.QUARTZ_BLOCK, 10),
                new ShopEntry(Material.PRISMARINE,   12),
                new ShopEntry(Material.OBSIDIAN,     50)
        ));

        // ── POÇÕES ─────────────────────────────────────────────────────────────
        items.put("pocoes", List.of(
                // Bebíveis
                new ShopEntry(Material.POTION,  80,  PotionType.HEALING),
                new ShopEntry(Material.POTION, 140,  PotionType.STRONG_HEALING),
                new ShopEntry(Material.POTION, 120,  PotionType.REGENERATION),
                new ShopEntry(Material.POTION, 100,  PotionType.SWIFTNESS),
                new ShopEntry(Material.POTION, 160,  PotionType.STRENGTH),
                new ShopEntry(Material.POTION, 120,  PotionType.FIRE_RESISTANCE),
                new ShopEntry(Material.POTION, 100,  PotionType.LEAPING),
                new ShopEntry(Material.POTION, 140,  PotionType.NIGHT_VISION),
                new ShopEntry(Material.POTION, 140,  PotionType.WATER_BREATHING),
                new ShopEntry(Material.POTION, 120,  PotionType.INVISIBILITY),
                new ShopEntry(Material.POTION, 180,  PotionType.SLOW_FALLING),
                new ShopEntry(Material.POTION, 160,  PotionType.LUCK),
                // Arremessáveis (splash)
                new ShopEntry(Material.SPLASH_POTION, 160,  PotionType.HEALING),
                new ShopEntry(Material.SPLASH_POTION, 200,  PotionType.STRONG_HEALING),
                new ShopEntry(Material.SPLASH_POTION, 220,  PotionType.STRONG_HARMING),
                new ShopEntry(Material.SPLASH_POTION, 140,  PotionType.POISON),
                new ShopEntry(Material.SPLASH_POTION, 180,  PotionType.STRONG_POISON),
                new ShopEntry(Material.SPLASH_POTION, 140,  PotionType.SLOWNESS),
                new ShopEntry(Material.SPLASH_POTION, 170,  PotionType.STRONG_SLOWNESS),
                new ShopEntry(Material.SPLASH_POTION, 120,  PotionType.WEAKNESS),
                new ShopEntry(Material.SPLASH_POTION, 140,  PotionType.REGENERATION),
                new ShopEntry(Material.SPLASH_POTION, 160,  PotionType.SWIFTNESS),
                new ShopEntry(Material.SPLASH_POTION, 180,  PotionType.STRENGTH),
                new ShopEntry(Material.SPLASH_POTION, 150,  PotionType.NIGHT_VISION),
                new ShopEntry(Material.SPLASH_POTION, 130,  PotionType.FIRE_RESISTANCE),
                new ShopEntry(Material.SPLASH_POTION, 200,  PotionType.SLOW_FALLING),
                // Persistentes (lingering)
                new ShopEntry(Material.LINGERING_POTION, 260, PotionType.REGENERATION),
                new ShopEntry(Material.LINGERING_POTION, 240, PotionType.SWIFTNESS),
                new ShopEntry(Material.LINGERING_POTION, 280, PotionType.POISON),
                new ShopEntry(Material.LINGERING_POTION, 260, PotionType.HEALING),
                new ShopEntry(Material.LINGERING_POTION, 300, PotionType.HARMING),
                new ShopEntry(Material.LINGERING_POTION, 220, PotionType.SLOWNESS),
                new ShopEntry(Material.LINGERING_POTION, 240, PotionType.STRENGTH),
                new ShopEntry(Material.LINGERING_POTION, 230, PotionType.WEAKNESS),
                // Garrafa de XP
                new ShopEntry(Material.EXPERIENCE_BOTTLE, 15)   // 30 coins final (×2.0)
        ));

        // ── COMIDA ─────────────────────────────────────────────────────────────
        items.put("comida", List.of(
                new ShopEntry(Material.BREAD,              5),
                new ShopEntry(Material.COOKED_BEEF,       10),
                new ShopEntry(Material.COOKED_PORKCHOP,   10),
                new ShopEntry(Material.COOKED_CHICKEN,     8),
                new ShopEntry(Material.GOLDEN_CARROT,     25),
                new ShopEntry(Material.CAKE,              30),
                new ShopEntry(Material.PUMPKIN_PIE,       12),
                new ShopEntry(Material.COOKIE,             3),
                new ShopEntry(Material.GOLDEN_APPLE,     125)   // 250 coins final (×2.0)
        ));

        // ── UTILITÁRIOS (sub-categorias) ───────────────────────────────────────
        // Picaretas separadas por material
        items.put("utilitarios_picaretas_diamante",  buildPickaxeEntries(Material.DIAMOND_PICKAXE,   1.0));
        items.put("utilitarios_picaretas_netherite", buildPickaxeEntries(Material.NETHERITE_PICKAXE, 1.8));

        // ── MISTO › VARIADOS ────────────────────────────────────────────────────
        // Subcategoria "Variados" do Misto. É EXATAMENTE o antigo menu "misto"
        // (mesmos produtos/preços/slots); só mudou a chave interna e o acesso, que
        // agora passa pelo seletor intermediário Misto → Variados / Itens Especiais.
        items.put("misto_variados", List.of(
                new ShopEntry(Material.ENDER_PEARL,    62.5), // 125 coins final (×2.0)
                new ShopEntry(Material.WIND_CHARGE,      60),
                new ShopEntry(Material.TNT,             150), // explode (dano + kb) mas NÃO quebra blocos
                new ShopEntry(Material.SHULKER_BOX,     350), // 700 coins com o multiplicador global

                new ShopEntry(Material.LADDER,            5),
                new ShopEntry(Material.SHIELD,           85, Map.of(Enchantment.UNBREAKING, 3)),  // só o Unbreaking 3
                new ShopEntry(Material.COBWEB,           20),
                new ShopEntry(Material.ARROW,             3),
                new ShopEntry(Material.SPECTRAL_ARROW,    8),
                new ShopEntry(Material.TOTEM_OF_UNDYING, 400), // 800 coins final (×2.0)
                new ShopEntry(Material.ANVIL,            50),   // 100 coins com o multiplicador global
                new ShopEntry(Material.FISHING_ROD,      40),   // movido da antiga seção "Outros"
                new ShopEntry(Material.SPYGLASS,         30),   // movido da antiga seção "Outros"
                new ShopEntry(Material.POINTED_DRIPSTONE, 30),
                new ShopEntry(Material.SLIME_BLOCK,       50)
        ));

        // ── ENCANTAMENTOS ──────────────────────────────────────────────────────
        // Livros encantados úteis para a arena de PvP. Preços por importância/nível:
        // encantamentos mais fortes ou mais procurados são mais caros. Só níveis
        // que fazem sentido — sem poluir o menu com todos os degraus.
        // Agrupados por uso: espadas, armaduras, arcos, bestas e durabilidade.
        items.put("encantamentos", List.of(
                // Espadas
                new ShopEntry(Material.ENCHANTED_BOOK, 400,  Map.of(Enchantment.SHARPNESS, 4)),
                new ShopEntry(Material.ENCHANTED_BOOK, 700,  Map.of(Enchantment.SHARPNESS, 5)),
                new ShopEntry(Material.ENCHANTED_BOOK, 350,  Map.of(Enchantment.FIRE_ASPECT, 2)),
                new ShopEntry(Material.ENCHANTED_BOOK, 300,  Map.of(Enchantment.KNOCKBACK, 2)),
                // Armaduras
                new ShopEntry(Material.ENCHANTED_BOOK, 600,  Map.of(Enchantment.PROTECTION, 4)),
                new ShopEntry(Material.ENCHANTED_BOOK, 400,  Map.of(Enchantment.PROJECTILE_PROTECTION, 4)),
                new ShopEntry(Material.ENCHANTED_BOOK, 350,  Map.of(Enchantment.BLAST_PROTECTION, 4)),
                new ShopEntry(Material.ENCHANTED_BOOK, 400,  Map.of(Enchantment.FEATHER_FALLING, 4)),
                new ShopEntry(Material.ENCHANTED_BOOK, 350,  Map.of(Enchantment.THORNS, 3)),
                // Arcos
                new ShopEntry(Material.ENCHANTED_BOOK, 350,  Map.of(Enchantment.POWER, 4)),
                new ShopEntry(Material.ENCHANTED_BOOK, 550,  Map.of(Enchantment.POWER, 5)),
                new ShopEntry(Material.ENCHANTED_BOOK, 300,  Map.of(Enchantment.PUNCH, 2)),
                new ShopEntry(Material.ENCHANTED_BOOK, 300,  Map.of(Enchantment.FLAME, 1)),
                new ShopEntry(Material.ENCHANTED_BOOK, 500,  Map.of(Enchantment.INFINITY, 1)),
                // Bestas
                new ShopEntry(Material.ENCHANTED_BOOK, 350,  Map.of(Enchantment.QUICK_CHARGE, 3)),
                new ShopEntry(Material.ENCHANTED_BOOK, 300,  Map.of(Enchantment.PIERCING, 4)),
                // Durabilidade
                new ShopEntry(Material.ENCHANTED_BOOK, 300,   Map.of(Enchantment.UNBREAKING, 3)),
                new ShopEntry(Material.ENCHANTED_BOOK, 15000, Map.of(Enchantment.MENDING, 1)) // 30.000 coins final (×2.0)
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ARMADURA: layout enfileirado por coluna (uma peça por coluna)
    //  Ordem de inserção: slot10=capacete, slot19=capacete_row1, slot28=capacete_row2, slot37=capacete_row3...
    //  Para isso, geramos: [capacete_var0, peitoral_var0, calça_var0, bota_var0,
    //                        capacete_var1, peitoral_var1, calça_var1, bota_var1, ...]
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Gera entradas na ordem de exibição enfileirada:
     *  linha 0: capacete var0, peitoral var0, calça var0, bota var0
     *  linha 1: capacete var1, peitoral var1, calça var1, bota var1
     *  ...
     * Variações: Proteção 1-4 × Inquebrável 1-3 = 12 variações por peça → 12 linhas × 4 colunas
     */
    /** Nº de peças por set de armadura (capacete, peitoral, calça, bota). */
    public static final int ARMOR_PIECES = 4;

    /**
     * Gera as armaduras AGRUPADAS por peça e ordenadas do pior ao melhor:
     *  [capacete_0..capacete_N, peitoral_0..peitoral_N, calça_0..calça_N, bota_0..bota_N]
     * O {@link ShopGUI} posiciona cada peça na sua própria COLUNA, do pior (embaixo)
     * ao melhor (em cima).
     */
    private static List<ShopEntry> buildColumnArmor(Material helmet, Material chest,
                                                     Material legs, Material boots, double mul) {
        List<ShopEntry> hList = pieceMatrix(helmet, Enchantment.PROTECTION, 160, 150, 60, mul);
        List<ShopEntry> cList = pieceMatrix(chest,  Enchantment.PROTECTION, 300, 200, 70, mul);
        List<ShopEntry> lList = pieceMatrix(legs,   Enchantment.PROTECTION, 250, 170, 65, mul);
        List<ShopEntry> bList = pieceMatrix(boots,  Enchantment.PROTECTION, 160, 140, 55, mul);

        // Ordena cada peça do pior (mais barato) ao melhor (mais caro).
        Comparator<ShopEntry> byPrice = Comparator.comparingDouble(ShopEntry::price);
        hList.sort(byPrice);
        cList.sort(byPrice);
        lList.sort(byPrice);
        bList.sort(byPrice);

        List<ShopEntry> out = new ArrayList<>();
        out.addAll(hList);
        out.addAll(cList);
        out.addAll(lList);
        out.addAll(bList);
        return out;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ARMAS: espadas e machados gerados separadamente, um por sub-categoria.
    //  O material (diamante/netherita) vem da seleção anterior no sub-menu, então
    //  espada e machado da mesma sub-categoria compartilham o metal escolhido.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Espadas com todas combinações de encantamentos.
     * Encantamentos: Sharpness 1-5, Fire Aspect 1, Knockback 1-2, Unbreaking 1-3
     */
    private static List<ShopEntry> buildSwordWeapons(Material sword, double mul) {
        List<ShopEntry> out = new ArrayList<>();

        // Sharpness 1-5 × Unbreaking 1-3
        for (int s = 1; s <= 5; s++) {
            for (int u = 1; u <= 3; u++) {
                double price = roundToFive((200 + (s - 1) * 100 + u * 50) * mul);
                out.add(new ShopEntry(sword, price, ench(Enchantment.SHARPNESS, s, Enchantment.UNBREAKING, u)));
            }
        }
        // Sharpness 3-5 + Fire Aspect 1 × Unbreaking 1-3
        for (int s = 3; s <= 5; s++) {
            for (int u = 1; u <= 3; u++) {
                double price = roundToFive((300 + (s - 3) * 120 + u * 60) * mul);
                Map<Enchantment, Integer> e = new LinkedHashMap<>();
                e.put(Enchantment.SHARPNESS, s);
                e.put(Enchantment.FIRE_ASPECT, 1);
                e.put(Enchantment.UNBREAKING, u);
                out.add(new ShopEntry(sword, price, e));
            }
        }
        // Sharpness 3-5 + Knockback 1-2 × Unbreaking 1-2
        for (int s = 3; s <= 5; s++) {
            for (int k = 1; k <= 2; k++) {
                for (int u = 1; u <= 2; u++) {
                    double price = roundToFive((280 + (s - 3) * 110 + k * 40 + u * 40) * mul);
                    Map<Enchantment, Integer> e = new LinkedHashMap<>();
                    e.put(Enchantment.SHARPNESS, s);
                    e.put(Enchantment.KNOCKBACK, k);
                    e.put(Enchantment.UNBREAKING, u);
                    out.add(new ShopEntry(sword, price, e));
                }
            }
        }
        // Combos triplos: Sharpness 4-5 + Fire Aspect 1 + Knockback 1-2 × Unbreaking 2-3
        for (int s = 4; s <= 5; s++) {
            for (int k = 1; k <= 2; k++) {
                for (int u = 2; u <= 3; u++) {
                    double price = roundToFive((500 + (s - 4) * 150 + k * 60 + u * 70) * mul);
                    Map<Enchantment, Integer> e = new LinkedHashMap<>();
                    e.put(Enchantment.SHARPNESS, s);
                    e.put(Enchantment.FIRE_ASPECT, 1);
                    e.put(Enchantment.KNOCKBACK, k);
                    e.put(Enchantment.UNBREAKING, u);
                    out.add(new ShopEntry(sword, price, e));
                }
            }
        }

        return out;
    }

    /**
     * Machados com todas combinações de encantamentos.
     * Encantamentos: Sharpness 1-5, Efficiency 1-5, Unbreaking 1-3
     */
    private static List<ShopEntry> buildAxeWeapons(Material axe, double mul) {
        List<ShopEntry> out = new ArrayList<>();

        // Sharpness 1-5 × Unbreaking 1-3
        for (int s = 1; s <= 5; s++) {
            for (int u = 1; u <= 3; u++) {
                double price = roundToFive((180 + (s - 1) * 90 + u * 45) * mul);
                out.add(new ShopEntry(axe, price, ench(Enchantment.SHARPNESS, s, Enchantment.UNBREAKING, u)));
            }
        }
        // Efficiency 1-5 × Unbreaking 1-3
        for (int ef = 1; ef <= 5; ef++) {
            for (int u = 1; u <= 3; u++) {
                double price = roundToFive((160 + (ef - 1) * 80 + u * 40) * mul);
                out.add(new ShopEntry(axe, price, ench(Enchantment.EFFICIENCY, ef, Enchantment.UNBREAKING, u)));
            }
        }
        // Sharpness 3-5 + Efficiency 3-5 × Unbreaking 2-3
        for (int s = 3; s <= 5; s++) {
            for (int ef = 3; ef <= 5; ef++) {
                for (int u = 2; u <= 3; u++) {
                    double price = roundToFive((400 + (s - 3) * 100 + (ef - 3) * 80 + u * 60) * mul);
                    Map<Enchantment, Integer> e = new LinkedHashMap<>();
                    e.put(Enchantment.SHARPNESS, s);
                    e.put(Enchantment.EFFICIENCY, ef);
                    e.put(Enchantment.UNBREAKING, u);
                    out.add(new ShopEntry(axe, price, e));
                }
            }
        }

        return out;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UTILITÁRIOS: picaretas (Diamante + Netherite intercaladas por variação)
    // ─────────────────────────────────────────────────────────────────────────

    /** Picaretas de um único material com Efficiency, Fortune e Silk Touch. */
    private static List<ShopEntry> buildPickaxeEntries(Material mat, double mul) {
        List<ShopEntry> out = new ArrayList<>();
        // Efficiency 1-5 × Unbreaking 1-3
        for (int ef = 1; ef <= 5; ef++) {
            for (int u = 1; u <= 3; u++) {
                double price = roundToFive((150 + (ef - 1) * 70 + u * 40) * mul);
                out.add(new ShopEntry(mat, price, ench(Enchantment.EFFICIENCY, ef, Enchantment.UNBREAKING, u)));
            }
        }
        // Fortune 1-3 × Efficiency 3-5 × Unbreaking 2-3
        for (int f = 1; f <= 3; f++) {
            for (int ef = 3; ef <= 5; ef++) {
                for (int u = 2; u <= 3; u++) {
                    double price = roundToFive((400 + f * 80 + (ef - 3) * 70 + u * 50) * mul);
                    Map<Enchantment, Integer> e = new LinkedHashMap<>();
                    e.put(Enchantment.EFFICIENCY, ef);
                    e.put(Enchantment.FORTUNE, f);
                    e.put(Enchantment.UNBREAKING, u);
                    out.add(new ShopEntry(mat, price, e));
                }
            }
        }
        // Silk Touch + Efficiency 3-5 × Unbreaking 2-3
        for (int ef = 3; ef <= 5; ef++) {
            for (int u = 2; u <= 3; u++) {
                double price = roundToFive((380 + (ef - 3) * 60 + u * 50) * mul);
                Map<Enchantment, Integer> e = new LinkedHashMap<>();
                e.put(Enchantment.EFFICIENCY, ef);
                e.put(Enchantment.SILK_TOUCH, 1);
                e.put(Enchantment.UNBREAKING, u);
                out.add(new ShopEntry(mat, price, e));
            }
        }
        return out;
    }

    /** Pás de um único material com Efficiency × Unbreaking. */
    private static List<ShopEntry> buildShovelEntries(Material mat, double mul) {
        List<ShopEntry> out = new ArrayList<>();
        for (int ef = 1; ef <= 5; ef++) {
            for (int u = 1; u <= 3; u++) {
                double price = roundToFive((80 + (ef - 1) * 50 + u * 30) * mul);
                out.add(new ShopEntry(mat, price, ench(Enchantment.EFFICIENCY, ef, Enchantment.UNBREAKING, u)));
            }
        }
        return out;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static Map<Enchantment, Integer> ench(Enchantment a, int av, Enchantment b, int bv) {
        Map<Enchantment, Integer> m = new LinkedHashMap<>();
        m.put(a, av);
        m.put(b, bv);
        return m;
    }

    private static double roundToFive(double raw) {
        return Math.round(raw / 5.0) * 5;
    }

    /** Peça × encantamento primário 1–4 × Inquebrável 1–3. */
    private static List<ShopEntry> pieceMatrix(Material mat, Enchantment primary,
                                               double base, double primaryStep, double unbStep, double mul) {
        List<ShopEntry> out = new ArrayList<>();
        for (int p = 1; p <= 4; p++) {
            for (int u = 1; u <= 3; u++) {
                double raw = (base + (p - 1) * primaryStep + u * unbStep) * mul;
                long price = Math.round(raw / 5.0) * 5;
                out.add(new ShopEntry(mat, price, ench(primary, p, Enchantment.UNBREAKING, u)));
            }
        }
        return out;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  API pública
    // ─────────────────────────────────────────────────────────────────────────

    public int getCategorySize(String category) { return 54; }

    public List<ShopEntry> getItems(String category) {
        return items.getOrDefault(category, List.of());
    }

    public ShopEntry getEntry(String category, int index) {
        List<ShopEntry> list = items.get(category);
        if (list == null || index < 0 || index >= list.size()) return null;
        return list.get(index);
    }

    // ── Itens Especiais (Misto → Itens Especiais) ───────────────────────────

    /** Lista imutável dos produtos especiais (Troca de Posição, Cadeia, Jaula). */
    public List<SpecialEntry> getSpecialItems() { return specialItems; }

    /** Produto especial posicionado em {@code slot}, ou null se o slot não tiver produto. */
    public SpecialEntry getSpecialBySlot(int slot) {
        for (SpecialEntry e : specialItems) if (e.slot() == slot) return e;
        return null;
    }

    /** Produto especial pelo índice na lista, ou null se fora do intervalo. */
    public SpecialEntry getSpecial(int index) {
        if (index < 0 || index >= specialItems.size()) return null;
        return specialItems.get(index);
    }

    /** Índice de um produto especial pelo slot do menu, ou -1 se não houver. */
    public int getSpecialIndexBySlot(int slot) {
        for (int i = 0; i < specialItems.size(); i++) {
            if (specialItems.get(i).slot() == slot) return i;
        }
        return -1;
    }

    public Material getIcon(String category) {
        return switch (category) {
            case "armas"                  -> Material.DIAMOND_SWORD;
            case "armas_diamante"         -> Material.DIAMOND_SWORD;
            case "armas_diamante_espada"  -> Material.DIAMOND_SWORD;
            case "armas_diamante_machado" -> Material.DIAMOND_AXE;
            case "armas_netherite"        -> Material.NETHERITE_SWORD;
            case "armas_netherite_espada" -> Material.NETHERITE_SWORD;
            case "armas_netherite_machado"-> Material.NETHERITE_AXE;
            case "armas_outros"           -> Material.BOW;
            case "armaduras"              -> Material.DIAMOND_CHESTPLATE;
            case "armaduras_diamante"     -> Material.DIAMOND_CHESTPLATE;
            case "armaduras_netherite"    -> Material.NETHERITE_CHESTPLATE;
            case "blocos"                 -> Material.OAK_LOG;
            case "pocoes"                 -> Material.POTION; // ícone = poção de velocidade (aplicado em buildMain)
            case "comida"                 -> Material.GOLDEN_CARROT;
            case "utilitarios"                    -> Material.DIAMOND_PICKAXE;
            case "utilitarios_picaretas"          -> Material.DIAMOND_PICKAXE;
            case "utilitarios_picaretas_diamante" -> Material.DIAMOND_PICKAXE;
            case "utilitarios_picaretas_netherite"-> Material.NETHERITE_PICKAXE;
            case "utilitarios_pas"                -> Material.DIAMOND_SHOVEL;
            case "utilitarios_pas_diamante"       -> Material.DIAMOND_SHOVEL;
            case "utilitarios_pas_netherite"      -> Material.NETHERITE_SHOVEL;
            case "utilitarios_outros"             -> Material.SHEARS;
            case "misto"                  -> Material.FIRE_CHARGE;
            case "misto_variados"         -> Material.FIRE_CHARGE;
            case "misto_especiais"        -> Material.NETHER_STAR;
            case "encantamentos"          -> Material.ENCHANTED_BOOK;
            default                       -> Material.STONE;
        };
    }

    /** Tipo de poção do ícone (para poções/velocidade e outros). */
    public PotionType getIconPotion(String category) {
        return switch (category) {
            case "pocoes" -> PotionType.SWIFTNESS;
            default       -> null;
        };
    }

    public List<String> getCategories() { return CATEGORIES; }
}