package com.psdk.crates;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CaixaCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public CaixaCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("psdk.caixa")) {
            sender.sendMessage(mm.deserialize("<#FF0000>Sem permissão!"));
            return true;
        }
        if (args.length == 0) { sendUsage(sender); return true; }

        return switch (args[0].toLowerCase()) {
            case "criar"        -> cmdCriar(sender, args);
            case "deletar"      -> cmdDeletar(sender, args);
            case "item"         -> cmdItem(sender, args);
            case "removeitem"   -> cmdRemoveItem(sender, args);
            case "givechave"    -> cmdGiveChave(sender, args);
            case "resetsaldo"   -> cmdResetSaldo(sender, args);
            case "resetlimite"  -> cmdResetLimite(sender, args);
            case "listar"       -> cmdListar(sender);
            case "info"         -> cmdInfo(sender, args);
            case "setlimite"    -> cmdSetLimite(sender, args);
            case "settipo"      -> cmdSetTipo(sender, args);
            case "setcor"       -> cmdSetCor(sender, args);
            case "setkeynexo"   -> cmdSetKeyNexo(sender, args);
            case "setpreco"     -> cmdSetPreco(sender, args);
            case "setup"        -> cmdSetup(sender, args);
            case "mover"        -> cmdMover(sender, args);
            default -> { sendUsage(sender); yield true; }
        };
    }

    private void sendUsage(CommandSender s) {
        s.sendMessage(mm.deserialize("<#a4a4a4><strikethrough>----</strikethrough> <#fcc850><bold>Crates Admin <#a4a4a4><strikethrough>----"));
        s.sendMessage(mm.deserialize("<#fcc850>/caixa criar <nome> <normal|exclusiva> <visual> [cor]"));
        s.sendMessage(mm.deserialize("<#a4a4a4>  Visual: bau | enderchest | @mainhand | @offhand"));
        s.sendMessage(mm.deserialize("<#a4a4a4>  Cor: <#fcc850><\\#hex> <#a4a4a4>ou <gradient:#hex1:#hex2>texto"));
        s.sendMessage(mm.deserialize("<#fcc850>/caixa deletar <nome>"));
        s.sendMessage(mm.deserialize("<#fcc850>/caixa item <nome> [slot] <#a4a4a4>- add item da mao (slot 11-15 ou 20-24; vazio = auto)"));
        s.sendMessage(mm.deserialize("<#fcc850>/caixa removeitem <nome> <slot> <#a4a4a4>- remove item do slot (11-15 ou 20-24)"));
        s.sendMessage(mm.deserialize("<#fcc850>/caixa givechave <jogador> <caixa> <qtd>"));
        s.sendMessage(mm.deserialize("<#fcc850>/caixa resetsaldo <jogador> <caixa|all>"));
        s.sendMessage(mm.deserialize("<#fcc850>/caixa resetlimite <caixa> [novo_limite]"));
        s.sendMessage(mm.deserialize("<#fcc850>/caixa setlimite <caixa> <limite>"));
        s.sendMessage(mm.deserialize("<#fcc850>/caixa settipo <caixa> <normal|exclusiva>"));
        s.sendMessage(mm.deserialize("<#fcc850>/caixa setcor <caixa> <cor>"));
        s.sendMessage(mm.deserialize("<#fcc850>/caixa setkeynexo <caixa> <nexo_id|reset>"));
        s.sendMessage(mm.deserialize("<#fcc850>/caixa setpreco <caixa> <tokens> <#a4a4a4>- preço por chave"));
        s.sendMessage(mm.deserialize("<#fcc850>/caixa setup <rara|especial> <#a4a4a4>- preenche itens padrão"));
        s.sendMessage(mm.deserialize("<#fcc850>/caixa mover <nome> <cima|baixo> [blocos] <#a4a4a4>- ajusta altura do holograma"));
        s.sendMessage(mm.deserialize("<#fcc850>/caixa listar"));
        s.sendMessage(mm.deserialize("<#fcc850>/caixa info <nome>"));
    }

    private boolean cmdCriar(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Apenas jogadores."));
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(mm.deserialize("<#fcc850>Uso: /caixa criar <nome> <normal|exclusiva> <visual>"));
            sender.sendMessage(mm.deserialize("<#a4a4a4>Visual: bau | enderchest | @mainhand | @offhand"));
            return true;
        }

        String nome = args[1].toLowerCase();
        if (plugin.getCrateManager().hasCrate(nome)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Já existe uma caixa com esse nome!"));
            return true;
        }

        Crate.Tipo tipo;
        try { tipo = Crate.Tipo.valueOf(args[2].toUpperCase()); }
        catch (IllegalArgumentException e) {
            sender.sendMessage(mm.deserialize("<#FF0000>Isso é um tipo inválido de caixa! Use: normal ou exclusiva"));
            return true;
        }

        String visualArg = args[3].toLowerCase();
        Crate.Visual visual;
        ItemStack customItem = null;

        if (visualArg.equals("@mainhand")) {
            visual = Crate.Visual.CUSTOM;
            customItem = player.getInventory().getItemInMainHand();
            if (customItem.getType().isAir()) {
                sender.sendMessage(mm.deserialize("<#FF0000>Segure um item na mao principal para usar como holograma!"));
                return true;
            }
            customItem = customItem.clone();
        } else if (visualArg.equals("@offhand")) {
            visual = Crate.Visual.CUSTOM;
            customItem = player.getInventory().getItemInOffHand();
            if (customItem.getType().isAir()) {
                sender.sendMessage(mm.deserialize("<#FF0000>Segure um item na mao secundaria para usar como holograma!"));
                return true;
            }
            customItem = customItem.clone();
        } else {
            try { visual = Crate.Visual.valueOf(visualArg.toUpperCase()); }
            catch (IllegalArgumentException e) {
                sender.sendMessage(mm.deserialize("<#FF0000>Visual inválido! Use: bau, enderchest, @mainhand ou @offhand"));
                return true;
            }
        }

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || targetBlock.getType().isAir()) {
            sender.sendMessage(mm.deserialize("<#FF0000>Olhe para um bloco para definir a localização."));
            return true;
        }

        String cor = "<#fcc850>";
        if (args.length >= 5) {
            cor = String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length));
        }

        Crate crate = new Crate(nome);
        crate.setTipo(tipo);
        crate.setVisual(visual);
        crate.setCor(cor);
        if (customItem != null) crate.setCustomHologramItem(customItem);
        crate.setTituloMenu("<#fcc850><bold>Escolha sua recompensa abaixo");
        crate.setLimiteGlobal(tipo == Crate.Tipo.EXCLUSIVA ? 500 : -1);
        crate.setLocal(targetBlock.getLocation());

        CrateKey chave = new CrateKey();
        chave.setMaterial(Material.TRIPWIRE_HOOK);
        chave.setDisplayName("<!italic><#efa600><bold>Chave " + nome);
        chave.setLore(List.of(
                "<!italic><#a4a4a4>Clique o botão direito",
                "<!italic><#a4a4a4>para ativar essa chave."
        ));
        chave.setNbtKey("psdk_key_" + nome);
        crate.setItemChave(chave);

        plugin.getCrateManager().saveCrate(crate);
        plugin.getHologramManager().spawnHologram(crate);

        sender.sendMessage(mm.deserialize("<#10fc46>Caixa <#efa600>" + nome + " <#10fc46>criada!"));
        return true;
    }

    private boolean cmdDeletar(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<#fcc850>Uso: /caixa deletar <nome>"));
            return true;
        }
        String nome = args[1].toLowerCase();
        Crate crate = plugin.getCrateManager().getCrate(nome);
        if (crate == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Caixa não encontrada!"));
            return true;
        }

        plugin.getHologramManager().despawnHologram(crate);
        plugin.getCrateManager().deleteCrate(nome);
        sender.sendMessage(mm.deserialize("<#FF0000>Caixa <#fcc850>" + nome + " <#FF0000>deletada!"));
        return true;
    }

    private boolean cmdItem(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Apenas jogadores."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<#fcc850>Uso: /caixa item <nome_da_caixa>"));
            return true;
        }

        String nome = args[1].toLowerCase();
        Crate crate = plugin.getCrateManager().getCrate(nome);
        if (crate == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Caixa não encontrada!"));
            return true;
        }

        ItemStack itemNaMao = player.getInventory().getItemInMainHand();
        if (itemNaMao.getType().isAir()) {
            sender.sendMessage(mm.deserialize("<#FF0000>Segure um item na mao!"));
            return true;
        }

        // Slot opcional: número real da GUI (11-15 ou 20-24).
        if (args.length >= 3) {
            int guiSlot;
            try { guiSlot = Integer.parseInt(args[2]); } catch (NumberFormatException e) {
                sender.sendMessage(mm.deserialize("<#FF0000>Slot inválido! Use: <#fcc850>11-15 <#FF0000>ou <#fcc850>20-24"));
                return true;
            }
            int idx = CrateGUI.getRewardSlots().indexOf(guiSlot);
            if (idx < 0) {
                sender.sendMessage(mm.deserialize("<#FF0000>Slot inválido! Use: <#fcc850>11-15 <#FF0000>ou <#fcc850>20-24"));
                return true;
            }
            ItemStack anterior = crate.getItens().get(idx);
            boolean substituiu = anterior != null && !anterior.getType().isAir();
            crate.setItemAt(idx, itemNaMao.clone());
            plugin.getCrateManager().saveCrate(crate);
            if (substituiu) {
                sender.sendMessage(mm.deserialize("<#10fc46>Item <#fcc850>substituído<#10fc46> em <#efa600>" + nome + " <#a4a4a4>(slot " + guiSlot + ")"));
            } else {
                sender.sendMessage(mm.deserialize("<#10fc46>Item adicionado a <#efa600>" + nome + " <#a4a4a4>(slot " + guiSlot + ")"));
            }
            return true;
        }

        // Sem slot: preenche a primeira posição livre (11→15, depois 20→24).
        if (crate.isFull()) {
            sender.sendMessage(mm.deserialize("<#FF0000>Caixa cheia! (Max <bold>" + Crate.MAX_ITENS + "</bold> itens)"));
            return true;
        }
        int idx = crate.addItem(itemNaMao.clone());
        plugin.getCrateManager().saveCrate(crate);
        int guiSlot = CrateGUI.getRewardSlots().get(idx);
        sender.sendMessage(mm.deserialize("<#10fc46>Item adicionado a <#efa600>" + nome + " <#a4a4a4>(slot " + guiSlot + ")"));
        return true;
    }

    private boolean cmdRemoveItem(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(mm.deserialize("<#fcc850>Uso: /caixa removeitem <nome> <slot>"));
            return true;
        }
        String nome = args[1].toLowerCase();
        Crate crate = plugin.getCrateManager().getCrate(nome);
        if (crate == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Caixa não encontrada!"));
            return true;
        }
        int guiSlot;
        try { guiSlot = Integer.parseInt(args[2]); } catch (NumberFormatException e) {
            sender.sendMessage(mm.deserialize("<#FF0000>Slot inválido! Use: <#fcc850>11-15 <#FF0000>ou <#fcc850>20-24"));
            return true;
        }
        int idx = CrateGUI.getRewardSlots().indexOf(guiSlot);
        if (idx < 0) {
            sender.sendMessage(mm.deserialize("<#FF0000>Slot inválido! Use: <#fcc850>11-15 <#FF0000>ou <#fcc850>20-24"));
            return true;
        }
        ItemStack atual = crate.getItens().get(idx);
        if (atual == null || atual.getType().isAir()) {
            sender.sendMessage(mm.deserialize("<#FF0000>O slot <#fcc850>" + guiSlot + " <#FF0000>já está vazio!"));
            return true;
        }
        crate.removeItemAt(idx);
        plugin.getCrateManager().saveCrate(crate);
        sender.sendMessage(mm.deserialize("<#10fc46>Item do slot <#fcc850>" + guiSlot + " <#10fc46>removido de <#efa600>" + nome));
        return true;
    }

    private boolean cmdGiveChave(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(mm.deserialize("<#fcc850>Uso: /caixa givechave <jogador> <caixa> <qtd>"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Jogador não encontrado!"));
            return true;
        }
        Crate crate = plugin.getCrateManager().getCrate(args[2].toLowerCase());
        if (crate == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Caixa não encontrada!"));
            return true;
        }
        int qtd;
        try { qtd = Integer.parseInt(args[3]); if (qtd <= 0) throw new NumberFormatException(); }
        catch (NumberFormatException e) {
            sender.sendMessage(mm.deserialize("<#FF0000>Quantidade inválida!"));
            return true;
        }

        int restante = qtd;
        while (restante > 0) {
            int stackSize = Math.min(restante, 64);
            ItemStack keyItem = plugin.getKeyManager().createKeyItem(crate, stackSize);
            if (keyItem != null) {
                java.util.Map<Integer, ItemStack> of = target.getInventory().addItem(keyItem);
                for (ItemStack o : of.values()) target.getWorld().dropItemNaturally(target.getLocation(), o);
            }
            restante -= stackSize;
        }
        sender.sendMessage(mm.deserialize("<#10fc46>Deu <#fcc850>" + qtd + " chave(s) <#10fc46>de <#efa600>" + crate.getNome() + " <#10fc46>para <#fcc850>" + target.getName()));
        return true;
    }

    private boolean cmdResetSaldo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(mm.deserialize("<#fcc850>Uso: /caixa resetsaldo <jogador> <caixa|all>"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Jogador não encontrado!"));
            return true;
        }
        if (args[2].equalsIgnoreCase("all")) {
            for (Crate c : plugin.getCrateManager().getAllCrates())
                plugin.getCrateManager().setSaldo(target.getUniqueId(), c.getNome(), 0);
            sender.sendMessage(mm.deserialize("<#10fc46>Saldo de todas caixas zerado para <#fcc850>" + target.getName()));
        } else {
            plugin.getCrateManager().setSaldo(target.getUniqueId(), args[2].toLowerCase(), 0);
            sender.sendMessage(mm.deserialize("<#10fc46>Saldo de <#efa600>" + args[2] + " <#10fc46>zerado para <#fcc850>" + target.getName()));
        }
        return true;
    }

    private boolean cmdResetLimite(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<#fcc850>Uso: /caixa resetlimite <caixa> [novo_limite]"));
            return true;
        }
        Crate crate = plugin.getCrateManager().getCrate(args[1].toLowerCase());
        if (crate == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Caixa não encontrada!"));
            return true;
        }
        if (crate.getTipo() != Crate.Tipo.EXCLUSIVA) {
            sender.sendMessage(mm.deserialize("<#FF0000>Apenas exclusivas tem limite!"));
            return true;
        }
        int novoLimite = 500;
        if (args.length >= 3) {
            try { novoLimite = Integer.parseInt(args[2]); } catch (NumberFormatException e) {
                sender.sendMessage(mm.deserialize("<#FF0000>Numero inválido!"));
                return true;
            }
        }
        crate.setLimiteGlobal(novoLimite);
        plugin.getCrateManager().updateLimiteGlobal(crate);
        sender.sendMessage(mm.deserialize("<#10fc46>Limite de <#efa600>" + crate.getNome() + " <#10fc46>resetado para <#fcc850>" + novoLimite));
        return true;
    }

    private boolean cmdSetLimite(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(mm.deserialize("<#fcc850>Uso: /caixa setlimite <caixa> <limite>"));
            return true;
        }
        Crate crate = plugin.getCrateManager().getCrate(args[1].toLowerCase());
        if (crate == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Caixa não encontrada!"));
            return true;
        }
        int limite;
        try { limite = Integer.parseInt(args[2]); } catch (NumberFormatException e) {
            sender.sendMessage(mm.deserialize("<#FF0000>Numero inválido!"));
            return true;
        }
        crate.setLimiteGlobal(limite);
        plugin.getCrateManager().updateLimiteGlobal(crate);
        sender.sendMessage(mm.deserialize("<#10fc46>Limite de <#efa600>" + crate.getNome() + " <#10fc46>definido para <#fcc850>" + limite));
        return true;
    }

    private boolean cmdSetTipo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(mm.deserialize("<#fcc850>Uso: /caixa settipo <caixa> <normal|exclusiva>"));
            return true;
        }
        Crate crate = plugin.getCrateManager().getCrate(args[1].toLowerCase());
        if (crate == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Caixa não encontrada!"));
            return true;
        }
        Crate.Tipo tipo;
        try { tipo = Crate.Tipo.valueOf(args[2].toUpperCase()); } catch (IllegalArgumentException e) {
            sender.sendMessage(mm.deserialize("<#FF0000>Tipo inválido! Use: normal ou exclusiva"));
            return true;
        }
        crate.setTipo(tipo);
        if (tipo == Crate.Tipo.EXCLUSIVA && crate.getLimiteGlobal() < 0) crate.setLimiteGlobal(500);
        plugin.getCrateManager().saveCrate(crate);
        sender.sendMessage(mm.deserialize("<#10fc46>Tipo de <#efa600>" + crate.getNome() + " <#10fc46>alterado para <#fcc850>" + tipo.name()));
        return true;
    }

    private boolean cmdSetCor(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(mm.deserialize("<#fcc850>Uso: /caixa setcor <caixa> <cor>"));
            sender.sendMessage(mm.deserialize("<#a4a4a4>Exemplo: /caixa setcor vip <\\#fcc850>"));
            sender.sendMessage(mm.deserialize("<#a4a4a4>Exemplo: /caixa setcor vip <gradient:\\#fcc850:\\#a4a4a4>"));
            return true;
        }
        Crate crate = plugin.getCrateManager().getCrate(args[1].toLowerCase());
        if (crate == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Caixa não encontrada!"));
            return true;
        }
        String cor = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        crate.setCor(cor);
        plugin.getCrateManager().saveCrate(crate);
        sender.sendMessage(mm.deserialize("<#10fc46>Cor de <#efa600>" + crate.getNome() + " <#10fc46>alterada para: " + cor + "Exemplo"));
        return true;
    }

    private boolean cmdSetKeyNexo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(mm.deserialize("<#fcc850>Uso: /caixa setkeynexo <caixa> <nexo_id|reset>"));
            sender.sendMessage(mm.deserialize("<#a4a4a4>Exemplo: /caixa setkeynexo infernal inferno_axe"));
            sender.sendMessage(mm.deserialize("<#a4a4a4>Use <#fcc850>reset <#a4a4a4>para voltar à chave padrão."));
            return true;
        }
        Crate crate = plugin.getCrateManager().getCrate(args[1].toLowerCase());
        if (crate == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Caixa não encontrada!"));
            return true;
        }

        String nexoId = args[2];

        if (nexoId.equalsIgnoreCase("reset")) {
            crate.setNexoKeyId(null);
            plugin.getCrateManager().updateNexoKeyId(crate);
            sender.sendMessage(mm.deserialize("<#10fc46>Chave de <#efa600>" + crate.getNome() + " <#10fc46>voltou ao padrão (TRIPWIRE_HOOK)."));
            return true;
        }

        // Verifica se o item Nexo existe
        if (com.nexomc.nexo.api.NexoItems.itemFromId(nexoId) == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Item Nexo '<#fcc850>" + nexoId + "<#FF0000>' não encontrado!"));
            sender.sendMessage(mm.deserialize("<#a4a4a4>Verifique o ID exato no seu config do Nexo."));
            return true;
        }

        crate.setNexoKeyId(nexoId);
        plugin.getCrateManager().updateNexoKeyId(crate);
        sender.sendMessage(mm.deserialize("<#10fc46>Chave da caixa <#efa600>" + crate.getNome() + " <#10fc46>definida para o item Nexo: <#fcc850>" + nexoId));
        return true;
    }

    private boolean cmdSetPreco(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(mm.deserialize("<#fcc850>Uso: /caixa setpreco <caixa> <tokens>"));
            return true;
        }
        Crate crate = plugin.getCrateManager().getCrate(args[1].toLowerCase());
        if (crate == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Caixa não encontrada!"));
            return true;
        }
        double preco;
        try { preco = Double.parseDouble(args[2]); } catch (NumberFormatException e) {
            sender.sendMessage(mm.deserialize("<#FF0000>Valor inválido!"));
            return true;
        }
        if (preco < 1) {
            sender.sendMessage(mm.deserialize("<#FF0000>O preço deve ser no mínimo 1 token!"));
            return true;
        }
        crate.setPrecoToken(preco);
        plugin.getCrateManager().saveCrate(crate);
        sender.sendMessage(mm.deserialize("<#10fc46>Preço da caixa <#efa600>" + crate.getNome()
                + " <#10fc46>definido para <#fcc850>" + (int) crate.getPrecoToken() + " tokens<#10fc46>."));
        return true;
    }

    private boolean cmdSetup(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<#fcc850>Uso: /caixa setup <rara|especial>"));
            return true;
        }

        String nome = args[1].toLowerCase();
        Crate crate = plugin.getCrateManager().getCrate(nome);
        if (crate == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Caixa <#fcc850>" + nome + " <#FF0000>não encontrada! Crie-a primeiro com /caixa criar."));
            return true;
        }

        List<ItemStack> items = switch (nome) {
            case "rara" -> CrateDefaultItems.buildRaraItems();
            case "especial" -> CrateDefaultItems.buildEspecialItems();
            case "eye" -> CrateDefaultItems.buildEyeItems();
            default -> null;
        };

        if (items == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Setup disponível apenas para: <#fcc850>rara<#FF0000>, <#fcc850>especial<#FF0000>, <#fcc850>eye"));
            return true;
        }

        // Preço padrão por caixa (aplicado automaticamente no setup).
        double precoPadrao = switch (nome) {
            case "rara" -> 100;
            case "especial" -> 150;
            case "eye" -> 300;
            default -> crate.getPrecoToken();
        };

        crate.setItens(items);
        crate.setPrecoToken(precoPadrao);
        plugin.getCrateManager().saveCrate(crate);
        sender.sendMessage(mm.deserialize("<#10fc46>Itens padrão configurados para <#efa600>" + nome
                + " <#a4a4a4>(" + crate.countItens() + " itens) <#10fc46>| Preço: <#fcc850>"
                + (int) crate.getPrecoToken() + " tokens"));
        return true;
    }

    private boolean cmdListar(CommandSender sender) {
        var crates = plugin.getCrateManager().getAllCrates();
        if (crates.isEmpty()) {
            sender.sendMessage(mm.deserialize("<#FF0000>Nenhuma caixa criada."));
            return true;
        }
        sender.sendMessage(mm.deserialize("<#a4a4a4><strikethrough>----</strikethrough> <#fcc850><bold>Caixas <#a4a4a4><strikethrough>----"));
        for (Crate c : crates) {
            String tipoTag = c.getTipo() == Crate.Tipo.EXCLUSIVA ? " <#e22c27>[EXCLUSIVA]" : " <#10fc46>[NORMAL]";
            sender.sendMessage(mm.deserialize("<#fcc850> " + c.getNome() + tipoTag +
                    " <#a4a4a4>| <#cbd1d7>" + c.countItens() + " itens" +
                    (c.getTipo() == Crate.Tipo.EXCLUSIVA ? " | <#cbd1d7>Limite: " + c.getLimiteGlobal() : "")));
        }
        return true;
    }

    private boolean cmdInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<#fcc850>Uso: /caixa info <nome>"));
            return true;
        }
        Crate crate = plugin.getCrateManager().getCrate(args[1].toLowerCase());
        if (crate == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Caixa não encontrada!"));
            return true;
        }
        sender.sendMessage(mm.deserialize("<#a4a4a4><strikethrough>----</strikethrough> <#fcc850><bold>" + crate.getNome().toUpperCase() + " <#a4a4a4><strikethrough>----"));
        sender.sendMessage(mm.deserialize("<#fcc850> Tipo: <#cbd1d7>" + crate.getTipo().name()));
        String visualStr = crate.getVisual().name();
        if (crate.getVisual() == Crate.Visual.CUSTOM && crate.getCustomHologramItem() != null) {
            visualStr = "CUSTOM (" + crate.getCustomHologramItem().getType().name() + ")";
        }
        sender.sendMessage(mm.deserialize("<#fcc850> Visual: <#cbd1d7>" + visualStr));
        sender.sendMessage(mm.deserialize("<#fcc850> Cor: " + crate.getCor() + "Exemplo"));
        sender.sendMessage(mm.deserialize("<#fcc850> Itens: <#cbd1d7>" + crate.countItens() + "/" + Crate.MAX_ITENS));
        if (crate.getTipo() == Crate.Tipo.EXCLUSIVA)
            sender.sendMessage(mm.deserialize("<#fcc850> Limite: <#cbd1d7>" + crate.getLimiteGlobal()));
        Location loc = crate.getLocal();
        if (loc != null)
            sender.sendMessage(mm.deserialize("<#fcc850> Local: <#cbd1d7>" + String.format("%.0f, %.0f, %.0f (%s)", loc.getX(), loc.getY(), loc.getZ(), loc.getWorld() != null ? loc.getWorld().getName() : "?")));
        return true;
    }

    private boolean cmdMover(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(mm.deserialize("<#fcc850>Uso: /caixa mover <nome> <cima|baixo> [blocos]"));
            sender.sendMessage(mm.deserialize("<#a4a4a4>Padrão: 0.3 blocos. Ex: /caixa mover eye cima 0.5"));
            return true;
        }

        String nome = args[1].toLowerCase();
        Crate crate = plugin.getCrateManager().getCrate(nome);
        if (crate == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Caixa não encontrada: " + nome));
            return true;
        }

        String direcao = args[2].toLowerCase();
        if (!direcao.equals("cima") && !direcao.equals("baixo")) {
            sender.sendMessage(mm.deserialize("<#FF0000>Use: cima ou baixo"));
            return true;
        }

        double valor = 0.3;
        if (args.length >= 4) {
            try { valor = Double.parseDouble(args[3]); }
            catch (NumberFormatException e) {
                sender.sendMessage(mm.deserialize("<#FF0000>Valor inválido: " + args[3]));
                return true;
            }
        }

        String bdUUID = crate.getBlockDisplayUUID();
        if (bdUUID == null || bdUUID.isEmpty()) {
            sender.sendMessage(mm.deserialize("<#FF0000>Essa caixa não tem holograma spawnado."));
            return true;
        }

        java.util.UUID uuid;
        try { uuid = java.util.UUID.fromString(bdUUID); }
        catch (IllegalArgumentException e) {
            sender.sendMessage(mm.deserialize("<#FF0000>UUID do holograma inválido."));
            return true;
        }

        Entity entity = Bukkit.getEntity(uuid);
        if (entity == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Entidade não encontrada (talvez o chunk não esteja carregado)."));
            return true;
        }

        double dy = direcao.equals("cima") ? valor : -valor;
        Location newLoc = entity.getLocation().add(0, dy, 0);
        entity.teleport(newLoc);

        sender.sendMessage(mm.deserialize("<#10fc46>Holograma de <#fcc850>" + nome
                + " <#10fc46>movido " + direcao + " " + valor + " blocos. Y atual: <#fcc850>"
                + String.format("%.2f", newLoc.getY())));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("psdk.caixa")) return List.of();
        if (args.length == 1)
            return filter(List.of("criar", "deletar", "item", "removeitem", "givechave", "resetsaldo",
                    "resetlimite", "setlimite", "settipo", "setcor", "setkeynexo", "setpreco", "setup", "mover", "listar", "info"), args[0]);
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "deletar", "item", "removeitem", "resetlimite", "setlimite", "settipo", "setcor", "setkeynexo", "setpreco", "mover", "info" ->
                        filter(new ArrayList<>(plugin.getCrateManager().getCratesMap().keySet()), args[1]);
                case "setup" -> filter(List.of("rara", "especial"), args[1]);
                case "givechave", "resetsaldo" ->
                        filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
                case "criar" -> filter(List.of("<nome>"), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (args[0].toLowerCase()) {
                case "givechave" -> filter(new ArrayList<>(plugin.getCrateManager().getCratesMap().keySet()), args[2]);
                case "resetsaldo" -> {
                    List<String> opts = new ArrayList<>(plugin.getCrateManager().getCratesMap().keySet());
                    opts.add("all"); yield filter(opts, args[2]);
                }
                case "criar" -> filter(List.of("normal", "exclusiva"), args[2]);
                case "settipo" -> filter(List.of("normal", "exclusiva"), args[2]);
                case "mover" -> filter(List.of("cima", "baixo"), args[2]);
                case "item" -> filter(slotSuggestions(args[1], true), args[2]);
                case "removeitem" -> filter(slotSuggestions(args[1], false), args[2]);
                default -> List.of();
            };
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("criar"))
            return filter(List.of("bau", "enderchest", "@mainhand", "@offhand"), args[3]);
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        return options.stream().filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase())).collect(Collectors.toList());
    }

    /**
     * Sugestões de slots de GUI para uma caixa.
     * @param livres true = slots vazios (para /caixa item); false = slots ocupados (para /caixa removeitem).
     */
    private List<String> slotSuggestions(String crateName, boolean livres) {
        Crate crate = plugin.getCrateManager().getCrate(crateName.toLowerCase());
        if (crate == null) return List.of();
        List<Integer> slots = CrateGUI.getRewardSlots();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack it = crate.getItens().get(i);
            boolean ocupado = it != null && !it.getType().isAir();
            if (livres != ocupado) out.add(String.valueOf(slots.get(i)));
        }
        return out;
    }
}