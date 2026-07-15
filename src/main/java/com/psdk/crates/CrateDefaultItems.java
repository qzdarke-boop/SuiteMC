package com.psdk.crates;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.ToolComponent;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import com.psdk.PSDK;
import com.psdk.util.NexoUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CrateDefaultItems {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    /**
     * Monta os itens padrão da Caixa Rara (diamante).
     * Retorna lista de 10 posições (MAX_ITENS), com itens nos índices 0-6 (em linha).
     */
    public static List<ItemStack> buildRaraItems() {
        List<ItemStack> items = new ArrayList<>(Collections.nCopies(Crate.MAX_ITENS, null));

        items.set(0, buildArmor(Material.DIAMOND_HELMET, "<#55cdfc>Capacete raro",
                Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3),
                TrimPattern.WARD, TrimMaterial.LAPIS));

        items.set(1, buildArmor(Material.DIAMOND_CHESTPLATE, "<#55cdfc>Peitoral raro",
                Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3),
                TrimPattern.WARD, TrimMaterial.LAPIS));

        items.set(2, buildArmor(Material.DIAMOND_LEGGINGS, "<#55cdfc>Calça rara",
                Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3),
                TrimPattern.WARD, TrimMaterial.LAPIS));

        items.set(3, buildArmor(Material.DIAMOND_BOOTS, "<#55cdfc>Bota rara",
                Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3),
                TrimPattern.WARD, TrimMaterial.LAPIS));

        items.set(4, buildWeapon(Material.DIAMOND_SWORD, "<#55cdfc>Espada rara",
                Map.of(Enchantment.SHARPNESS, 4, Enchantment.UNBREAKING, 3)));

        items.set(5, buildWeapon(Material.DIAMOND_AXE, "<#55cdfc>Machado raro",
                Map.of(Enchantment.SHARPNESS, 4, Enchantment.UNBREAKING, 3)));

        items.set(6, buildWeapon(Material.DIAMOND_PICKAXE, "<#55cdfc>Picareta rara",
                Map.of(Enchantment.EFFICIENCY, 4, Enchantment.UNBREAKING, 3, Enchantment.FORTUNE, 1)));

        return items;
    }

    /**
     * Monta os itens padrão da Caixa Eye: ITEM REAL DO NEXO (textura garantida) com a
     * FUNÇÃO de netherite aplicada por cima via data-components (atributos/ferramenta).
     * Encantamentos máximos + Mending + Unbreaking V.
     * Retorna lista de 10 posições (MAX_ITENS), com itens nos índices 0-6 (em linha).
     */
    public static List<ItemStack> buildEyeItems() {
        List<ItemStack> items = new ArrayList<>(Collections.nCopies(Crate.MAX_ITENS, null));

        items.set(0, buildEyeItem("azgad_helmet", Material.NETHERITE_HELMET, "Capacete Eye",
                Map.of(Enchantment.PROTECTION, 5, Enchantment.UNBREAKING, 5,
                        Enchantment.MENDING, 1)));

        items.set(1, buildEyeItem("azgad_chestplate", Material.NETHERITE_CHESTPLATE, "Peitoral Eye",
                Map.of(Enchantment.PROTECTION, 5, Enchantment.UNBREAKING, 5,
                        Enchantment.MENDING, 1)));

        items.set(2, buildEyeItem("azgad_leggings", Material.NETHERITE_LEGGINGS, "Calça Eye",
                Map.of(Enchantment.PROTECTION, 5, Enchantment.UNBREAKING, 5,
                        Enchantment.MENDING, 1, Enchantment.SWIFT_SNEAK, 3)));

        items.set(3, buildEyeItem("azgad_boots", Material.NETHERITE_BOOTS, "Bota Eye",
                Map.of(Enchantment.PROTECTION, 5, Enchantment.UNBREAKING, 5,
                        Enchantment.MENDING, 1,
                        Enchantment.DEPTH_STRIDER, 3, Enchantment.FEATHER_FALLING, 4)));

        items.set(4, buildEyeItem("azgad_weapon_sword", Material.NETHERITE_SWORD, "Espada Eye",
                Map.of(Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 5,
                        Enchantment.MENDING, 1, Enchantment.SWEEPING_EDGE, 3,
                        Enchantment.FIRE_ASPECT, 2, Enchantment.LOOTING, 3)));

        items.set(5, buildEyeItem("azgad_weapon_axe", Material.NETHERITE_AXE, "Machado Eye",
                Map.of(Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 5,
                        Enchantment.MENDING, 1, Enchantment.EFFICIENCY, 5)));

        items.set(6, buildEyeItem("azgad_weapon_pickaxe", Material.NETHERITE_PICKAXE, "Picareta Eye",
                Map.of(Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 5,
                        Enchantment.MENDING, 1, Enchantment.FORTUNE, 3)));

        return items;
    }

    /**
     * Monta um item da Caixa Eye usando o ITEM REAL DO NEXO como base (textura 100%
     * correta no inventário, na mão e VESTIDO no corpo, pois é literalmente o item do
     * Nexo). Como esses itens usam material base fraco (couro/ferro/papel), aplicamos
     * a FUNÇÃO de netherite por cima via data-components:
     * <ul>
     *   <li><b>Armadura</b> (couro): modificadores de atributo de Armadura + Tenacidade
     *       + Resistência a Empurrão equivalentes (ou melhores) ao netherite.</li>
     *   <li><b>Picareta</b> (papel, não mina nada sozinha): componente {@code tool} com
     *       velocidade alta e "correto para drops" em todos os blocos de picareta.</li>
     *   <li><b>Espada/Machado</b> (ferro): já funcionam; os encantamentos (Sharpness V,
     *       Fire Aspect, Looting...) deixam bem acima da Especial.</li>
     * </ul>
     * Se o Nexo não devolver o item (desligado/id errado), cai no netherite vanilla.
     */
    private static ItemStack buildEyeItem(String nexoId, Material fallbackMat,
                                          String displayName, Map<Enchantment, Integer> enchants) {
        java.util.logging.Logger log = PSDK.getInstance().getLogger();

        ItemStack nexo = NexoUtil.buildItem(nexoId);
        boolean fromNexo = nexo != null;
        ItemStack item = fromNexo ? nexo : new ItemStack(fallbackMat);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(mm.deserialize("<!italic><gradient:#ff0000:#8b0000>" + displayName));
        enchants.forEach((ench, level) -> meta.addEnchant(ench, level, true));
        // Esconde a linha "Color: #..." que o couro tingido do Nexo exibe.
        meta.addItemFlags(ItemFlag.HIDE_DYE);

        // Atributos REMOVIDOS a pedido: limpamos os modificadores próprios do Nexo e
        // escondemos a seção de atributos do tooltip (cobre também os atributos implícitos
        // do netherite, caso o Nexo não devolva o item). Resultado: nenhuma linha de atributo.
        clearAttributeModifiers(meta);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        // Mantém apenas a função de mineração da picareta (não é atributo).
        applyNetheriteFunction(meta, fallbackMat);

        item.setItemMeta(meta);

        if (!fromNexo) {
            log.warning("[CaixaEye] '" + nexoId + "' -> Nexo devolveu null; usando NETHERITE vanilla "
                    + "(sem textura). Verifique se o Nexo está ativo e se o id existe (/nexo iteminfo).");
        } else {
            log.info("[CaixaEye] '" + nexoId + "' base=" + item.getType()
                    + " (item real do Nexo) -> textura garantida + funcao netherite aplicada.");
        }
        return item;
    }

    /**
     * Atributos REMOVIDOS a pedido: os itens da Eye não recebem nenhum modificador de
     * atributo (dano, velocidade de ataque, armadura, tenacidade, etc.). Mantemos apenas
     * a função de mineração da picareta — que NÃO é um atributo — para ela continuar
     * quebrando blocos normalmente.
     */
    private static void applyNetheriteFunction(ItemMeta meta, Material profile) {
        if (profile == Material.NETHERITE_PICKAXE) {
            pickaxeTool(meta);
        }
    }

    /** Remove TODOS os modificadores de atributo já presentes no item (limpa os atributos do Nexo). */
    private static void clearAttributeModifiers(ItemMeta meta) {
        var existing = meta.getAttributeModifiers();
        if (existing != null) {
            for (Attribute a : new java.util.HashSet<>(existing.keySet())) {
                meta.removeAttributeModifier(a);
            }
        }
    }

    /** Faz um item (mesmo de papel) minerar como/melhor que netherite e dropar minérios. */
    private static void pickaxeTool(ItemMeta meta) {
        ToolComponent tool = meta.getTool();
        tool.setDefaultMiningSpeed(12.0f);
        tool.setDamagePerBlock(1);
        Tag<Material> mineable = Bukkit.getTag(Tag.REGISTRY_BLOCKS,
                NamespacedKey.minecraft("mineable/pickaxe"), Material.class);
        if (mineable != null) {
            tool.addRule(mineable, 12.0f, true);
        }
        meta.setTool(tool);
    }

    /**
     * Monta os itens padrão da Caixa Especial (netherite).
     * Retorna lista de 10 posições (MAX_ITENS), com itens nos índices 0-6 (em linha).
     */
    public static List<ItemStack> buildEspecialItems() {
        List<ItemStack> items = new ArrayList<>(Collections.nCopies(Crate.MAX_ITENS, null));

        items.set(0, buildArmor(Material.NETHERITE_HELMET, "<#b06dff>Capacete especial",
                Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3),
                TrimPattern.FLOW, TrimMaterial.AMETHYST));

        items.set(1, buildArmor(Material.NETHERITE_CHESTPLATE, "<#b06dff>Peitoral especial",
                Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3),
                TrimPattern.SNOUT, TrimMaterial.AMETHYST));

        items.set(2, buildArmor(Material.NETHERITE_LEGGINGS, "<#b06dff>Calça especial",
                Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3),
                TrimPattern.SILENCE, TrimMaterial.AMETHYST));

        items.set(3, buildArmor(Material.NETHERITE_BOOTS, "<#b06dff>Bota especial",
                Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3),
                TrimPattern.WAYFINDER, TrimMaterial.AMETHYST));

        items.set(4, buildWeapon(Material.NETHERITE_SWORD, "<#b06dff>Espada especial",
                Map.of(Enchantment.SHARPNESS, 4, Enchantment.UNBREAKING, 3)));

        items.set(5, buildWeapon(Material.NETHERITE_AXE, "<#b06dff>Machado especial",
                Map.of(Enchantment.SHARPNESS, 4, Enchantment.UNBREAKING, 3)));

        items.set(6, buildWeapon(Material.NETHERITE_PICKAXE, "<#b06dff>Picareta especial",
                Map.of(Enchantment.EFFICIENCY, 4, Enchantment.UNBREAKING, 4, Enchantment.FORTUNE, 2)));

        return items;
    }

    private static ItemStack buildArmor(Material mat, String displayName,
                                        Map<Enchantment, Integer> enchants,
                                        TrimPattern trimPattern, TrimMaterial trimMaterial) {
        ItemStack item = new ItemStack(mat);
        ArmorMeta meta = (ArmorMeta) item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(mm.deserialize("<!italic>" + displayName));
        enchants.forEach((ench, level) -> meta.addEnchant(ench, level, true));
        meta.setTrim(new ArmorTrim(trimMaterial, trimPattern));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildWeapon(Material mat, String displayName,
                                         Map<Enchantment, Integer> enchants) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(mm.deserialize("<!italic>" + displayName));
        enchants.forEach((ench, level) -> meta.addEnchant(ench, level, true));
        item.setItemMeta(meta);
        return item;
    }
}
