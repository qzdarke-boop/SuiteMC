package com.psdk.thepit;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ThePitCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public ThePitCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("psdk.thepit")) {
            sender.sendMessage(mm.deserialize("<#FF0000>Você não tem permissão para isso."));
            return true;
        }
        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "resetkillsall" -> handleResetKillsAll(sender);
            case "reset"         -> handleReset(sender);
            case "resetall"      -> handleResetAll(sender, args);
            case "resetstatus"   -> handleResetStatus(sender);
            case "colocaritem"   -> handleColocarItem(sender, args);
            case "removeritem"   -> handleRemoverItem(sender, args);
            case "restoreinfo"    -> handleRestoreInfo(sender, args);
            default              -> sendHelp(sender);
        }
        return true;
    }

    /**
     * RESET TOTAL para lançamento. Apaga ABSOLUTAMENTE TUDO de TODOS os jogadores
     * (online + offline): inventário, ender chest, coins, tokens, kills, mortes,
     * level/xp, chaves de caixas, cooldowns de kit, bounties, casamentos, clãs e
     * cores. Mantém apenas a configuração do servidor (caixas, regiões, arena,
     * loot de kit, etc).
     *
     * Fluxo: kicka todos -> espera os saves de saída -> limpa o banco e apaga os
     * arquivos .dat de todos os jogadores -> pede restart pra recarregar os caches.
     */
    private void handleResetAll(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirmar")) {
            sender.sendMessage(mm.deserialize(""));
            sender.sendMessage(mm.deserialize("<#FF0000><bold>⚠ RESET TOTAL ⚠"));
            sender.sendMessage(mm.deserialize("<#ffffff>Isso apaga <#FF0000>PERMANENTEMENTE<#ffffff> de TODOS (online e offline):"));
            sender.sendMessage(mm.deserialize("<#a4a4a4>• Inventário, ender chest e itens"));
            sender.sendMessage(mm.deserialize("<#a4a4a4>• Coins, tokens, kills, mortes, level e xp"));
            sender.sendMessage(mm.deserialize("<#a4a4a4>• Chaves de caixas, cooldowns de kit e progresso de playtime"));
            sender.sendMessage(mm.deserialize("<#a4a4a4>• Bounties, casamentos, clãs e cores desbloqueadas"));
            sender.sendMessage(mm.deserialize("<#ffffff>Mantém a config do servidor (caixas, regiões, arena, loot)."));
            sender.sendMessage(mm.deserialize("<#fcc850>Todos serão desconectados. REINICIE o servidor logo após o reset."));
            sender.sendMessage(mm.deserialize("<#10fc46>Digite <#fcc850>/thepit resetall confirmar <#10fc46>para executar."));
            sender.sendMessage(mm.deserialize(""));
            return;
        }

        // 1) Desconecta todos (evita que autosave de saída regrave dados já limpos).
        for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            p.getInventory().clear();
            p.getInventory().setArmorContents(new ItemStack[4]);
            p.getInventory().setItemInOffHand(null);
            p.getEnderChest().clear();
            p.kick(mm.deserialize("<#FF0000><bold>RESET TOTAL\n<#ffffff>O servidor foi resetado para o lançamento."));
        }

        sender.sendMessage(mm.deserialize("<#fcc850>Iniciando reset total... aguardando saves de saída (3s)."));

        // 2) Espera ~3s pros saves de saída terminarem, então nuke tudo.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // (main thread) banco é rápido + limpeza dos caches em memória que, senão,
            // manteriam dados de quem estava online (cooldowns de kit VIP/premium,
            // saldo de chaves e saldo de economia).
            // Limpa caches/filas ANTES do wipe para que o flush assíncrono não regrave
            // saldos antigos por cima do banco recém-limpo.
            plugin.getEconomyManager().clearCache();
            plugin.getCrateManager().clearSaldoCache();
            int wipedTables = wipeDatabase(sender);
            plugin.getKitCooldownManager().clearAll();

            // Inventário/ender chest/xp VANILLA vivem no NBT (.dat), não no banco.
            // ABORDAGEM HÍBRIDA (à prova de falhas):
            //  1) Marca um "epoch" de reset — rede de segurança que zera QUALQUER jogador
            //     no próximo login (cobre quem reconectar na janela ou cujo .dat falhar).
            //  2) Apaga FISICAMENTE os .dat de todos (agora é seguro: todos foram kickados
            //     e esperamos os saves). Isso zera offline IMEDIATAMENTE, mesmo quem nunca
            //     mais voltar.
            plugin.getLaunchResetListener().markResetNow();
            int deletedFiles = deletePlayerDataFiles();

            plugin.getLogger().info("[RESET] Reset total por " + sender.getName()
                    + " — " + wipedTables + " tabelas limpas, " + deletedFiles
                    + " arquivos .dat apagados. Backstop de login ARMADO.");
            sender.sendMessage(mm.deserialize("<#10fc46>RESET TOTAL concluído! <#fcc850>" + wipedTables
                    + " <#10fc46>tabelas limpas, <#fcc850>" + deletedFiles + " <#10fc46>perfis apagados."));
            sender.sendMessage(mm.deserialize("<#10fc46>Inventário, ender chest e xp de <#fcc850>TODOS"
                    + " <#10fc46>(online, offline e futuros) zerados."));
            sender.sendMessage(mm.deserialize("<#FF0000><bold>IMPORTANTE: <#ffffff>reinicie o "
                    + "servidor agora (<#fcc850>/stop<#ffffff>) para recarregar tudo limpo."));
        }, 60L);
    }

    /** Mostra se o reset-no-login está armado e quem ainda falta limpar (online). */
    private void handleResetStatus(CommandSender sender) {
        long epoch = plugin.getLaunchResetListener().currentEpoch();
        if (epoch <= 0) {
            sender.sendMessage(mm.deserialize("<#fcc850>Nenhum reset de lançamento pendente (epoch=0)."));
            return;
        }
        int pending = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.getLaunchResetListener().isPending(p, epoch)) pending++;
        }
        sender.sendMessage(mm.deserialize("<#10fc46>Reset de lançamento ARMADO <#a4a4a4>(epoch=" + epoch + ")."));
        sender.sendMessage(mm.deserialize("<#ffffff>Jogadores online ainda pendentes de limpeza: <#fcc850>" + pending));
        sender.sendMessage(mm.deserialize("<#a4a4a4>Offline e futuros serão zerados quando entrarem."));
    }

    /** Limpa todas as tabelas de DADOS DE JOGADOR (mantém config do servidor). */
    private int wipeDatabase(CommandSender sender) {
        String[] wipes = {
                "DELETE FROM crate_keys",
                "DELETE FROM player_data",
                "DELETE FROM player_economy",
                "DELETE FROM player_settings",
                "DELETE FROM player_ec_extended",
                "DELETE FROM kit_cooldowns",
                "DELETE FROM marriage_remarry_cooldown",
                "DELETE FROM marriages",
                "DELETE FROM playtime_key_rewards",
                "DELETE FROM bounties",
                "DELETE FROM clan_player_colors",
                "DELETE FROM clan_activated_colors",
                "DELETE FROM clan_chest",
                "DELETE FROM clan_market_items",
                "DELETE FROM clan_invites",
                "DELETE FROM clan_permissions",
                "DELETE FROM clan_members",
                "DELETE FROM clan_allies",
                "DELETE FROM clans",
                "UPDATE colina SET dono = 'Sem Dono'"
        };
        int ok = 0;
        try (Statement stmt = plugin.getDatabaseManager().getConnection().createStatement()) {
            for (String sql : wipes) {
                try {
                    stmt.executeUpdate(sql);
                    ok++;
                } catch (Exception e) {
                    // Tabela pode não existir em alguma versão — apenas loga e segue.
                    plugin.getLogger().warning("[RESET] Falha em '" + sql + "': " + e.getMessage());
                }
            }
        } catch (Exception e) {
            sender.sendMessage(mm.deserialize("<#FF0000>Erro ao limpar o banco: " + e.getMessage()));
        }
        return ok;
    }

    /** Apaga os arquivos .dat de TODOS os jogadores (inventário/ender chest vanilla). */
    private int deletePlayerDataFiles() {
        int deleted = 0;
        for (World world : Bukkit.getWorlds()) {
            File playerDataDir = new File(world.getWorldFolder(), "playerdata");
            if (!playerDataDir.isDirectory()) continue;
            File[] files = playerDataDir.listFiles((dir, name) ->
                    name.endsWith(".dat") || name.endsWith(".dat_old"));
            if (files == null) continue;
            for (File f : files) {
                if (f.delete()) deleted++;
            }
        }
        return deleted;
    }

    /**
     * Reset geral: zera o dinheiro de TODOS (online + offline), e nos jogadores
     * online limpa o inventário, a ender chest e dá de novo o kit aleatório.
     */
    private void handleReset(CommandSender sender) {
        // 1) Limpa o cache/fila de economia ANTES de mexer no banco, senão o flush
        //    assíncrono poderia regravar saldos antigos por cima do reset.
        plugin.getEconomyManager().clearCache();
        try (Statement stmt = plugin.getDatabaseManager().getConnection().createStatement()) {
            stmt.executeUpdate("UPDATE player_economy SET coins = 0, tokens = 0");
            stmt.executeUpdate("UPDATE player_data SET kills = 0, deaths = 0, level = 1, xp = 0");
        } catch (Exception e) {
            sender.sendMessage(mm.deserialize("<#FF0000>Erro ao resetar: " + e.getMessage()));
            return;
        }

        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            // 2) Limpa inventário (incl. armadura e off-hand) e ender chest.
            p.getInventory().clear();
            p.getInventory().setArmorContents(new ItemStack[4]);
            p.getInventory().setItemInOffHand(null);
            p.getEnderChest().clear();

            // 3) Zera saldo e stats no cache do jogador online também.
            plugin.getEconomyManager().setCoins(p.getUniqueId(), p.getName(), 0);
            plugin.getEconomyManager().setTokens(p.getUniqueId(), p.getName(), 0);
            PlayerData pd = plugin.getPlayerDataManager().getPlayerData(p);
            if (pd != null) { pd.setKills(0); pd.setDeaths(0); pd.setLevel(1); pd.setXp(0); }

            // 4) Dá novamente o kit aleatório (inventário já limpo -> passa a checagem do kit).
            plugin.getKitManager().give(p);
            count++;

            p.sendMessage(mm.deserialize(""));
            p.sendMessage(mm.deserialize("<#FF0000><bold>RESET GERAL!"));
            p.sendMessage(mm.deserialize("<#ffffff>Dinheiro, kills, level, ender chest e inventário foram zerados."));
            p.sendMessage(mm.deserialize("<#10fc46>Você recebeu um novo kit aleatório!"));
            p.sendMessage(mm.deserialize(""));
        }
        sender.sendMessage(mm.deserialize("<#10fc46>Reset geral concluído (online + offline)! <#fcc850>"
                + count + " <#10fc46>jogador(es) online resetado(s)."));
    }

    private void handleResetKillsAll(CommandSender sender) {
        // Reset GLOBAL: zera TODOS os jogadores no banco (online + OFFLINE), sem WHERE.
        try (Statement stmt = plugin.getDatabaseManager().getConnection().createStatement()) {
            stmt.executeUpdate("UPDATE player_data SET kills = 0, deaths = 0, level = 1, xp = 0");
        } catch (Exception e) {
            sender.sendMessage(mm.deserialize("<#FF0000>Erro ao resetar: " + e.getMessage()));
            return;
        }
        // Zera também o cache dos online (senão a próxima gravação do cache desfaz o reset deles).
        for (Player online : Bukkit.getOnlinePlayers()) {
            PlayerData pd = plugin.getPlayerDataManager().getPlayerData(online);
            if (pd != null) { pd.setKills(0); pd.setDeaths(0); pd.setLevel(1); pd.setXp(0); }
            online.sendMessage(mm.deserialize(""));
            online.sendMessage(mm.deserialize("<#FF0000><bold>RESET GLOBAL!"));
            online.sendMessage(mm.deserialize("<#ffffff>Todos os status do The Pit foram resetados."));
            online.sendMessage(mm.deserialize("<#a4a4a4>Kills, level e progresso foram zerados."));
            online.sendMessage(mm.deserialize("<#10fc46>Boa sorte farmando novamente!"));
            online.sendMessage(mm.deserialize(""));
        }
        sender.sendMessage(mm.deserialize("<#10fc46>Reset GLOBAL concluído (online + offline)!"));
    }

    private void handleColocarItem(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Este comando e apenas para jogadores."));
            return;
        }
        if (args.length < 4) {
            player.sendMessage(mm.deserialize("<#fcc850>Uso: /thepit colocaritem <slot> <qtd> <chance>"));
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(mm.deserialize("<#FF0000>Voce precisa estar segurando um item!"));
            return;
        }

        int slot, qtd, chance;
        try {
            slot   = Integer.parseInt(args[1]);
            qtd    = Integer.parseInt(args[2]);
            chance = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            player.sendMessage(mm.deserialize("<#FF0000>Valores invalidos!"));
            return;
        }
        if (slot < 0 || slot > 40) {
            player.sendMessage(mm.deserialize("<#FF0000>Slot invalido (0-40)!"));
            return;
        }

        plugin.getKitLootManager().addEntry(slot, hand.clone(), qtd, chance);
        player.sendMessage(mm.deserialize(
                "<#10fc46>Adicionado ao slot <#fcc850>" + slot
                + " <#10fc46>com sucesso! (qtd " + qtd + ", " + chance + "%)"));
    }

    private void handleRemoverItem(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<#fcc850>Uso: /thepit removeritem <slot|all>"));
            return;
        }
        if (args[1].equalsIgnoreCase("all")) {
            plugin.getKitLootManager().clearAll();
            sender.sendMessage(mm.deserialize("<#10fc46>Loot do kit limpo (todos os slots)!"));
            return;
        }
        int slot;
        try { slot = Integer.parseInt(args[1]); }
        catch (NumberFormatException e) {
            sender.sendMessage(mm.deserialize("<#FF0000>Slot invalido!"));
            return;
        }
        plugin.getKitLootManager().clearSlot(slot);
        sender.sendMessage(mm.deserialize(
                "<#10fc46>Slot <#fcc850>" + slot + " <#10fc46>limpo!"));
    }

    private void handleRestoreInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<#FF0000>Uso: /thepit restoreinfo <jogador>"));
            return;
        }

        CombatInventorySaveManager combatSave = plugin.getCombatInventorySaveManager();
        if (combatSave == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Sistema de backup de inventário indisponível."));
            return;
        }

        Player online = Bukkit.getPlayer(args[1]);
        UUID uuid = online != null ? online.getUniqueId() : null;
        String displayName = args[1];

        if (uuid == null) {
            @SuppressWarnings("deprecation")
            var offline = Bukkit.getOfflinePlayer(args[1]);
            if (offline.hasPlayedBefore() || offline.isOnline()) {
                uuid = offline.getUniqueId();
                displayName = offline.getName() != null ? offline.getName() : args[1];
            }
        }

        if (uuid == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Jogador não encontrado: <#fcc850>" + args[1]));
            return;
        }

        Optional<CombatInventorySaveManager.BackupInfo> pending = combatSave.findPendingBackup(uuid);
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

        sender.sendMessage(mm.deserialize("<#efa600><bold>Restore — " + displayName));
        sender.sendMessage(mm.deserialize("<#a4a4a4>Modo crash: <#ffffff>"
                + (combatSave.isRestoreMode() ? "sim" : "não")));
        sender.sendMessage(mm.deserialize("<#a4a4a4>Sessão ativa: <#ffffff>" + combatSave.getActiveSessionId()));

        if (online != null) {
            sender.sendMessage(mm.deserialize("<#a4a4a4>Online: <#10fc46>sim"));
            sender.sendMessage(mm.deserialize("<#a4a4a4>Inventário atual: <#ffffff>"
                    + (CombatInventorySaveManager.hasItems(online) ? "com itens" : "vazio")));
        } else {
            sender.sendMessage(mm.deserialize("<#a4a4a4>Online: <#FF5555>não"));
        }

        if (pending.isPresent()) {
            CombatInventorySaveManager.BackupInfo info = pending.get();
            sender.sendMessage(mm.deserialize("<#10fc46>Backup pendente: <#ffffff>sim"));
            sender.sendMessage(mm.deserialize("<#a4a4a4>  Sessão: <#ffffff>" + info.sessionId()));
            sender.sendMessage(mm.deserialize("<#a4a4a4>  Salvo em: <#ffffff>" + fmt.format(new Date(info.savedAt()))));
            sender.sendMessage(mm.deserialize("<#a4a4a4>  Em combate: <#ffffff>" + (info.inCombat() ? "sim" : "não")));
        } else {
            sender.sendMessage(mm.deserialize("<#FF5555>Backup pendente: <#ffffff>não"));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(mm.deserialize("<#efa600><bold>ThePit Admin:"));
        sender.sendMessage(mm.deserialize("  <#fcc850>/thepit reset <#a4a4a4>- Zera dinheiro/ender chest/inventario e da rekit"));
        sender.sendMessage(mm.deserialize("  <#fcc850>/thepit resetkillsall <#a4a4a4>- Reseta kills/deaths de todos"));
        sender.sendMessage(mm.deserialize("  <#FF0000>/thepit resetall confirmar <#a4a4a4>- RESET TOTAL (apaga TUDO de todos: banco + inventarios)"));
        sender.sendMessage(mm.deserialize("  <#fcc850>/thepit colocaritem <slot> <qtd> <chance> <#a4a4a4>- Adiciona item ao kit"));
        sender.sendMessage(mm.deserialize("  <#fcc850>/thepit removeritem <slot|all> <#a4a4a4>- Remove loot do kit"));
        sender.sendMessage(mm.deserialize("  <#fcc850>/thepit restoreinfo <jogador> <#a4a4a4>- Info de backup pós-crash"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("psdk.thepit")) return List.of();

        if (args.length == 1) {
            return List.of("reset", "resetkillsall", "resetall", "colocaritem", "removeritem", "restoreinfo").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("resetall")) {
            return List.of("confirmar").stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("removeritem")) {
            List<String> opts = new ArrayList<>();
            opts.add("all");
            for (int i = 0; i <= 40; i++) opts.add(String.valueOf(i));
            return opts.stream().filter(s -> s.startsWith(args[1])).toList();
        }
        return List.of();
    }
}
