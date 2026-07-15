package com.psdk.adminabuse;

import com.psdk.PSDK;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** Fábrica + identificação dos itens-poder do arsenal (tag em PersistentDataContainer). */
public final class AdminAbuseItems {

    private AdminAbuseItems() {}

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public static final String POWER_METEOR  = "meteor";
    public static final String POWER_CHEST   = "chest";
    public static final String POWER_TORNADO = "tornado";
    public static final String POWER_THUNDER = "thunder";
    public static final String POWER_CHICKEN = "chicken";
    public static final String POWER_GATHER  = "gather";
    public static final String POWER_FREEZE  = "freeze";
    public static final String POWER_NUKE    = "nuke";

    private static NamespacedKey key(PSDK plugin) {
        return new NamespacedKey(plugin, "abuse_power");
    }

    /** O poder do item, ou null se não for item do arsenal. */
    public static String powerOf(PSDK plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key(plugin), PersistentDataType.STRING);
    }

    public static List<ItemStack> arsenal(PSDK plugin) {
        return List.of(meteor(plugin), egg(plugin), tornado(plugin), thunder(plugin),
                chicken(plugin), gather(plugin), freeze(plugin), nuke(plugin));
    }

    public static ItemStack meteor(PSDK plugin) {
        return build(plugin, Material.SNOWBALL, 16, POWER_METEOR,
                "<!italic><#ff4e2d><bold>☄ Cometa do Caos</bold>",
                "<!italic><#a4a4a4>Jogue e um <#ff4e2d>METEORO<#a4a4a4> cai do céu.",
                "<!italic><#a4a4a4>O impacto lança todo mundo pro alto!");
    }

    public static ItemStack egg(PSDK plugin) {
        return build(plugin, Material.EGG, 16, POWER_CHEST,
                "<!italic><#fcc850><bold>🥚 Ovo do Tesouro</bold>",
                "<!italic><#a4a4a4>Jogue e um baú <#b85afc>ÉPICO<#a4a4a4> ou",
                "<!italic><#fcc850>LENDÁRIO<#a4a4a4> aparece para todos!");
    }

    public static ItemStack tornado(PSDK plugin) {
        return build(plugin, Material.WIND_CHARGE, 16, POWER_TORNADO,
                "<!italic><#71f3ec><bold>🌀 Tornado Portátil</bold>",
                "<!italic><#a4a4a4>Jogue e um <#71f3ec>TORNADO<#a4a4a4> suga os",
                "<!italic><#a4a4a4>jogadores e cospe todo mundo pro céu!");
    }

    public static ItemStack thunder(PSDK plugin) {
        return build(plugin, Material.BLAZE_ROD, 1, POWER_THUNDER,
                "<!italic><#ffe14e><bold>⚡ Cajado do Trovão</bold>",
                "<!italic><#a4a4a4>Clique direito: <#ffe14e>RAIO EM CADEIA<#a4a4a4>",
                "<!italic><#a4a4a4>onde a sua mira apontar!");
    }

    public static ItemStack chicken(PSDK plugin) {
        return build(plugin, Material.FEATHER, 1, POWER_CHICKEN,
                "<!italic><#fcfcfc><bold>🐔 Pena do Apocalipse</bold>",
                "<!italic><#a4a4a4>Clique direito: chove <#fcfcfc>GALINHAS<#a4a4a4>",
                "<!italic><#a4a4a4>na sua mira. Elas somem sozinhas.");
    }

    public static ItemStack gather(PSDK plugin) {
        return build(plugin, Material.ENDER_PEARL, 16, POWER_GATHER,
                "<!italic><#b85afc><bold>🧲 Pérola Magnética</bold>",
                "<!italic><#a4a4a4>Jogue e <#b85afc>TODOS os jogadores<#a4a4a4>",
                "<!italic><#a4a4a4>são puxados pra onde ela cair!");
    }

    public static ItemStack freeze(PSDK plugin) {
        return build(plugin, Material.ICE, 1, POWER_FREEZE,
                "<!italic><#9ce8ff><bold>🧊 Gelo Eterno</bold>",
                "<!italic><#a4a4a4>Clique direito: <#9ce8ff>CONGELA<#a4a4a4>",
                "<!italic><#a4a4a4>TODO MUNDO no lugar por 5 segundos!");
    }

    public static ItemStack nuke(PSDK plugin) {
        return build(plugin, Material.FIRE_CHARGE, 1, POWER_NUKE,
                "<!italic><#ff2222><bold>☢ Nuke de Mentira</bold>",
                "<!italic><#a4a4a4>Clique direito: explosão <#ff2222>NUCLEAR<#a4a4a4>",
                "<!italic><#a4a4a4>na mira (só visual — sem destruir nada).");
    }

    private static ItemStack build(PSDK plugin, Material mat, int amount, String power,
                                   String nameMini, String... loreMini) {
        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(nameMini));
        meta.lore(java.util.Arrays.stream(loreMini).map(l -> (Component) MM.deserialize(l)).toList());
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, power);
        item.setItemMeta(meta);
        return item;
    }
}
