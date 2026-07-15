package com.psdk.adminabuse;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

/** Painel de controle do Admin Abuse — todos os eventos em um clique. */
public class AdminAbuseGUI implements InventoryHolder {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    public static final Component TITLE = mm.deserialize("<#ff4e2d><bold>⚡ ADMIN ABUSE ⚡");

    public static final int SLOT_START    = 10;
    public static final int SLOT_MINING   = 11;
    public static final int SLOT_CHESTALL = 12;
    public static final int SLOT_COINS    = 13;
    public static final int SLOT_FIREWORK = 14;
    public static final int SLOT_ARSENAL  = 15;
    public static final int SLOT_STOP     = 16;
    public static final int SLOT_BRAZIL   = 17;
    public static final int SLOT_SHUFFLE  = 18;
    public static final int SLOT_ROCKET   = 19;
    public static final int SLOT_TURBO    = 20;
    public static final int SLOT_ZOMBIE   = 21;
    public static final int SLOT_SIZE     = 22;
    public static final int SLOT_LOTTERY  = 23;
    public static final int SLOT_POTATO   = 24;
    public static final int SLOT_RAINBOW  = 25;
    public static final int SLOT_STORM    = 26;

    private final Inventory inventory;

    public AdminAbuseGUI(AdminAbuseManager manager) {
        this.inventory = Bukkit.createInventory(this, 27, TITLE);
        build(manager);
    }

    private void build(AdminAbuseManager manager) {
        ItemStack filler = named(new ItemStack(Material.BLACK_STAINED_GLASS_PANE), "<!italic> ");
        for (int i = 0; i < 27; i++) inventory.setItem(i, filler);

        boolean active = manager.isActive();
        boolean mining = manager.isMining2x();

        inventory.setItem(SLOT_START, named(new ItemStack(Material.NETHER_STAR),
                active ? "<!italic><#a4a4a4><bold>⚡ SHOW EM ANDAMENTO" : "<!italic><#ff4e2d><bold>⚡ INICIAR O SHOW",
                active ? "<!italic><#a4a4a4>Host atual: <#fcc850>" + manager.getHostName()
                       : "<!italic><#a4a4a4>Fade preto + música + você",
                active ? "<!italic><#a4a4a4>Use Encerrar pra parar."
                       : "<!italic><#a4a4a4>recebe o <#fcc850>ARSENAL DO CAOS<#a4a4a4>!"));

        inventory.setItem(SLOT_MINING, named(new ItemStack(Material.GOLDEN_PICKAXE),
                "<!italic><#10fc46><bold>⛏ MINING 2X " + (mining ? "<#10fc46>[LIGADO]" : "<#e22c27>[DESLIGADO]"),
                "<!italic><#a4a4a4>Todo minério vale <#10fc46>DOBRO<#a4a4a4> de coins.",
                "<!italic><#a4a4a4>Clique pra " + (mining ? "desligar." : "ligar.")));

        inventory.setItem(SLOT_CHESTALL, named(new ItemStack(Material.CHEST),
                "<!italic><#fc9d1a><bold>☄ CHUVA DE BAÚS",
                "<!italic><#a4a4a4>Spawna baús de TODAS as raridades",
                "<!italic><#a4a4a4>caindo pelo mapa AGORA."));

        inventory.setItem(SLOT_COINS, named(new ItemStack(Material.SUNFLOWER),
                "<!italic><#ffe14e><bold>💰 CHUVA DE COINS",
                "<!italic><#a4a4a4>TODO MUNDO online ganha",
                "<!italic><#ffe14e>+250 coins<#a4a4a4> na hora."));

        inventory.setItem(SLOT_FIREWORK, named(new ItemStack(Material.FIREWORK_ROCKET),
                "<!italic><#ff5af0><bold>🎆 FESTA DE FOGOS",
                "<!italic><#a4a4a4>Fogos em todo mundo +",
                "<!italic><#71f3ec>SUPER PULO<#a4a4a4> por 15 segundos."));

        inventory.setItem(SLOT_ARSENAL, named(new ItemStack(Material.SNOWBALL),
                "<!italic><#71f3ec><bold>☄ RECEBER ARSENAL",
                "<!italic><#a4a4a4>Repõe os itens-poder na sua mão.",
                "<!italic><#a4a4a4>(precisa do show ativo)"));

        inventory.setItem(SLOT_STOP, named(new ItemStack(Material.BARRIER),
                "<!italic><#e22c27><bold>✖ ENCERRAR TUDO",
                "<!italic><#a4a4a4>Para música, eventos, bossbars",
                "<!italic><#a4a4a4>e recolhe o arsenal."));

        inventory.setItem(SLOT_BRAZIL, named(new ItemStack(Material.JUKEBOX),
                "<!italic><#009C3B><bold>🇧🇷 BRAZIL EVENT",
                "<!italic><#a4a4a4>Fade preto em todos os jogadores",
                "<!italic><#FFDF00>para você colocar a música!"));

        inventory.setItem(SLOT_SHUFFLE, named(new ItemStack(Material.ENDER_EYE),
                "<!italic><#b85afc><bold>🔀 EMBARALHAR JOGADORES",
                "<!italic><#a4a4a4>TODO MUNDO troca de lugar",
                "<!italic><#a4a4a4>com alguém aleatório. Caos puro."));

        inventory.setItem(SLOT_ROCKET, named(new ItemStack(Material.FIREWORK_STAR),
                "<!italic><#71f3ec><bold>🚀 FOGUETE COLETIVO",
                "<!italic><#a4a4a4>Lança TODO MUNDO pro céu",
                "<!italic><#a4a4a4>(com queda leve, ninguém morre)."));

        inventory.setItem(SLOT_TURBO, named(new ItemStack(Material.SUGAR),
                "<!italic><#71f3ec><bold>💨 MODO TURBO",
                "<!italic><#a4a4a4>Velocidade + haste + super pulo",
                "<!italic><#a4a4a4>pra todo mundo por 30 segundos."));

        inventory.setItem(SLOT_ZOMBIE, named(new ItemStack(Material.ZOMBIE_HEAD),
                "<!italic><#5b8731><bold>🧟 INVASÃO ZUMBI",
                "<!italic><#a4a4a4>Zumbis bebês perseguem todo mundo",
                "<!italic><#a4a4a4>(mordida ZERO, só o desespero)."));

        inventory.setItem(SLOT_SIZE, named(new ItemStack(Material.TURTLE_EGG),
                "<!italic><#ff5af0><bold>🤏 TAMANHO ALEATÓRIO",
                "<!italic><#a4a4a4>Cada jogador vira formiga OU",
                "<!italic><#a4a4a4>gigante por 30 segundos. Caos."));

        inventory.setItem(SLOT_LOTTERY, named(new ItemStack(Material.GOLD_INGOT),
                "<!italic><#ffe14e><bold>🎰 SORTEIO RELÂMPAGO",
                "<!italic><#a4a4a4>Roleta com suspense: um sortudo",
                "<!italic><#a4a4a4>aleatório leva <#ffe14e>500 coins<#a4a4a4>."));

        inventory.setItem(SLOT_POTATO, named(new ItemStack(Material.BAKED_POTATO),
                "<!italic><#ff8b2d><bold>💣 BATATA QUENTE",
                "<!italic><#a4a4a4>Um sorteado vira a bomba: brilha,",
                "<!italic><#a4a4a4>10s de tic-tac e... 💥 (sem dano)."));

        inventory.setItem(SLOT_RAINBOW, named(new ItemStack(Material.AMETHYST_SHARD),
                "<!italic><#b85afc><bold>🌈 RASTRO ARCO-ÍRIS",
                "<!italic><#a4a4a4>Todo mundo deixa um rastro",
                "<!italic><#a4a4a4>colorido por 60 segundos."));

        inventory.setItem(SLOT_STORM, named(new ItemStack(Material.LIGHTNING_ROD),
                "<!italic><#ffe14e><bold>⚡ TEMPESTADE ELÉTRICA",
                "<!italic><#a4a4a4>20s de raios (visuais) caindo",
                "<!italic><#a4a4a4>perto de todo mundo. Drama puro."));
    }

    private ItemStack named(ItemStack item, String nameMini, String... loreMini) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm.deserialize(nameMini));
        if (loreMini.length > 0)
            meta.lore(Arrays.stream(loreMini).map(l -> (Component) mm.deserialize(l)).toList());
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() { return inventory; }
}
