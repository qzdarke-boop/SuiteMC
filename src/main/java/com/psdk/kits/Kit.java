package com.psdk.kits;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public enum Kit {

    FERRAMENTAS("Ferramentas", Material.DIAMOND_PICKAXE,
            TimeUnit.MINUTES.toMillis(5), "<#10fc46>Kit Ferramentas",
            List.of(
                named(Material.DIAMOND_PICKAXE, 1, "<!italic><#e22c27>Picareta de diamante"),
                named(Material.DIAMOND_AXE,     1, "<!italic><#e22c27>Machado de diamante"),
                named(Material.DIAMOND_SHOVEL,  1, "<!italic><#e22c27>Pá de diamante")
            )),

    COMIDA("Comida", Material.COOKED_BEEF,
            TimeUnit.MINUTES.toMillis(10), "<#10fc46>Kit Comida",
            List.of(new ItemStack(Material.COOKED_BEEF, 40))),

    PVP("PvP", Material.DIAMOND_SWORD,
            TimeUnit.MINUTES.toMillis(20), "<#10fc46>Kit PvP",
            List.of(
                enchanted(Material.DIAMOND_SWORD, Map.of(Enchantment.SHARPNESS, 1)),
                enchanted(Material.DIAMOND_HELMET, Map.of(Enchantment.PROTECTION, 1)),
                enchanted(Material.DIAMOND_CHESTPLATE, Map.of(Enchantment.PROTECTION, 1)),
                enchanted(Material.DIAMOND_LEGGINGS, Map.of(Enchantment.PROTECTION, 1)),
                enchanted(Material.DIAMOND_BOOTS, Map.of(Enchantment.PROTECTION, 1))
            ));

    private final String name;
    private final Material icon;
    private final long cooldownMs;
    private final String displayName;
    private final List<ItemStack> items;

    Kit(String name, Material icon, long cooldownMs, String displayName, List<ItemStack> items) {
        this.name        = name;
        this.icon        = icon;
        this.cooldownMs  = cooldownMs;
        this.displayName = displayName;
        this.items       = items;
    }

    public String getName()           { return name; }
    public Material getIcon()         { return icon; }
    public long getCooldownMs()       { return cooldownMs; }
    public String getDisplayName()    { return displayName; }
    public List<ItemStack> getItems() { return items; }

    public String configKey() { return name().toLowerCase(); }

    private static ItemStack named(Material mat, int amount, String name) {
        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MiniMessage.miniMessage().deserialize(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack enchanted(Material mat, Map<Enchantment, Integer> enchants) {
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            enchants.forEach((ench, level) -> meta.addEnchant(ench, level, true));
            item.setItemMeta(meta);
        }
        return item;
    }
}
