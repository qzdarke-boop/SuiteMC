package com.psdk;

// database
import com.psdk.chat.*;
import com.psdk.database.DatabaseManager;
// vip
import com.psdk.vip.VipManager;
import com.psdk.vip.commands.*;
import com.psdk.vip.listeners.ProxyMessageListener;
import com.psdk.vip.util.ProxyMessenger;
// economy
import com.psdk.economy.EconomyManager;
import com.psdk.economy.CoinsCommand;
import com.psdk.economy.TokensCommand;
import com.psdk.economy.EcoCommand;
// crates
import com.psdk.crates.CrateManager;
import com.psdk.crates.KeyManager;
import com.psdk.crates.HologramManager;
import com.psdk.crates.PlaytimeKeyManager;
import com.psdk.crates.CrateBlockListener;
import com.psdk.crates.CrateGUIListener;
import com.psdk.crates.HologramInteractListener;
import com.psdk.crates.KeyActivateListener;
import com.psdk.crates.CaixaCommand;
import com.psdk.crates.CaixasCommand;
import com.psdk.crates.SetCratesCommand;
import com.psdk.crates.CratesExpansion;
// thepit
import com.psdk.thepit.ArenaManager;
import com.psdk.thepit.PlayerDataManager;
import com.psdk.thepit.LevelManager;
import com.psdk.thepit.KitManager;
import com.psdk.thepit.KitLootManager;
import com.psdk.thepit.CombatManager;
import com.psdk.thepit.CombatInventorySaveManager;
import com.psdk.thepit.ArenaWandListener;
import com.psdk.thepit.BlockMineListener;
import com.psdk.thepit.ArenaProtectionListener;
import com.psdk.thepit.CombatCommandListener;
import com.psdk.thepit.CombatListener;
import com.psdk.thepit.PlayerSessionListener;
import com.psdk.thepit.ArenaCommand;
import com.psdk.thepit.KillsCommand;
import com.psdk.thepit.SetSpawnCommand;
import com.psdk.thepit.SetDeathCommand;
import com.psdk.thepit.SpawnCommand;
import com.psdk.thepit.StatsCommand;
import com.psdk.thepit.ThePitCommand;
import com.psdk.thepit.TutorialCommand;
import com.psdk.thepit.TntListener;
import com.psdk.thepit.TutorialGUIListener;
import com.psdk.thepit.ThePitExpansion;
// region
import com.psdk.region.RegionManager;
import com.psdk.region.RegionCommand;
import com.psdk.region.RegionProtectionListener;
import com.psdk.region.RegionWandListener;
// shop
import com.psdk.shop.ShopManager;
import com.psdk.shop.ShopGUIListener;
import com.psdk.shop.ShopCommand;
// colina
import com.psdk.colina.ColinaManager;
import com.psdk.colina.DonoColinaCommand;
import com.psdk.colina.RemoverColinaCommand;
import com.psdk.colina.SetarColinaCommand;
// afk
import com.psdk.afk.AfkManager;
import com.psdk.afk.AfkCommand;
import com.psdk.afk.SetAfkCommand;
import com.psdk.afk.AfkListener;
// pitems
import com.psdk.pitems.PSDKItemExpireTask;
import com.psdk.pitems.PSDKItems;
import com.psdk.pitems.PSDKItemListener;
import com.psdk.pitems.PItemCommand;
// lootchest
import com.psdk.lootchest.LootChestManager;
import com.psdk.lootchest.LootChestListener;
import com.psdk.lootchest.LootChestCommand;
import com.psdk.lixeiro.LixeiroManager;
import com.psdk.lixeiro.LixeiroCommand;
// util
import com.psdk.util.WandUtils;
import com.psdk.util.EnderChestCommand;
import com.psdk.util.EcSeeCommand;
import com.psdk.util.RedstoneDisableListener;
// social
import com.psdk.social.SociasCommand;
import com.psdk.social.DiscordCommand;
import com.psdk.social.marriage.MarriageManager;
import com.psdk.social.marriage.MarriageCommand;
import com.psdk.social.marriage.DivorceCommand;
import com.psdk.social.marriage.KissCommand;
import com.psdk.social.marriage.HugCommand;
import com.psdk.social.birthday.BirthdayManager;
import com.psdk.social.birthday.BirthdayCommand;
// chat
// settings
import com.psdk.settings.SettingsManager;
import com.psdk.settings.SettingsCommand;
import com.psdk.settings.SettingsGUIListener;
// clan
import com.psdk.clan.ClanManager;
import com.psdk.clan.ClanCommand;
import com.psdk.clan.ClanGUIListener;
import com.psdk.clan.ClanFriendlyFireListener;
import com.psdk.clan.ClanChatInputListener;
import com.psdk.clan.ClanChatCommand;
import com.psdk.clan.AllyChatCommand;
import com.psdk.clan.AllyCommand;
import com.psdk.clan.ColorManager;
import com.psdk.clan.ClanColorManager;
import com.psdk.clan.ColorPacketManager;
import com.psdk.clan.ClanAdminCommand;
import com.psdk.clan.ClanColorActivateListener;
// staff
import com.psdk.staff.StaffManager;
import com.psdk.staff.StaffCommand;
import com.psdk.staff.StaffCommandLogListener;
import com.psdk.staff.StaffHudCommand;
import com.psdk.staff.NetworkTrafficMonitor;
// tops
import com.psdk.thepit.BlockStatsListener;
import com.psdk.thepit.ResetKillsCommand;
import com.psdk.thepit.ResetCommand;
import com.psdk.thepit.TopsCommand;
import com.psdk.thepit.TopsGUIListener;
import com.psdk.thepit.topboard.TopBoardCommand;
import com.psdk.thepit.topboard.TopBoardListener;
import com.psdk.thepit.topboard.TopBoardManager;
import com.psdk.thepit.topboard.TopBoardPlayerListener;
import com.psdk.thepit.topboard.TopQueryService;
import com.psdk.thepit.topboard.TopStatsTracker;
// vault
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.logging.Level;

public final class PSDK extends JavaPlugin {

    private static PSDK instance;

    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private com.psdk.thepit.LaunchResetListener launchResetListener;
    private Economy economy;
    private CrateManager crateManager;
    private KeyManager keyManager;
    private HologramManager hologramManager;
    private PlaytimeKeyManager playtimeKeyManager;
    private PlayerDataManager playerDataManager;
    private KitLootManager kitLootManager;
    private LevelManager levelManager;
    private ArenaManager arenaManager;
    private KitManager kitManager;
    private CombatManager combatManager;
    private CombatInventorySaveManager combatInventorySaveManager;
    private RegionManager regionManager;
    private com.psdk.cage.CageManager cageManager;
    private com.psdk.pitems.AbilityCooldownManager abilityCooldownManager;
    private com.psdk.thepit.ReconnectManager reconnectManager;
    private ShopManager shopManager;
    private ColinaManager colinaManager;
    private com.psdk.social.DiscordNpcManager discordNpcManager;
    private AfkManager afkManager;
    private ShopGUIListener shopGUIListener;
    private ChatManager chatManager;
    private SpyCommand spyCommand;
    private SettingsManager settingsManager;
    private ClanManager clanManager;
    private ClanChatInputListener clanChatInputListener;
    private ColorManager colorManager;
    private ClanColorManager clanColorManager;
    private com.psdk.clan.ClanTopQueryService clanTopQueryService;
    private ColorPacketManager colorPacketManager;
    private StaffManager staffManager;
    private VipManager vipManager;
    private LootChestManager lootChestManager;
    private LixeiroManager lixeiroManager;
    private com.psdk.boss.BossManager bossManager;
    private com.psdk.boss.BossArenaManager bossArenaManager;
    private com.psdk.adminabuse.AdminAbuseManager adminAbuseManager;
    private com.psdk.adminabuse.AdminAbuseScheduler adminAbuseScheduler;
    private com.psdk.ec.EnderChestManager enderChestManager;
    private com.psdk.kits.KitCooldownManager kitCooldownManager;
    private MarriageManager marriageManager;
    private BirthdayManager birthdayManager;
    private com.psdk.bounty.BountyManager bountyManager;
    private TopStatsTracker topStatsTracker;
    private TopQueryService topQueryService;
    private TopBoardManager topBoardManager;
    private Location cratesSpawn;
    private Location spawnLocation;
    private Location deathSpawnLocation;

    @Override
    public void onEnable() {
        instance = this;

        try {
            vipManager = new VipManager(this);
        } catch (Exception e) {
            getLogger().warning("VipManager não carregado (LuckPerms ausente?): " + e.getMessage());
        }

        databaseManager = new DatabaseManager(this);
        if (!databaseManager.initialize()) {
            getLogger().severe("Falha ao inicializar banco de dados! Desabilitando plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Backup automático do banco a cada boot (mantém os 10 mais recentes em
        // plugins/PSDK/backups/). Roda em segundo plano pra não atrasar o start.
        getServer().getScheduler().runTaskAsynchronously(this, () -> databaseManager.backupDatabase(10));

        topStatsTracker = new TopStatsTracker(this);
        economyManager = new EconomyManager(this);
        setupEconomy();

        WandUtils.init(this);
        PSDKItems.init();

        crateManager = new CrateManager(this);
        crateManager.loadAll();

        keyManager = new KeyManager(this);
        playtimeKeyManager = new PlaytimeKeyManager(this);

        hologramManager = new HologramManager(this, crateManager);
        hologramManager.spawnAll();

        playerDataManager = new PlayerDataManager(this);
        kitLootManager = new KitLootManager(this);
        levelManager = new LevelManager(this);
        arenaManager = new ArenaManager(this);
        kitManager = new KitManager(this);
        combatManager = new CombatManager(this);
        combatInventorySaveManager = new CombatInventorySaveManager(this);
        regionManager = new RegionManager(this);
        shopManager = new ShopManager();
        colinaManager = new ColinaManager(this);
        getServer().getPluginManager().registerEvents(colinaManager, this);   // grace de morte na colina
        discordNpcManager = new com.psdk.social.DiscordNpcManager(this);
        discordNpcManager.load();                                             // NPC do Discord (Wumpus)
        afkManager = new AfkManager(this);

        arenaManager.loadArena();
        loadCratesSpawn();
        loadSpawnLocation();
        loadDeathSpawnLocation();

        // Jaula (item especial): recupera blocos deixados por crash/reload antes de operar.
        cageManager = new com.psdk.cage.CageManager(this);
        cageManager.recoverFromDatabase();

        // Cooldowns centralizados dos itens especiais (padrão visual do escudo + persistência).
        abilityCooldownManager = new com.psdk.pitems.AbilityCooldownManager(this);
        // Restauração de posição na reconexão (arena de PvP sem Combat Log).
        reconnectManager = new com.psdk.thepit.ReconnectManager(this);

        chatManager = new ChatManager();
        spyCommand = new SpyCommand();
        settingsManager = new SettingsManager(this);
        clanManager = new ClanManager(this);
        clanChatInputListener = new ClanChatInputListener(this);
        colorManager = new ColorManager(this);
        colorManager.loadAll();
        clanColorManager = new ClanColorManager();
        colorPacketManager = new ColorPacketManager(this);
        staffManager = new StaffManager(this);

        lootChestManager = new LootChestManager(this);
        lixeiroManager = new LixeiroManager(this);
        bossManager = new com.psdk.boss.BossManager(this);
        bossArenaManager = new com.psdk.boss.BossArenaManager(this);
        adminAbuseManager   = new com.psdk.adminabuse.AdminAbuseManager(this);
        adminAbuseScheduler = new com.psdk.adminabuse.AdminAbuseScheduler(this);
        enderChestManager   = new com.psdk.ec.EnderChestManager(this);
        kitCooldownManager  = new com.psdk.kits.KitCooldownManager(this);
        marriageManager     = new MarriageManager(this);
        birthdayManager     = new BirthdayManager(this);
        bountyManager       = new com.psdk.bounty.BountyManager(this);
        topQueryService     = new TopQueryService(this);
        topBoardManager     = new TopBoardManager(this, topQueryService);
        clanTopQueryService = new com.psdk.clan.ClanTopQueryService(this);
        getLogger().info("[Clan] GUI completa — Relações, Tesouro, Tops, Cargos e Logs (v"
                + getDescription().getVersion() + ")");

        // HUD de staff (TPS/MSPT/CPU/Heap/Net/Online/Ping).
        NetworkTrafficMonitor netMonitor = new NetworkTrafficMonitor(this);
        getServer().getPluginManager().registerEvents(netMonitor, this);
        netMonitor.injectOnline();
        registerCmd("staffhud", new StaffHudCommand(this, netMonitor));

        registerCommands();
        registerListeners();
        startTasks();
        registerVip();
        registerSpeakBridge();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new CratesExpansion(this).register();
            new ThePitExpansion(this).register();
            new com.psdk.clan.ClanExpansion(this).register();
            getLogger().info("PlaceholderAPI integrado.");
        }

        if (launchResetListener != null) launchResetListener.logArmedStatus();

        getLogger().info("PSDK v" + getDescription().getVersion() + " iniciado com sucesso!");
    }

    @Override
    public void onDisable() {
        // Salva dados ANTES de cancelar tasks e limpar entidades — evita perda.
        // Shulker portátil aberto: devolve à mão/inventário antes do save do .dat
        // (os listeners somem antes do disconnect, então o onClose não rodaria).
        com.psdk.util.PortableShulkerListener.saveAllOpen();
        if (playtimeKeyManager != null) playtimeKeyManager.saveAll();
        if (topStatsTracker != null) topStatsTracker.flushAll();
        if (combatInventorySaveManager != null) combatInventorySaveManager.saveAllEmergency();
        if (playerDataManager != null) playerDataManager.saveAllPlayers();
        com.psdk.ec.EnderChestGUIListener.saveAllOpen();

        if (vipManager != null) vipManager.shutdown();
        if (bossManager != null) bossManager.despawn();
        if (discordNpcManager != null) discordNpcManager.despawn();
        if (adminAbuseManager != null) adminAbuseManager.stop();
        if (lootChestManager != null) lootChestManager.clearAll();
        if (topBoardManager != null) topBoardManager.despawnAll();
        if (cageManager != null) cageManager.shutdown(); // restaura vidros + limpa DB antes de fechar
        if (abilityCooldownManager != null) abilityCooldownManager.shutdown(); // persiste cooldowns ativos
        if (hologramManager != null) hologramManager.despawnAll();
        if (combatInventorySaveManager != null) combatInventorySaveManager.markCleanShutdown();
        getServer().getScheduler().cancelTasks(this);
        if (economyManager != null) economyManager.flushAll(); // grava saldos pendentes (write-behind)
        if (databaseManager != null) databaseManager.close();
    }

    private void registerCommands() {
        registerCmd("caixa",  new CaixaCommand(this));
        registerCmd("arena",  new ArenaCommand(this));
        registerCmd("thepit", new ThePitCommand(this));
        registerCmd("spawn",  new SpawnCommand(this));
        registerCmd("setspawn", new SetSpawnCommand(this));
        registerCmd("tokens",  new TokensCommand(this));
        registerCmd("coins",  new CoinsCommand(this));
        registerCmd("eco",    new EcoCommand(this));
        registerCmd("pay",    new com.psdk.economy.PayCommand(this));
        registerCmd("bounty",  new com.psdk.bounty.BountyCommand(this));
        registerCmd("stats",  new StatsCommand(this));
        registerCmd("kills",  new KillsCommand(this));
        registerCmd("regiao", new RegionCommand(this));
        registerCmd("pitem",  new PItemCommand(this));
        registerCmd("shop",   new ShopCommand(this));
        registerCmd("setarcolina", new SetarColinaCommand(this));
        registerCmd("removercolina", new RemoverColinaCommand(this));
        registerCmd("donocolina", new DonoColinaCommand(this));
        registerCmd("caixas", new CaixasCommand(this));
        registerCmd("setcrates", new SetCratesCommand(this));
        registerCmd("tutorial", new TutorialCommand(this));
        registerCmd("afk", new AfkCommand(this));
        registerCmd("setafk", new SetAfkCommand(this));
        registerCmd("setdeath", new SetDeathCommand(this));
        registerCmd("tell", new TellCommand(spyCommand));
        registerCmd("reply", new TellCommand(spyCommand));
        registerCmd("say", new SayCommand(this));
        registerCmd("saytoggle", new SayToggleCommand(this));
        registerCmd("chatclear", new ChatClearCommand(this));
        registerCmd("lastmsg", new LastMsgCommand(this));
        registerCmd("lockchat", new LockCommand(this));
        registerCmd("openchat", new OpenCommand(this));
        registerCmd("spy", spyCommand);
        registerCmd("anunciar", new AnnounceCommand(this));
        registerCmd("settings", new SettingsCommand(this));
        registerCmd("clan", new ClanCommand(this));
        registerCmd("c",    new ClanChatCommand(this));
        registerCmd("a",    new AllyChatCommand(this));
        registerCmd("ally", new AllyCommand(this));
        registerCmd("clanAdmin", new ClanAdminCommand(this));
        registerCmd("staff", new StaffCommand(this));
        registerCmd("sc", new com.psdk.staff.StaffChatCommand());
        registerCmd("ec", new EnderChestCommand(this));
        registerCmd("kit", new com.psdk.kits.KitCommand());
        com.psdk.util.EcSeeCommand ecSee = new com.psdk.util.EcSeeCommand(this);
        registerCmd("ecsee", ecSee);
        getServer().getPluginManager().registerEvents(ecSee, this);   // listener que salva a EC editada (online/offline)
        com.psdk.staff.InvSeeCommand invSee = new com.psdk.staff.InvSeeCommand(this);
        registerCmd("invsee", invSee);
        getServer().getPluginManager().registerEvents(new com.psdk.staff.InvSeeListener(this), this);
        registerCmd("socias", new SociasCommand());
        registerCmd("discord", new DiscordCommand());
        registerCmd("spawnnpcdiscord", new com.psdk.social.SpawnNpcDiscordCommand(this));
        registerCmd("live", new com.psdk.social.LiveCommand(this));
        registerCmd("baus", new LootChestCommand(this));
        registerCmd("lixeiro", new LixeiroCommand(this));
        registerCmd("boss", new com.psdk.boss.BossCommand(this));
        registerCmd("pboss", new com.psdk.boss.PBossCommand(this));
        com.psdk.boss.BossSetCommand bossSetCmd = new com.psdk.boss.BossSetCommand(this);
        registerCmd("setbossarena", bossSetCmd);
        registerCmd("setbossspawn", bossSetCmd);
        registerCmd("bossarena", new com.psdk.boss.BossArenaCommand(this));
        registerCmd("adminabuse", new com.psdk.adminabuse.AdminAbuseCommand(this));

        // Casamento
        registerCmd("casar",    new MarriageCommand(this));
        registerCmd("divorcio", new DivorceCommand(this));
        registerCmd("beijar",   new KissCommand(this));
        registerCmd("abracar",  new HugCommand(this));
        registerCmd("aniversariante", new BirthdayCommand(this));

        // Resets de playerdata
        registerCmd("resetkills",  new ResetKillsCommand(this));
        registerCmd("resetplayer", new ResetCommand(this));

        registerCmd("tops", new TopsCommand(this));
        TopBoardCommand topBoardCommand = new TopBoardCommand(this);
        registerCmd("settopboard", topBoardCommand);
        registerCmd("removetopboard", topBoardCommand);
        registerCmd("topboard", topBoardCommand);
    }

    private void registerCmd(String name, Object handler) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) return;
        if (handler instanceof org.bukkit.command.CommandExecutor exec) cmd.setExecutor(exec);
        if (handler instanceof org.bukkit.command.TabCompleter tab) cmd.setTabCompleter(tab);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new KeyActivateListener(this), this);
        getServer().getPluginManager().registerEvents(new CrateBlockListener(this), this);
        getServer().getPluginManager().registerEvents(new HologramInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new CrateGUIListener(this), this);
        getServer().getPluginManager().registerEvents(playtimeKeyManager, this);

        getServer().getPluginManager().registerEvents(new PlayerSessionListener(this), this);
        launchResetListener = new com.psdk.thepit.LaunchResetListener(this);
        getServer().getPluginManager().registerEvents(launchResetListener, this);
        getServer().getPluginManager().registerEvents(new BlockMineListener(this), this);
        getServer().getPluginManager().registerEvents(new ArenaProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockStatsListener(this), this);
        getServer().getPluginManager().registerEvents(new TntListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatCommandListener(this), this);
        getServer().getPluginManager().registerEvents(new ArenaWandListener(this), this);
        getServer().getPluginManager().registerEvents(new TutorialGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new AfkListener(this), this);

        getServer().getPluginManager().registerEvents(new PSDKItemListener(this), this);
        getServer().getPluginManager().registerEvents(new com.psdk.pitems.TrocaPosicaoListener(this), this);
        getServer().getPluginManager().registerEvents(new com.psdk.cage.CageListener(this), this);
        getServer().getPluginManager().registerEvents(new LootChestListener(this), this);
        getServer().getPluginManager().registerEvents(new RegionWandListener(this), this);
        getServer().getPluginManager().registerEvents(new RegionProtectionListener(this), this);

        shopGUIListener = new ShopGUIListener(this);
        getServer().getPluginManager().registerEvents(shopGUIListener, this);
        getServer().getPluginManager().registerEvents(new com.psdk.shop.SafeTntListener(this), this);

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinLeaveListener(this), this);
        getServer().getPluginManager().registerEvents(new SettingsGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new ClanGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new ClanFriendlyFireListener(this), this);
        getServer().getPluginManager().registerEvents(clanChatInputListener, this);
        getServer().getPluginManager().registerEvents(new ClanColorActivateListener(this), this);
        getServer().getPluginManager().registerEvents(new StaffCommandLogListener(this), this);
        getServer().getPluginManager().registerEvents(new com.psdk.staff.LastCmdGUIListener(this), this); // paginação do /staff lastcmd
        getServer().getPluginManager().registerEvents(new com.psdk.staff.PromoterRestrictionListener(this), this);   // trava abuso do cargo promoter

        // Redstone desativada globalmente.
        getServer().getPluginManager().registerEvents(new RedstoneDisableListener(), this);
        // Portais do Nether e do End desativados.
        getServer().getPluginManager().registerEvents(new com.psdk.util.PortalDisableListener(), this);
        getServer().getPluginManager().registerEvents(new com.psdk.util.PortableShulkerListener(), this);
        getServer().getPluginManager().registerEvents(new com.psdk.social.DiscordNpcListener(this), this);
        getServer().getPluginManager().registerEvents(bossManager, this);
        getServer().getPluginManager().registerEvents(bossArenaManager, this);
        getServer().getPluginManager().registerEvents(adminAbuseManager, this);
        getServer().getPluginManager().registerEvents(new com.psdk.adminabuse.AdminAbuseGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new com.psdk.adminabuse.AdminAbuseItemListener(this), this);
        getServer().getPluginManager().registerEvents(new com.psdk.ec.EnderChestGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new com.psdk.kits.KitsGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new com.psdk.bounty.BountyGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new TopBoardListener(this), this);
        getServer().getPluginManager().registerEvents(new TopBoardPlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new TopsGUIListener(), this);
    }

    private void startTasks() {
        new PSDKItemExpireTask(this).start();
        playtimeKeyManager.startTask();

        // Baús de loot: limpa displays órfãos de um restart e inicia os timers de spawn.
        lootChestManager.clearOrphans();
        lootChestManager.startSchedulers();

        // Lixeiro (limpa itens do chão) e regen automático da arena.
        lixeiroManager.start();
        arenaManager.startAutoReset();

        topBoardManager.loadAll();
        topBoardManager.spawnAll();

        // Mensagens automáticas (Discord + lembrete de /report). Cancelada no onDisable
        // pelo próprio Bukkit — sem duplicação após reload.
        if (com.psdk.chat.AnnouncementManager.ENABLED) {
            new com.psdk.chat.AnnouncementManager(this).runTaskTimer(this,
                    com.psdk.chat.AnnouncementManager.INTERVAL_TICKS,
                    com.psdk.chat.AnnouncementManager.INTERVAL_TICKS);
        }

        startPlayerAutosave();
    }

    /**
     * Autosave periódico do .dat de todos os jogadores online. Limita a janela de perda
     * em caso de crash/restart abrupto (inclusive de quem está em combate): mesmo sem
     * fechar limpo, no máximo ~30s de progresso de inventário se perdem. Escalonado: salva
     * alguns jogadores por tick em vez de todos de uma vez, pra não causar travadas de I/O.
     */
    private void startPlayerAutosave() {
        final int INTERVAL_TICKS = 600; // ~30s
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override public void run() {
                java.util.List<org.bukkit.entity.Player> online =
                        new java.util.ArrayList<>(getServer().getOnlinePlayers());
                if (online.isEmpty()) return;
                // Distribui os saves ao longo de ~1s (20 ticks) pra suavizar o I/O.
                int perBatch = Math.max(1, (int) Math.ceil(online.size() / 20.0));
                for (int b = 0; b < online.size(); b += perBatch) {
                    final int start = b;
                    final int end = Math.min(online.size(), b + perBatch);
                    getServer().getScheduler().runTaskLater(PSDK.this, () -> {
                        for (int i = start; i < end; i++) {
                            org.bukkit.entity.Player p = online.get(i);
                            if (p.isOnline()) {
                                try { p.saveData(); } catch (Throwable ignored) {}
                            }
                        }
                    }, (b / perBatch));
                }
            }
        }.runTaskTimer(this, INTERVAL_TICKS, INTERVAL_TICKS);
    }

    private void registerVip() {
        if (vipManager == null) return;

        getServer().getMessenger().registerOutgoingPluginChannel(this, ProxyMessenger.CHANNEL_ANNOUNCE);

        SetVipCommand setVipCmd = new SetVipCommand(this);
        ProxyMessageListener proxyListener = new ProxyMessageListener(this, setVipCmd);
        getServer().getMessenger().registerIncomingPluginChannel(this, ProxyMessenger.CHANNEL_ANNOUNCE, proxyListener);
        getServer().getMessenger().registerIncomingPluginChannel(this, ProxyMessenger.CHANNEL_COMMAND, proxyListener);

        registerCmd("setvip",    setVipCmd);
        registerCmd("removevip", new RemoveVipCommand(this));
        registerCmd("viptime",   new VipTimeCommand(this));
        registerCmd("gg",        new GGCommand(this));

        getLogger().info("VipManager (FrostyAnnounce) carregado.");
    }

    /**
     * Registra o canal de plugin-message do /speak (proxy VelocityCore).
     * O /speak e' registrado na proxy; aqui so respondemos ao pedido de
     * formatacao, mantendo a mesma aparencia do /say (ver {@link com.psdk.chat.SpeakBridge}).
     */
    private void registerSpeakBridge() {
        getServer().getMessenger().registerOutgoingPluginChannel(this, com.psdk.chat.SpeakBridge.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, com.psdk.chat.SpeakBridge.CHANNEL,
                new com.psdk.chat.SpeakBridge(this));
        getLogger().info("Ponte do /speak registrada (canal " + com.psdk.chat.SpeakBridge.CHANNEL + ").");
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            economy = rsp.getProvider();
            if (economy != null) getLogger().info("Vault/Economy integrado.");
        }
    }

    public static PSDK getInstance() { return instance; }
    public VipManager getVipManager() { return vipManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public com.psdk.thepit.LaunchResetListener getLaunchResetListener() { return launchResetListener; }
    public Economy getEconomy() { return economy; }
    public boolean hasEconomy() { return economy != null; }
    public CrateManager getCrateManager() { return crateManager; }
    public KeyManager getKeyManager() { return keyManager; }
    public HologramManager getHologramManager() { return hologramManager; }
    public ArenaManager getArenaManager() { return arenaManager; }
    public LevelManager getLevelManager() { return levelManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public KitManager getKitManager() { return kitManager; }
    public KitLootManager getKitLootManager() { return kitLootManager; }
    public CombatManager getCombatManager() { return combatManager; }
    public CombatInventorySaveManager getCombatInventorySaveManager() { return combatInventorySaveManager; }
    public RegionManager getRegionManager() { return regionManager; }
    public com.psdk.cage.CageManager getCageManager() { return cageManager; }
    public com.psdk.pitems.AbilityCooldownManager getAbilityCooldownManager() { return abilityCooldownManager; }
    public com.psdk.thepit.ReconnectManager getReconnectManager() { return reconnectManager; }
    public ShopManager getShopManager() { return shopManager; }
    public ColinaManager getColinaManager() { return colinaManager; }
    public com.psdk.social.DiscordNpcManager getDiscordNpcManager() { return discordNpcManager; }
    public AfkManager getAfkManager() { return afkManager; }
    public ShopGUIListener getShopGUIListener() { return shopGUIListener; }
    public ChatManager getChatManager() { return chatManager; }
    public SettingsManager getSettingsManager() { return settingsManager; }
    public ClanManager getClanManager() { return clanManager; }
    public ClanChatInputListener getClanChatInputListener() { return clanChatInputListener; }
    public ColorManager getColorManager() { return colorManager; }

    public ClanColorManager getClanColorManager() { return clanColorManager; }
    public com.psdk.clan.ClanTopQueryService getClanTopQueryService() { return clanTopQueryService; }
    public ColorPacketManager getColorPacketManager() { return colorPacketManager; }
    public StaffManager getStaffManager() { return staffManager; }
    public LootChestManager getLootChestManager() { return lootChestManager; }
    public LixeiroManager getLixeiroManager() { return lixeiroManager; }
    public com.psdk.boss.BossManager getBossManager() { return bossManager; }
    public com.psdk.boss.BossArenaManager getBossArenaManager() { return bossArenaManager; }
    public com.psdk.adminabuse.AdminAbuseManager getAdminAbuseManager() { return adminAbuseManager; }
    public com.psdk.adminabuse.AdminAbuseScheduler getAdminAbuseScheduler() { return adminAbuseScheduler; }
    public com.psdk.ec.EnderChestManager getEnderChestManager()      { return enderChestManager; }
    public com.psdk.kits.KitCooldownManager getKitCooldownManager() { return kitCooldownManager; }
    public MarriageManager getMarriageManager() { return marriageManager; }
    public BirthdayManager getBirthdayManager() { return birthdayManager; }
    public com.psdk.bounty.BountyManager getBountyManager() { return bountyManager; }
    public TopStatsTracker getTopStatsTracker() { return topStatsTracker; }
    public TopQueryService getTopQueryService() { return topQueryService; }
    public TopBoardManager getTopBoardManager() { return topBoardManager; }

    public Location getCratesSpawn() { return cratesSpawn; }
    public Location getSpawnLocation() { return spawnLocation; }
    public Location getDeathSpawnLocation() { return deathSpawnLocation; }

    /**
     * True se o mundo pertence ao Skill Pit — derivado das âncoras oficiais do projeto
     * (spawn, death-spawn, crates, arena principal e arena/spawn do Boss). Centraliza a
     * definição de "mundos controlados pelo Skill Pit" para proteções globais (ex.: bloqueio
     * de coleta de água/lava), sem afetar mundos externos que compartilhem a mesma instância.
     */
    public boolean isSkillPitWorld(org.bukkit.World world) {
        if (world == null) return false;
        if (matchesWorld(world, spawnLocation)) return true;
        if (matchesWorld(world, deathSpawnLocation)) return true;
        if (matchesWorld(world, cratesSpawn)) return true;
        if (arenaManager != null && world.equals(arenaManager.getCachedWorld())) return true;
        if (bossManager != null) {
            if (matchesWorld(world, bossManager.getArena())) return true;
            if (matchesWorld(world, bossManager.getBossSpawn())) return true;
        }
        return false;
    }

    private static boolean matchesWorld(org.bukkit.World world, Location loc) {
        return loc != null && world.equals(loc.getWorld());
    }

    public void setSpawnLocation(Location loc) {
        this.spawnLocation = loc;
        try (PreparedStatement ps = databaseManager.getConnection().prepareStatement(
                "INSERT OR REPLACE INTO settings (key, value) VALUES ('spawn_location', ?)")) {
            ps.setString(1, loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," +
                    loc.getZ() + "," + loc.getYaw() + "," + loc.getPitch());
            ps.executeUpdate();
        } catch (SQLException e) {
            getLogger().log(Level.WARNING, "Erro ao salvar spawn location", e);
        }
    }

    public void setDeathSpawnLocation(Location loc) {
        this.deathSpawnLocation = loc;
        try (PreparedStatement ps = databaseManager.getConnection().prepareStatement(
                "INSERT OR REPLACE INTO settings (key, value) VALUES ('death_spawn_location', ?)")) {
            ps.setString(1, loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," +
                    loc.getZ() + "," + loc.getYaw() + "," + loc.getPitch());
            ps.executeUpdate();
        } catch (SQLException e) {
            getLogger().log(Level.WARNING, "Erro ao salvar death spawn location", e);
        }
    }

    public void setCratesSpawn(Location loc) {
        this.cratesSpawn = loc;
        try (PreparedStatement ps = databaseManager.getConnection().prepareStatement(
                "INSERT OR REPLACE INTO settings (key, value) VALUES ('crates_spawn', ?)")) {
            ps.setString(1, loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," +
                    loc.getZ() + "," + loc.getYaw() + "," + loc.getPitch());
            ps.executeUpdate();
        } catch (SQLException e) {
            getLogger().log(Level.WARNING, "Erro ao salvar crates spawn", e);
        }
    }

    private void loadCratesSpawn() {
        try (PreparedStatement ps = databaseManager.getConnection().prepareStatement(
                "SELECT value FROM settings WHERE key = 'crates_spawn'")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String[] p = rs.getString("value").split(",");
                if (p.length >= 6) {
                    World w = Bukkit.getWorld(p[0]);
                    if (w != null) {
                        cratesSpawn = new Location(w,
                                Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                                Float.parseFloat(p[4]), Float.parseFloat(p[5]));
                    }
                }
            }
        } catch (SQLException e) {
            getLogger().log(Level.WARNING, "Erro ao carregar crates spawn", e);
        }
    }

    private void loadSpawnLocation() {
        try (PreparedStatement ps = databaseManager.getConnection().prepareStatement(
                "SELECT value FROM settings WHERE key = 'spawn_location'")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String[] p = rs.getString("value").split(",");
                if (p.length >= 6) {
                    World w = Bukkit.getWorld(p[0]);
                    if (w != null) {
                        spawnLocation = new Location(w,
                                Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                                Float.parseFloat(p[4]), Float.parseFloat(p[5]));
                    }
                }
            }
        } catch (SQLException e) {
            getLogger().log(Level.WARNING, "Erro ao carregar spawn location", e);
        }
    }

    private void loadDeathSpawnLocation() {
        try (PreparedStatement ps = databaseManager.getConnection().prepareStatement(
                "SELECT value FROM settings WHERE key = 'death_spawn_location'")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String[] p = rs.getString("value").split(",");
                if (p.length >= 6) {
                    World w = Bukkit.getWorld(p[0]);
                    if (w != null) {
                        deathSpawnLocation = new Location(w,
                                Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                                Float.parseFloat(p[4]), Float.parseFloat(p[5]));
                    }
                }
            }
        } catch (SQLException e) {
            getLogger().log(Level.WARNING, "Erro ao carregar death spawn location", e);
        }
    }
}