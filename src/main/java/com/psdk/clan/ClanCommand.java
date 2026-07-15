package com.psdk.clan;

import com.psdk.PSDK;
import com.psdk.util.ClanText;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ClanCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private final PSDK plugin;

    public record ClanColor(String name, String hex, String permission, String material, boolean isGradient) {}

    public static final List<ClanColor> CLAN_COLORS = List.of(
            // Cores sólidas
            new ClanColor("Branco", "#FFFFFF", "psdk.clan.cor.branco", "WHITE_DYE", false),
            new ClanColor("Cinza", "#AAAAAA", "psdk.clan.cor.cinza", "LIGHT_GRAY_DYE", false),
            new ClanColor("Preto", "#333333", "psdk.clan.cor.preto", "BLACK_DYE", false),
            new ClanColor("Vermelho", "#FF4444", "psdk.clan.cor.vermelho", "RED_DYE", false),
            new ClanColor("Laranja", "#FF8C00", "psdk.clan.cor.laranja", "ORANGE_DYE", false),
            new ClanColor("Amarelo", "#FFD700", "psdk.clan.cor.amarelo", "YELLOW_DYE", false),
            new ClanColor("Lima", "#32CD32", "psdk.clan.cor.lima", "LIME_DYE", false),
            new ClanColor("Verde", "#228B22", "psdk.clan.cor.verde", "GREEN_DYE", false),
            new ClanColor("Ciano", "#00CED1", "psdk.clan.cor.ciano", "CYAN_DYE", false),
            new ClanColor("Azul", "#4169E1", "psdk.clan.cor.azul", "BLUE_DYE", false),
            new ClanColor("Roxo", "#8A2BE2", "psdk.clan.cor.roxo", "PURPLE_DYE", false),
            new ClanColor("Magenta", "#FF00FF", "psdk.clan.cor.magenta", "MAGENTA_DYE", false),
            new ClanColor("Rosa", "#FF69B4", "psdk.clan.cor.rosa", "PINK_DYE", false),
            new ClanColor("Marrom", "#8B4513", "psdk.clan.cor.marrom", "BROWN_DYE", false),
            new ClanColor("Dourado", "#FFB800", "psdk.clan.cor.dourado", "GOLD_INGOT", false),
            new ClanColor("Aqua", "#00FFFF", "psdk.clan.cor.aqua", "DIAMOND", false),
            new ClanColor("Coral", "#FF7F50", "psdk.clan.cor.coral", "FIRE_CORAL", false),
            new ClanColor("Turquesa", "#40E0D0", "psdk.clan.cor.turquesa", "PRISMARINE_SHARD", false),
            new ClanColor("Violeta", "#EE82EE", "psdk.clan.cor.violeta", "ALLIUM", false)
    );

    public static final List<ClanColor> CLAN_GRADIENTS = List.of(
            // Gradientes
            new ClanColor("Arco-iris", "<gradient:#FF4B4B:#FF8B2D:#FFD031:#DCFF39:#6DFF45:#3BFFC7:#58D0FF:#5489FF>", "psdk.clan.gradient.arcoiris", "NETHER_STAR", true),
            new ClanColor("Fogo", "<gradient:#FF0000:#FF4500:#FFA500>", "psdk.clan.gradient.fogo", "BLAZE_POWDER", true),
            new ClanColor("Oceano", "<gradient:#00CED1:#1E90FF:#0000CD>", "psdk.clan.gradient.oceano", "HEART_OF_THE_SEA", true),
            new ClanColor("Floresta", "<gradient:#228B22:#32CD32:#90EE90>", "psdk.clan.gradient.floresta", "OAK_LEAVES", true),
            new ClanColor("Anoitecer", "<gradient:#FF6B6B:#C44569:#6C5CE7>", "psdk.clan.gradient.anoitecer", "END_CRYSTAL", true),
            new ClanColor("Amanhecer", "<gradient:#FF9A8B:#FECDA6:#FFF6B7>", "psdk.clan.gradient.amanhecer", "SUNFLOWER", true),
            new ClanColor("Neon", "<gradient:#FF00FF:#00FFFF:#FF00FF>", "psdk.clan.gradient.neon", "GLOWSTONE_DUST", true),
            new ClanColor("Galaxy", "<gradient:#0F0C29:#302B63:#24243E>", "psdk.clan.gradient.galaxy", "ENDER_EYE", true),
            new ClanColor("Aurora", "<gradient:#00C9FF:#92FE9D>", "psdk.clan.gradient.aurora", "EMERALD", true),
            new ClanColor("Sangue", "<gradient:#8B0000:#DC143C:#FF6347>", "psdk.clan.gradient.sangue", "REDSTONE", true),
            new ClanColor("Gelo", "<gradient:#A5FECB:#20BDFF:#5433FF>", "psdk.clan.gradient.gelo", "BLUE_ICE", true),
            new ClanColor("Ouro", "<gradient:#F7971E:#FFD200>", "psdk.clan.gradient.ouro", "GOLD_BLOCK", true),
            new ClanColor("Rosa-Quente", "<gradient:#FF0080:#FF8C00>", "psdk.clan.gradient.rosaquente", "PINK_TULIP", true),
            new ClanColor("Menta", "<gradient:#00B09B:#96C93D>", "psdk.clan.gradient.menta", "VINE", true),
            new ClanColor("Roxo-Azul", "<gradient:#8E2DE2:#4A00E0>", "psdk.clan.gradient.roxoazul", "AMETHYST_SHARD", true),
            new ClanColor("Pessego", "<gradient:#FFECD2:#FCB69F>", "psdk.clan.gradient.pessego", "PEONY", true),
            new ClanColor("Eletrico", "<gradient:#00F260:#0575E6>", "psdk.clan.gradient.eletrico", "LIGHTNING_ROD", true),
            new ClanColor("Lava", "<gradient:#F12711:#F5AF19>", "psdk.clan.gradient.lava", "LAVA_BUCKET", true),
            new ClanColor("Noite", "<gradient:#141E30:#243B55>", "psdk.clan.gradient.noite", "BLACK_CONCRETE", true),
            new ClanColor("Candy", "<gradient:#FF61D2:#FE9090:#FFCF86>", "psdk.clan.gradient.candy", "COOKIE", true),
            new ClanColor("Toxico", "<gradient:#56AB2F:#A8E063>", "psdk.clan.gradient.toxic", "SLIME_BALL", true)
    );

    public static final List<ClanColor> CLAN_ANIMATED = List.of(
            // --- SÉRIE F0F0 (TEXT_EFFECT) ---
            new ClanColor("Orange Waving",        "#F0F004", "psdk.clan.animated.orangewaving",    "ORANGE_DYE",          false),
            new ClanColor("Rainbow Shine",        "#F0F008", "psdk.clan.animated.rainbowshine",    "NETHER_STAR",         false),
            new ClanColor("Rainbow Disperse",     "#F0F00C", "psdk.clan.animated.rainbowdisperse", "FIREWORK_ROCKET",     false),
            new ClanColor("Glowing White",        "#F0F010", "psdk.clan.animated.f0f010",          "WHITE_CANDLE",        false),
            new ClanColor("Rainbow",              "#F0F01C", "psdk.clan.animated.rainbow",         "MAGENTA_DYE",         false),
            new ClanColor("Silver Shimmer",       "#F0F024", "psdk.clan.animated.f0f024",          "IRON_INGOT",          false),
            new ClanColor("Gray Pulse",           "#F0F028", "psdk.clan.animated.f0f028",          "GRAY_CONCRETE",       false),
            new ClanColor("Shadow flicker",       "#F0F02C", "psdk.clan.animated.f0f02c",          "BLACK_CANDLE",        false),
            new ClanColor("Earth wave",           "#F0F038", "psdk.clan.animated.f0f038",          "ROOTED_DIRT",         false),
            new ClanColor("Piscando",             "#F0F03C", "psdk.clan.animated.piscando",        "REDSTONE_TORCH",      false),
            new ClanColor("Golden wave",          "#F0F044", "psdk.clan.animated.f0f044",          "GOLD_INGOT",          false),
            new ClanColor("Lime glow",            "#F0F050", "psdk.clan.animated.f0f050",          "LIME_CANDLE",         false),
            new ClanColor("Green",                "#F0F064", "psdk.clan.animated.green",           "GREEN_DYE",           false),
            new ClanColor("Blue",                 "#F0F068", "psdk.clan.animated.blue",            "BLUE_DYE",            false),
            new ClanColor("Yellow",               "#F0F06C", "psdk.clan.animated.yellow",          "YELLOW_CANDLE",       false),
            new ClanColor("Purple",               "#F0F070", "psdk.clan.animated.purple",          "PURPLE_DYE",          false),
            new ClanColor("Pink",                 "#F0F074", "psdk.clan.animated.pink",            "PINK_DYE",            false),
            new ClanColor("Red",                  "#F0F078", "psdk.clan.animated.red",             "RED_DYE",             false),
            new ClanColor("Dreamteam",            "#F0F07C", "psdk.clan.animated.dreamteam",       "HEART_OF_THE_SEA",    false),
            new ClanColor("Cosmic Quartz",        "#F0F080", "psdk.clan.animated.cosmicquartz",    "AMETHYST_SHARD",      false),
            new ClanColor("Easter",               "#F0F084", "psdk.clan.animated.easter",          "PINK_TULIP",          false),
            new ClanColor("Ivory Glow",           "#F0F088", "psdk.clan.animated.f0f088",          "WHITE_TULIP",         false),
            new ClanColor("Daisy Bloom",          "#F0F08C", "psdk.clan.animated.f0f08c",          "OXEYE_DAISY",         false),
            new ClanColor("Violet Dream",         "#F0F090", "psdk.clan.animated.f0f090",          "ALLIUM",              false),
            new ClanColor("Azure Breeze",         "#F0F094", "psdk.clan.animated.f0f094",          "AZURE_BLUET",         false),
            new ClanColor("Icy Aurora",           "#F0F094", "psdk.clan.animated.icyaurora",       "BLUE_ICE",            false),
            new ClanColor("Mint Delight",         "#F0F098", "psdk.clan.animated.mintdelight",     "VINE",                false),
            new ClanColor("Summer Sunshine",      "#F0F09C", "psdk.clan.animated.summersunshine",  "SUNFLOWER",           false),
            new ClanColor("Wizard Magic",         "#F0F0A0", "psdk.clan.animated.wizardmagic",     "ENCHANTING_TABLE",    false),
            new ClanColor("Ocean Blue",           "#F0F0A4", "psdk.clan.animated.oceanblue",       "HEART_OF_THE_SEA",    false),
            new ClanColor("Pumpkin Patch",        "#F0F0A8", "psdk.clan.animated.pumpkinpatch",    "CARVED_PUMPKIN",      false),
            new ClanColor("Witch Brew",           "#F0F0AC", "psdk.clan.animated.witchbrew",       "BREWING_STAND",       false),
            new ClanColor("Vampire Kiss",         "#F0F0B0", "psdk.clan.animated.vampirekiss",     "RED_MUSHROOM",        false),
            new ClanColor("Electric Pulse",       "#F0F0B4", "psdk.clan.animated.electricpulse",   "LIGHTNING_ROD",       false),
            new ClanColor("Spring Meadow",        "#F0F0B8", "psdk.clan.animated.springmeadow",    "GRASS_BLOCK",         false),
            new ClanColor("Golden Sparkle",       "#F0F0BC", "psdk.clan.animated.goldensparkle",   "GOLD_NUGGET",         false),
            new ClanColor("Candy Cane",           "#F0F0C0", "psdk.clan.animated.candycane",       "SUGAR",               false),
            new ClanColor("Poinsettia Bloom",     "#F0F0C4", "psdk.clan.animated.poinsettia",      "RED_TULIP",           false),
            new ClanColor("Sweet Embrace",        "#F0F0C8", "psdk.clan.animated.sweetembrace",    "COOKIE",              false),
            new ClanColor("Enchanted Evening",    "#F0F0CC", "psdk.clan.animated.enchantedeve",    "END_CRYSTAL",         false),
            new ClanColor("Scarlet Passion",      "#F0F0D0", "psdk.clan.animated.scarletpassion",  "POPPY",               false),
            new ClanColor("Pale Rainbow Fractal", "#F0F0D8", "psdk.clan.animated.prf",             "PRISMARINE_CRYSTALS", false),
            new ClanColor("Easter Shimmer",       "#F0F0DC", "psdk.clan.animated.eastershimmer",   "RABBIT_FOOT",         false),
            new ClanColor("Ying Yang",             "#F0F0E0", "psdk.clan.animated.yinyang",         "BONE",                false),
            new ClanColor("Candy Floss",          "#F0F0E4", "psdk.clan.animated.candyfloss",      "PINK_GLAZED_TERRACOTTA", false),
            new ClanColor("Spring Garden",        "#F0F0E8", "psdk.clan.animated.springgarden",    "OAK_SAPLING",         false),
            // --- SÉRIE F5F5 (GRADIENT TEXT_EFFECT) ---
            new ClanColor("Grad Ying Yang",        "#F5F505", "psdk.clan.animated.gradyinyang",     "BONE_BLOCK",          false),
            new ClanColor("Grad Easter Egg",      "#F5F50A", "psdk.clan.animated.gradeasteregg",   "EGG",                 false),
            new ClanColor("Grad Cotton Candy",    "#F5F50F", "psdk.clan.animated.gradcottoncandy", "PINK_WOOL",           false),
            new ClanColor("Grad Peach Summer",         "#F5F514", "psdk.clan.animated.peachsummer",     "PEONY",               false),
            new ClanColor("Grad MTN Dew Prefix",       "#F5F519", "psdk.clan.animated.mtndewprefix",    "LIME_CONCRETE",       false),
            new ClanColor("Grad MTN Dew Chat",         "#F5F51E", "psdk.clan.animated.mtndewchat",      "LIME_WOOL",           false),
            new ClanColor("Grad Water",                "#F5F523", "psdk.clan.animated.water",           "WATER_BUCKET",        false),
            new ClanColor("Grad Rainbowshine",    "#F5F528", "psdk.clan.animated.rainbowshine",    "NETHER_STAR",         false),
            new ClanColor("Grad America",              "#F5F52D", "psdk.clan.animated.america",         "BLUE_BANNER",         false),
            new ClanColor("Grad Flame",                "#F5F532", "psdk.clan.animated.flame",           "BLAZE_POWDER",        false),
            new ClanColor("Grad Devils Nightmare",     "#F5F537", "psdk.clan.animated.devilsnightmare", "REDSTONE_BLOCK",      false),
            new ClanColor("Grad Midnight Curse",       "#F5F53C", "psdk.clan.animated.midnightcurse",   "PURPLE_CONCRETE",     false),
            new ClanColor("Grad Halloween Night",      "#F5F541", "psdk.clan.animated.halloweennight",  "CARVED_PUMPKIN",      false),
            new ClanColor("Grad Witch Poison",         "#F5F546", "psdk.clan.animated.witchpoison",     "SPIDER_EYE",          false),
            new ClanColor("Grad Night Sky",            "#F5F54B", "psdk.clan.animated.nightsky",        "ENDER_EYE",           false),
            new ClanColor("Grad Ghost",                "#F5F550", "psdk.clan.animated.ghost",           "WHITE_WOOL",          false),
            new ClanColor("Grad Volcano",              "#F5F555", "psdk.clan.animated.volcano",         "MAGMA_BLOCK",         false),
            new ClanColor("Grad Aurora",               "#F5F55A", "psdk.clan.animated.aurora",          "EMERALD",             false),
            new ClanColor("Grad Pink Blue",            "#F5F55F", "psdk.clan.animated.pinkblue",        "LIGHT_BLUE_DYE",      false),
            new ClanColor("Grad Rainbow Nox",          "#F5F564", "psdk.clan.animated.rainbownox",      "GLOWSTONE",           false),
            new ClanColor("Grad Pastel Fractal",       "#F5F569", "psdk.clan.animated.pof",             "QUARTZ",              false),
            new ClanColor("Grad Winter Camo",          "#F5F56E", "psdk.clan.animated.wintercamo",      "POWDER_SNOW_BUCKET",  false),
            new ClanColor("Grad Cookie Monster",       "#F5F573", "psdk.clan.animated.cookiemonster",   "COOKIE",              false),
            new ClanColor("Grad Firework",             "#F5F578", "psdk.clan.animated.firework",        "FIREWORK_ROCKET",     false),
            new ClanColor("Grad Christmas Tree",       "#F5F57D", "psdk.clan.animated.christmastree",   "SPRUCE_SAPLING",      false),
            new ClanColor("Grad Starlit Heart",        "#F5F582", "psdk.clan.animated.starlitheart",    "NETHER_STAR",         false),
            new ClanColor("Grad Lunar Love",           "#F5F587", "psdk.clan.animated.lunarlove",       "PHANTOM_MEMBRANE",    false),
            new ClanColor("Grad Cherry Blossom",       "#F5F58C", "psdk.clan.animated.cherryblossom",   "CHERRY_LEAVES",       false),
            new ClanColor("Grad Blush Kiss",           "#F5F591", "psdk.clan.animated.blushkiss",       "ROSE_BUSH",           false),
            new ClanColor("Grad Sweet Embrace",   "#F5F596", "psdk.clan.animated.sweetembracef5",  "PINK_PETALS",         false),
            new ClanColor("Grad Spring Light",         "#F5F59B", "psdk.clan.animated.springlight",     "DANDELION",           false),
            new ClanColor("Grad Brasil",               "#F5F5A0", "psdk.clan.animated.brasil",          "LIME_BANNER",         false),
            new ClanColor("Grad Forest Spirit",        "#F5F5A5", "psdk.clan.animated.forestspirit",    "OAK_LEAVES",          false),
            new ClanColor("Grad Dreamy Cloud",         "#F5F5AA", "psdk.clan.animated.dreamycloud",     "WHITE_CONCRETE",      false),
            new ClanColor("Grad Crystal Pulse",        "#F5F5AF", "psdk.clan.animated.crystalpulse",    "AMETHYST_BLOCK",      false)
    );

    public static List<ClanColor> getAllColors() {
        List<ClanColor> all = new ArrayList<>();
        all.addAll(CLAN_COLORS);
        all.addAll(CLAN_GRADIENTS);
        all.addAll(CLAN_ANIMATED);
        return all;
    }

    public static boolean hasColorPermission(Player player, ClanColor color) {
        return player.isOp() || player.hasPermission(color.permission());
    }

    public ClanCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Apenas jogadores podem usar este comando."));
            return true;
        }

        ClanManager cm = plugin.getClanManager();

        if (args.length == 0) {
            Clan clan = cm.getClanByPlayer(player.getUniqueId());
            ClanGUI.openMainMenu(player, clan);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "criar" -> handleCriar(player, cm, args);
            case "deletar" -> handleDeletar(player, cm);
            case "aceitar" -> handleAceitar(player, cm, args);
            case "sair" -> handleSair(player, cm);
            case "convidar" -> handleConvidar(player, cm, args);
            case "expulsar" -> handleExpulsar(player, cm, args);
            case "info" -> handleInfo(player, cm, args);
            case "chat" -> handleChat(player, cm, args);
            case "ally" -> handleAlly(player, cm, args);
            case "unally" -> handleUnally(player, cm, args);
            case "rival" -> handleRival(player, cm, args);
            case "unrival" -> handleUnrival(player, cm, args);
            case "transferir" -> handleTransferir(player, cm, args);
            case "promover" -> handleCargo(player, cm, args, true);
            case "rebaixar" -> handleCargo(player, cm, args, false);
            case "anunciar" -> handleAnunciar(player, cm, args);
            case "descricao" -> handleDescricao(player, cm, args);
            default -> player.sendMessage(mm.deserialize("<#FF0000>Uso: /clan [criar|deletar|aceitar|sair|convidar|expulsar|transferir|promover|rebaixar|anunciar|descricao|info|chat|ally|unally|rival|unrival]"));
        }

        return true;
    }

    private void handleCriar(Player player, ClanManager cm, String[] args) {
        if (args.length < 3) {
            player.sendMessage(mm.deserialize("<#FF0000>Uso: /clan criar <tag> <nome>"));
            return;
        }
        if (cm.getClanByPlayer(player.getUniqueId()) != null) {
            player.sendMessage(mm.deserialize("<#FF0000>Você já está em um clan!"));
            return;
        }

        boolean infiniteTag = player.hasPermission("psdk.infinitetag") || player.isOp();
        String tag = infiniteTag ? args[1] : args[1].toUpperCase();
        StringBuilder nameBuilder = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (i > 2) nameBuilder.append(" ");
            nameBuilder.append(args[i]);
        }
        String name = nameBuilder.toString();

        if (!infiniteTag && !tag.matches("[A-Z0-9]+")) {
            player.sendMessage(mm.deserialize("<#FF0000>A tag deve conter apenas letras e números!"));
            return;
        }
        if (!infiniteTag && (tag.length() < 3 || tag.length() > 4)) {
            player.sendMessage(mm.deserialize("<#FF0000>A tag deve ter 3-4 caracteres!"));
            return;
        }
        if (infiniteTag && tag.length() < 2) {
            player.sendMessage(mm.deserialize("<#FF0000>A tag deve ter pelo menos 2 caracteres!"));
            return;
        }
        if (name.length() < 3 || name.length() > 16) {
            player.sendMessage(mm.deserialize("<#FF0000>O nome deve ter entre 3 e 16 caracteres!"));
            return;
        }

        Clan clan = cm.createClan(player.getUniqueId(), tag, name, infiniteTag);
        if (clan != null) {
            player.sendMessage(mm.deserialize("<#10fc46>Clan <white>[" + clan.getTag() + "] " + clan.getName() + " <#10fc46>criado com sucesso!"));
        } else {
            player.sendMessage(mm.deserialize("<#FF0000>Não foi possível criar o clan. Tag já existe ou dados inválidos."));
        }
    }

    private void handleDeletar(Player player, ClanManager cm) {
        Clan clan = cm.getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não está em um clan!"));
            return;
        }
        if (!clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(mm.deserialize("<#FF0000>Apenas o líder pode deletar o clan!"));
            return;
        }

        cm.deleteClan(clan.getId());
        player.sendMessage(mm.deserialize("<#FF0000>Seu clan foi desfeito."));
    }

    private void handleAceitar(Player player, ClanManager cm, String[] args) {
        if (args.length < 2) {
            player.sendMessage(mm.deserialize("<#FF0000>Uso: /clan aceitar <tag>"));
            return;
        }
        if (cm.getClanByPlayer(player.getUniqueId()) != null) {
            player.sendMessage(mm.deserialize("<#FF0000>Você já está em um clan!"));
            return;
        }

        String tag = args[1].toUpperCase();
        if (cm.acceptInvite(player.getUniqueId(), tag)) {
            Clan clan = cm.getClanByTag(tag);
            player.sendMessage(mm.deserialize("<#10fc46>Você entrou no clan <white>[" + tag + "]<#10fc46>!"));
            Player leader = (clan != null) ? Bukkit.getPlayer(clan.getLeader()) : null;
            if (leader != null) {
                leader.sendMessage(mm.deserialize("<#10fc46>" + player.getName() + " <#10fc46>entrou no clan!"));
            }
            if (clan != null) cm.log(clan.getId(), player.getName(), "entrar", player.getName() + " entrou no clan");
        } else {
            player.sendMessage(mm.deserialize("<#FF0000>Convite não encontrado, expirado, ou clan lotado."));
        }
    }

    private void handleSair(Player player, ClanManager cm) {
        Clan clan = cm.getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não está em um clan!"));
            return;
        }
        if (clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(mm.deserialize("<#FF0000>O líder não pode sair do clan! Use /clan deletar para desfazer."));
            return;
        }

        cm.removeMember(clan.getId(), player.getUniqueId());
        player.sendMessage(mm.deserialize("<#FF0000>Você saiu do clan <white>" + clan.getTag() + "<#FF0000>."));
        cm.log(clan.getId(), player.getName(), "sair", player.getName() + " saiu do clan");
    }

    private void handleConvidar(Player player, ClanManager cm, String[] args) {
        if (args.length < 2) {
            player.sendMessage(mm.deserialize("<#FF0000>Uso: /clan convidar <jogador>"));
            return;
        }
        Clan clan = cm.getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não está em um clan!"));
            return;
        }
        if (!cm.hasPermission(clan.getId(), player.getUniqueId(), "invite")) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não tem permissão para convidar jogadores!"));
            return;
        }
        int limite = ClanGUI.getMemberLimit(player);   // mesmo limite dinâmico do GUI (base 15, expansível por perm)
        if (clan.getMembers().size() >= limite) {
            player.sendMessage(mm.deserialize("<#FF0000>Seu clan já atingiu o limite de " + limite + " membros!"));
            return;
        }

        // Funciona com jogador ONLINE ou OFFLINE (busca o UUID mesmo offline).
        Player onlineTarget = Bukkit.getPlayer(args[1]);
        java.util.UUID targetUuid;
        String targetName;
        if (onlineTarget != null) {
            targetUuid = onlineTarget.getUniqueId();
            targetName = onlineTarget.getName();
        } else {
            org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayer(args[1]);
            if (!off.hasPlayedBefore()) {
                player.sendMessage(mm.deserialize("<#FF0000>Jogador não encontrado (nunca entrou no servidor)."));
                return;
            }
            targetUuid = off.getUniqueId();
            targetName = off.getName() != null ? off.getName() : args[1];
        }
        if (targetUuid.equals(player.getUniqueId())) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não pode convidar a si mesmo!"));
            return;
        }
        if (cm.getClanByPlayer(targetUuid) != null) {
            player.sendMessage(mm.deserialize("<#FF0000>Este jogador já está em um clan!"));
            return;
        }

        if (cm.invitePlayer(clan.getId(), targetUuid)) {
            player.sendMessage(mm.deserialize("<#10fc46>Convite enviado para <white>" + targetName + "<#10fc46>!"));
            if (onlineTarget != null) {
                onlineTarget.sendMessage(mm.deserialize("<#10fc46>Você recebeu um convite do clan <white>[" + clan.getTag() + "] " + clan.getName() + "<#10fc46>!"));
                onlineTarget.sendMessage(mm.deserialize("<gray>Use <yellow>/clan aceitar " + clan.getTag() + " <gray>para aceitar."));
            }
        } else {
            player.sendMessage(mm.deserialize("<#FF0000>Não foi possível enviar o convite."));
        }
    }

    private void handleExpulsar(Player player, ClanManager cm, String[] args) {
        if (args.length < 2) {
            player.sendMessage(mm.deserialize("<#FF0000>Uso: /clan expulsar <jogador>"));
            return;
        }
        Clan clan = cm.getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não está em um clan!"));
            return;
        }
        if (!cm.hasPermission(clan.getId(), player.getUniqueId(), "kick")) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não tem permissão para expulsar membros!"));
            return;
        }

        String targetInput = args[1];
        ClanMember target = null;
        for (ClanMember m : clan.getMembers()) {
            if (m.name().equalsIgnoreCase(targetInput)) {
                target = m;
                break;
            }
        }
        if (target == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Jogador não encontrado no seu clan!"));
            return;
        }
        if (target.role().equals("lider")) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não pode expulsar o líder!"));
            return;
        }
        if (target.uuid().equals(player.getUniqueId())) {
            player.sendMessage(mm.deserialize("<#FF0000>Use /clan sair para deixar o clan."));
            return;
        }

        if (!cm.removeMember(clan.getId(), target.uuid())) {
            player.sendMessage(mm.deserialize("<#FF0000>Não foi possível expulsar o membro."));
            return;
        }

        ClanGUI.closeClanGuiFor(target.uuid());
        Player kicked = Bukkit.getPlayer(target.uuid());
        if (kicked != null) {
            kicked.sendMessage(mm.deserialize("<#FF0000>Você foi expulso do clan <white>[" + clan.getTag() + "]<#FF0000>."));
        }
        player.sendMessage(mm.deserialize("<#10fc46>" + target.name() + " foi expulso do clan."));
        cm.log(clan.getId(), player.getName(), "expulsar", target.name() + " foi expulso do clan");
    }

    private void handleChat(Player player, ClanManager cm, String[] args) {
        if (args.length < 2) {
            player.sendMessage(mm.deserialize("<#FF0000>Uso: /clan chat <mensagem>"));
            return;
        }
        Clan clan = cm.getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não está em um clan!"));
            return;
        }
        StringBuilder msg = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) msg.append(" ");
            msg.append(args[i]);
        }
        ClanChatCommand.broadcastClanMessage(plugin, clan, player, msg.toString());
    }

    private void handleAlly(Player player, ClanManager cm, String[] args) {
        if (args.length < 2) {
            player.sendMessage(mm.deserialize("<#FF0000>Uso: /clan ally <tag>"));
            return;
        }
        Clan clan = cm.getClanByPlayer(player.getUniqueId());
        if (clan == null || !clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(mm.deserialize("<#FF0000>Apenas o líder pode gerenciar aliados!"));
            return;
        }
        Clan ally = cm.getClanByTag(args[1]);
        if (ally == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Clan não encontrado."));
            return;
        }
        String result = cm.requestAlly(clan.getId(), ally.getId());
        Player allyLeader = Bukkit.getPlayer(ally.getLeader());
        if ("allied".equals(result)) {
            player.sendMessage(mm.deserialize("<#10fc46>Aliança formada com <white>[" + ally.getTag() + "]<#10fc46>!"));
            if (allyLeader != null) {
                allyLeader.sendMessage(mm.deserialize("<#10fc46>Aliança formada com <white>[" + clan.getTag() + "]<#10fc46>!"));
            }
            cm.log(clan.getId(), player.getName(), "ally", "Aliança formada com [" + ally.getTag() + "]");
            cm.log(ally.getId(), player.getName(), "ally", "Aliança formada com [" + clan.getTag() + "]");
        } else if ("requested".equals(result)) {
            player.sendMessage(mm.deserialize("<#fcc850>Pedido de aliança enviado para <white>[" + ally.getTag() + "]<#fcc850>. Aguardando aceite."));
            if (allyLeader != null) {
                allyLeader.sendMessage(mm.deserialize("<#fcc850>O clan <white>[" + clan.getTag() + "]<#fcc850> quer ser seu aliado! Use <yellow>/clan ally " + clan.getTag() + " <#fcc850>para aceitar."));
            }
        } else {
            player.sendMessage(mm.deserialize("<#FF0000>Não foi possível enviar o pedido (já são aliados?)."));
        }
    }

    private void handleUnally(Player player, ClanManager cm, String[] args) {
        if (args.length < 2) {
            player.sendMessage(mm.deserialize("<#FF0000>Uso: /clan unally <tag>"));
            return;
        }
        Clan clan = cm.getClanByPlayer(player.getUniqueId());
        if (clan == null || !clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(mm.deserialize("<#FF0000>Apenas o líder pode gerenciar aliados!"));
            return;
        }
        Clan ally = cm.getClanByTag(args[1]);
        if (ally == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Clan não encontrado."));
            return;
        }
        if (cm.removeAlly(clan.getId(), ally.getId())) {
            player.sendMessage(mm.deserialize("<#10fc46>Aliança removida com <white>[" + ally.getTag() + "]<#10fc46>."));
            cm.log(clan.getId(), player.getName(), "unally", "Aliança removida com [" + ally.getTag() + "]");
        } else {
            player.sendMessage(mm.deserialize("<#FF0000>Não foi possível remover aliança."));
        }
    }

    private void handleTransferir(Player player, ClanManager cm, String[] args) {
        if (args.length < 2) {
            player.sendMessage(mm.deserialize("<#FF0000>Uso: /clan transferir <jogador>"));
            return;
        }
        Clan clan = cm.getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não está em um clan!"));
            return;
        }
        if (!clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(mm.deserialize("<#FF0000>Apenas o líder pode transferir a liderança!"));
            return;
        }

        ClanMember target = null;
        for (ClanMember m : clan.getMembers()) {
            if (m.name().equalsIgnoreCase(args[1])) { target = m; break; }
        }
        if (target == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Jogador não encontrado no seu clan!"));
            return;
        }
        if (target.uuid().equals(player.getUniqueId())) {
            player.sendMessage(mm.deserialize("<#FF0000>Você já é o líder!"));
            return;
        }

        if (cm.updateClanLeader(clan.getId(), target.uuid())) {
            player.sendMessage(mm.deserialize("<#10fc46>Liderança transferida para <white>" + target.name() + "<#10fc46>!"));
            Player newLeader = Bukkit.getPlayer(target.uuid());
            if (newLeader != null) {
                newLeader.sendMessage(mm.deserialize("<#fcc850>Você agora é o líder do clan <white>[" + clan.getTag() + "]<#fcc850>!"));
            }
            cm.log(clan.getId(), player.getName(), "transferir", "Liderança transferida para " + target.name());
        } else {
            player.sendMessage(mm.deserialize("<#FF0000>Não foi possível transferir a liderança."));
        }
    }

    /** /clan promover|rebaixar <jogador> — só o líder gerencia cargos. */
    private void handleCargo(Player player, ClanManager cm, String[] args, boolean promote) {
        String uso = promote ? "promover" : "rebaixar";
        if (args.length < 2) {
            player.sendMessage(mm.deserialize("<#FF0000>Uso: /clan " + uso + " <jogador>"));
            return;
        }
        Clan clan = cm.getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não está em um clan!"));
            return;
        }
        if (!clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(mm.deserialize("<#FF0000>Apenas o líder pode gerenciar cargos!"));
            return;
        }

        ClanMember target = null;
        for (ClanMember m : clan.getMembers()) {
            if (m.name().equalsIgnoreCase(args[1])) { target = m; break; }
        }
        if (target == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Jogador não encontrado no seu clan!"));
            return;
        }
        if (target.role().equals("lider")) {
            player.sendMessage(mm.deserialize("<#FF0000>O líder não pode ser " + (promote ? "promovido" : "rebaixado") + "! Use /clan transferir."));
            return;
        }

        // Move o membro um degrau na hierarquia de cargos personalizados.
        List<ClanManager.ClanRole> roles = cm.getRoles(clan.getId());
        ClanManager.ClanRole current = cm.getMemberRoleObj(clan.getId(), target.uuid());
        int idx = -1;
        for (int i = 0; i < roles.size(); i++) {
            if (current != null && roles.get(i).id() == current.id()) { idx = i; break; }
        }
        if (idx < 0) idx = roles.size() - 1; // sem cargo resolvido: trata como o mais baixo

        int newIdx = promote ? idx - 1 : idx + 1;
        if (newIdx < 0) {
            player.sendMessage(mm.deserialize("<#FF0000>" + target.name() + " já está no cargo mais alto."));
            return;
        }
        if (newIdx >= roles.size()) {
            player.sendMessage(mm.deserialize("<#FF0000>" + target.name() + " já está no cargo mais baixo."));
            return;
        }
        ClanManager.ClanRole newRole = roles.get(newIdx);

        if (cm.setMemberRole(clan.getId(), target.uuid(), newRole)) {
            player.sendMessage(mm.deserialize("<#10fc46>" + target.name() + " agora é <white>" + newRole.name() + "<#10fc46>."));
            Player targetPlayer = Bukkit.getPlayer(target.uuid());
            if (targetPlayer != null) {
                targetPlayer.sendMessage(promote
                        ? mm.deserialize("<#fcc850>Você foi promovido a <white>" + newRole.name() + "<#fcc850> no clan!")
                        : mm.deserialize("<#FF6B6B>Você foi rebaixado a <white>" + newRole.name() + "<#FF6B6B> no clan."));
            }
            cm.log(clan.getId(), player.getName(), promote ? "promover" : "rebaixar",
                    target.name() + " agora é " + newRole.name());
        } else {
            player.sendMessage(mm.deserialize("<#FF0000>Não foi possível alterar o cargo."));
        }
    }

    /** /clan anunciar <mensagem> — líder ou vice envia anúncio destacado para o clan. */
    private void handleAnunciar(Player player, ClanManager cm, String[] args) {
        if (args.length < 2) {
            player.sendMessage(mm.deserialize("<#FF0000>Uso: /clan anunciar <mensagem>"));
            return;
        }
        Clan clan = cm.getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não está em um clan!"));
            return;
        }
        // Líder ou cargo com permissão de expulsar (nível de gestão) podem anunciar.
        boolean isLeader = clan.getLeader().equals(player.getUniqueId());
        if (!isLeader && !cm.hasPermission(clan.getId(), player.getUniqueId(), "kick")) {
            player.sendMessage(mm.deserialize("<#FF0000>Apenas o líder ou cargos de gestão podem anunciar!"));
            return;
        }

        StringBuilder msg = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) msg.append(" ");
            msg.append(args[i]);
        }
        String plain = msg.toString().replace("<", "").replace(">", "");

        for (ClanMember member : clan.getMembers()) {
            Player online = Bukkit.getPlayer(member.uuid());
            if (online == null) continue;
            online.sendMessage(mm.deserialize(""));
            online.sendMessage(mm.deserialize("<#fcc850><bold>ANÚNCIO DO CLAN</bold> <gray>por <white>" + player.getName()));
            online.sendMessage(mm.deserialize("<white>" + plain));
            online.sendMessage(mm.deserialize(""));
        }
    }

    /** /clan descricao <texto> — só o líder define a descrição exibida no /clan info. */
    private void handleDescricao(Player player, ClanManager cm, String[] args) {
        Clan clan = cm.getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não está em um clan!"));
            return;
        }
        if (!clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(mm.deserialize("<#FF0000>Apenas o líder pode alterar a descrição!"));
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) sb.append(" ");
            sb.append(args[i]);
        }
        String desc = sb.toString().replace("<", "").replace(">", "").trim();
        if (desc.length() > 64) {
            player.sendMessage(mm.deserialize("<#FF0000>A descrição deve ter no máximo 64 caracteres!"));
            return;
        }
        if (cm.setClanDescription(clan.getId(), desc)) {
            player.sendMessage(desc.isEmpty()
                    ? mm.deserialize("<#10fc46>Descrição do clan removida.")
                    : mm.deserialize("<#10fc46>Descrição atualizada: <white>" + desc));
        } else {
            player.sendMessage(mm.deserialize("<#FF0000>Não foi possível alterar a descrição."));
        }
    }

    private void handleRival(Player player, ClanManager cm, String[] args) {
        if (args.length < 2) {
            player.sendMessage(mm.deserialize("<#FF0000>Uso: /clan rival <tag>"));
            return;
        }
        Clan clan = cm.getClanByPlayer(player.getUniqueId());
        if (clan == null || !clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(mm.deserialize("<#FF0000>Apenas o líder pode gerenciar rivais!"));
            return;
        }
        Clan rival = cm.getClanByTag(args[1]);
        if (rival == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Clan não encontrado."));
            return;
        }
        if (cm.addRival(clan.getId(), rival.getId())) {
            player.sendMessage(mm.deserialize("<#e22c27>Clan <white>[" + rival.getTag() + "]<#e22c27> agora é rival!"));
            cm.log(clan.getId(), player.getName(), "rival", "[" + rival.getTag() + "] marcado como rival");
        } else {
            player.sendMessage(mm.deserialize("<#FF0000>Não foi possível adicionar o rival."));
        }
    }

    private void handleUnrival(Player player, ClanManager cm, String[] args) {
        if (args.length < 2) {
            player.sendMessage(mm.deserialize("<#FF0000>Uso: /clan unrival <tag>"));
            return;
        }
        Clan clan = cm.getClanByPlayer(player.getUniqueId());
        if (clan == null || !clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(mm.deserialize("<#FF0000>Apenas o líder pode gerenciar rivais!"));
            return;
        }
        Clan rival = cm.getClanByTag(args[1]);
        if (rival == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Clan não encontrado."));
            return;
        }
        if (cm.removeRival(clan.getId(), rival.getId())) {
            player.sendMessage(mm.deserialize("<#10fc46>Rivalidade removida com <white>[" + rival.getTag() + "]<#10fc46>."));
            cm.log(clan.getId(), player.getName(), "unrival", "Rivalidade removida com [" + rival.getTag() + "]");
        } else {
            player.sendMessage(mm.deserialize("<#FF0000>Não foi possível remover a rivalidade."));
        }
    }

    private void handleInfo(Player player, ClanManager cm, String[] args) {
        Clan clan;
        if (args.length >= 2) {
            clan = cm.getClanByTag(args[1].toUpperCase());
        } else {
            clan = cm.getClanByPlayer(player.getUniqueId());
        }

        if (clan == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Clan não encontrado."));
            return;
        }

        player.sendMessage(mm.deserialize(""));
        player.sendMessage(mm.deserialize(ClanText.formatClanTag(clan.getColorHex(), clan.getTag()) + " <white>" + clan.getName()));
        if (!clan.getDescription().isEmpty()) {
            player.sendMessage(mm.deserialize("<gray><i>" + clan.getDescription() + "</i>"));
        }
        player.sendMessage(mm.deserialize("<gray>Líder: <white>" + Bukkit.getOfflinePlayer(clan.getLeader()).getName()));
        player.sendMessage(mm.deserialize("<gray>Membros: <white>" + clan.getMembers().size() + "/" + ClanGUI.getClanMemberLimit(clan)));
        long kills = plugin.getClanTopQueryService().getKills(clan.getId());
        player.sendMessage(mm.deserialize("<gray>Kills (soma dos membros): <white>" + kills));
        player.sendMessage(mm.deserialize(""));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = List.of("criar", "deletar", "aceitar", "sair", "convidar", "expulsar", "transferir",
                    "promover", "rebaixar", "anunciar", "descricao", "info", "ally", "unally", "rival", "unrival");
            String prefix = args[0].toLowerCase();
            for (String s : subs) {
                if (s.startsWith(prefix)) completions.add(s);
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("convidar") || sub.equals("expulsar") || sub.equals("transferir")
                    || sub.equals("promover") || sub.equals("rebaixar")) {
                String prefix = args[1].toLowerCase();
                Clan clan = null;
                if (sender instanceof Player p) clan = plugin.getClanManager().getClanByPlayer(p.getUniqueId());
                boolean membersOnly = !sub.equals("convidar");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getName().toLowerCase().startsWith(prefix)) continue;
                    if (membersOnly && (clan == null || clan.getMembers().stream().noneMatch(m -> m.uuid().equals(p.getUniqueId())))) {
                        continue;
                    }
                    completions.add(p.getName());
                }
            } else if (sub.equals("aceitar") || sub.equals("info")
                    || sub.equals("ally") || sub.equals("unally")
                    || sub.equals("rival") || sub.equals("unrival")) {
                String prefix = args[1].toUpperCase();
                for (Clan clan : plugin.getClanManager().getAllClans()) {
                    if (clan.getTag().startsWith(prefix)) completions.add(clan.getTag());
                }
            }
        }
        return completions;
    }
}