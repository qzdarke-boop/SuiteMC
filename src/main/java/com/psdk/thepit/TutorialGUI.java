package com.psdk.thepit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Map;

public class TutorialGUI implements InventoryHolder {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final Component TITLE = mm.deserialize("<!italic><#a4a4a4>Locais e sistemas do servidor");

    public static final int SLOT_CAIXAS = 11;
    public static final int SLOT_SHOP   = 12;
    public static final int SLOT_COLINA = 14;
    public static final int SLOT_GOLD   = 15;
    public static final int SLOT_STATS  = 20;
    public static final int SLOT_AFK    = 21;
    public static final int SLOT_REAIS  = 23;
    public static final int SLOT_SPAWN  = 24;

    private static final Map<Integer, String> SLOT_COMMANDS = Map.of(
            SLOT_CAIXAS, "caixas",
            SLOT_SHOP,   "shop",
            SLOT_GOLD,   "coins",
            SLOT_COLINA, "donocolina",
            SLOT_STATS,  "tops",
            SLOT_AFK,    "afk",
            SLOT_REAIS,  "tokens",
            SLOT_SPAWN,  "spawn"
    );

    private final Inventory inventory;

    private TutorialGUI() {
        this.inventory = Bukkit.createInventory(this, 36, TITLE);
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public static String getCommand(int slot) {
        return SLOT_COMMANDS.get(slot);
    }

    public static boolean isClickable(int slot) {
        return SLOT_COMMANDS.containsKey(slot);
    }

    public static Inventory build() {
        TutorialGUI holder = new TutorialGUI();
        Inventory inv = holder.inventory;

        inv.setItem(SLOT_CAIXAS, createItem(Material.CHEST,
                "<!italic><#fcc850>Caixas",
                "<!italic><#a4a4a4>Abra caixas e escolha",
                "<!italic><#a4a4a4>um item exclusivo de",
                "<!italic><#a4a4a4>sua preferência.",
                " ",
                "<!italic><#a4a4a4> ▸ Uso rápido: <#fcc850>/caixas",
                " ",
                "<!italic><#fcc850>Clique para acessar."));

        inv.setItem(SLOT_SHOP, createItem(Material.GOLD_INGOT,
                "<!italic><#fcc850>Loja",
                "<!italic><#a4a4a4>Compre armas, armaduras,",
                "<!italic><#a4a4a4>blocos e mais usando",
                "<!italic><#a4a4a4>seus coins.",
                " ",
                "<!italic><#a4a4a4> ▸ Uso rápido: <#fcc850>/shop",
                " ",
                "<!italic><#fcc850>Clique para acessar."));

        inv.setItem(SLOT_GOLD, createItem(Material.SUNFLOWER,
                "<!italic><#fcc850>Coins",
                "<!italic><#a4a4a4>Veja seu saldo de coins.",
                "<!italic><#a4a4a4>Ganhe coins com kills",
                "<!italic><#a4a4a4>e mineração.",
                " ",
                "<!italic><#a4a4a4> ▸ Uso rápido: <#fcc850>/coins",
                " ",
                "<!italic><#fcc850>Clique para acessar."));

        inv.setItem(SLOT_COLINA, createItem(Material.GOLDEN_APPLE,
                "<!italic><#fcc850>Colina",
                "<!italic><#a4a4a4>Domine a colina e ganhe",
                "<!italic><#a4a4a4>coins continuamente!",
                "<!italic><#a4a4a4>Elimine outros jogadores",
                "<!italic><#a4a4a4>para dominar.",
                " ",
                "<!italic><#a4a4a4> ▸ Uso rápido: <#fcc850>/donocolina",
                " ",
                "<!italic><#fcc850>Clique para acessar."));

        inv.setItem(SLOT_STATS, createItem(Material.BOOK,
                "<!italic><#fcc850>Rankings",
                "<!italic><#a4a4a4>Veja os tops de kills,",
                "<!italic><#a4a4a4>blocos, coins e mais.",
                " ",
                "<!italic><#a4a4a4> ▸ Uso rápido: <#fcc850>/tops",
                " ",
                "<!italic><#fcc850>Clique para acessar."));

        inv.setItem(SLOT_AFK, createItem(Material.CLOCK,
                "<!italic><#B100E2>Farm de Coins AFK",
                "<!italic><#a4a4a4>Fique AFK e farme",
                "<!italic><#a4a4a4>Coins para comprar",
                "<!italic><#a4a4a4>itens na loja!",
                " ",
                "<!italic><#cbd1d7><bold>Farme fora da área!",
                "<!italic><#a4a4a4>Jogadores VIP farmam",
                "<!italic><#a4a4a4>aura em qualquer lugar.",
                " ",
                "<!italic><#a4a4a4> ▸ Uso rápido: <#fcc850>/afk",
                " ",
                "<!italic><#fcc850>Clique para acessar."));

        inv.setItem(SLOT_REAIS, createItem(Material.EMERALD,
                "<!italic><#10fc46>Tokens 💰",
                "<!italic><#a4a4a4>Veja seu saldo de tokens.",
                "<!italic><#a4a4a4>Moeda premium usada",
                "<!italic><#a4a4a4>para comprar chaves.",
                " ",
                "<!italic><#a4a4a4> ▸ Uso rápido: <#fcc850>/tokens",
                " ",
                "<!italic><#fcc850>Clique para acessar."));

        inv.setItem(SLOT_SPAWN, createItem(Material.ENDER_PEARL,
                "<!italic><#fcc850>Spawn",
                "<!italic><#a4a4a4>Voltar ao spawn.",
                "<!italic><#a4a4a4>Espere 3 segundos",
                "<!italic><#a4a4a4>sem se mover.",
                " ",
                " ",
                "<!italic><#a4a4a4> ▸ Uso rápido: <#fcc850>/spawn",
                " ",
                "<!italic><#fcc850>Clique para acessar."));

        return inv;
    }

    private static ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(mm.deserialize(name));
        if (lore.length > 0) {
            meta.lore(Arrays.stream(lore).map(mm::deserialize).toList());
        }
        item.setItemMeta(meta);
        return item;
    }
}
