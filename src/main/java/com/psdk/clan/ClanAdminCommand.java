package com.psdk.clan;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ClanAdminCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private final PSDK plugin;

    public ClanAdminCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    // ───────────────────── COMMAND ─────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("psdk.clan.admin")) {
            sender.sendMessage(mm.deserialize("<#FF0000>Sem permissão!"));
            return true;
        }

        if (args.length == 0) { showHelp(sender); return true; }

        return switch (args[0].toLowerCase()) {
            case "delete", "deletar"      -> deleteClan(sender, args);
            case "transfer", "transferir" -> transferOwnership(sender, args);
            case "color", "cor"           -> manageColor(sender, args);
            default -> { showHelp(sender); yield true; }
        };
    }

    // ───────────────────── TAB COMPLETE ─────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("psdk.clan.admin")) return List.of();

        // args[0] — subcomando principal
        if (args.length == 1) {
            return filter(List.of("delete", "transfer", "color"), args[0]);
        }

        // args[1]
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "delete", "deletar", "transfer", "transferir" -> {
                    List<String> tags = plugin.getClanManager().getAllClans()
                            .stream().map(Clan::getTag).collect(Collectors.toList());
                    yield filter(tags, args[1]);
                }
                case "color", "cor" -> filter(List.of("list", "add", "remove", "givekey", "givepacote", "pacote"), args[1]);
                default -> List.of();
            };
        }

        // args[2]
        if (args.length == 3) {
            return switch (args[0].toLowerCase()) {
                case "transfer", "transferir" ->
                    // /clanAdmin transfer <tag> <player>
                    filter(Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName).collect(Collectors.toList()), args[2]);

                case "color", "cor" -> switch (args[1].toLowerCase()) {
                    // /clanAdmin color remove <colorName>
                    case "remove"  -> filter(colorNames(), args[2]);
                    // /clanAdmin color givekey <colorName>   ← args[2] = cor
                    case "givekey" -> filter(allCommandColorNames(), args[2]);
                    // /clanAdmin color givepacote <player>   ← args[2] = jogador
                    case "givepacote" -> filter(Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName).collect(Collectors.toList()), args[2]);
                    default -> List.of();
                };

                default -> List.of();
            };
        }

        // args[3]
        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("color") || args[0].equalsIgnoreCase("cor")) {
                return switch (args[1].toLowerCase()) {
                    // /clanAdmin color add <name> <hex>
                    case "add" -> filter(List.of("<#FF0000>", "<#00FF00>", "<#0000FF>", "<#FFD700>", "<#FF69B4>"), args[3]);
                    // /clanAdmin color givekey <colorName> <player>   ← args[3] = jogador
                    case "givekey" -> filter(Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName).collect(Collectors.toList()), args[3]);
                    // /clanAdmin color givepacote <player> <tipo>     ← args[3] = tipo
                    case "givepacote" -> filter(List.of("solida", "gradiente", "animada", "qualquer"), args[3]);
                    default -> List.of();
                };
            }
        }

        // args[4]
        if (args.length == 5) {
            if (args[0].equalsIgnoreCase("color") || args[0].equalsIgnoreCase("cor")) {
                return switch (args[1].toLowerCase()) {
                    // /clanAdmin color add <name> <hex> <displayName>
                    case "add" -> List.of("<displayName>");
                    // /clanAdmin color givekey <colorName> <player> [amount]
                    case "givekey" -> List.of("1", "2", "5", "10", "64");
                    // /clanAdmin color givepacote <player> <tipo> [amount]
                    case "givepacote" -> List.of("1", "2", "5", "10", "64");
                    default -> List.of();
                };
            }
        }

        return List.of();
    }

    // ───────────────────── SUBCOMANDOS ─────────────────────

    private boolean deleteClan(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(mm.deserialize("<#fcc850>/clanAdmin delete <tag>")); return true; }

        String tag = args[1].toUpperCase();
        Clan clan = plugin.getClanManager().getClanByTag(tag);
        if (clan == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Clan <bold>" + tag + "</bold> não encontrado!")); return true;
        }

        if (plugin.getClanManager().deleteClan(clan.getId())) {
            sender.sendMessage(mm.deserialize("<#10fc46>Clan <bold>" + clan.getName() + "</bold> deletado!"));
            for (Player p : Bukkit.getOnlinePlayers()) {
                for (ClanMember m : clan.getMembers()) {
                    if (m.uuid().equals(p.getUniqueId())) {
                        p.sendMessage(mm.deserialize("<#FF0000>Seu clan foi deletado por um administrador!"));
                        break;
                    }
                }
            }
        } else {
            sender.sendMessage(mm.deserialize("<#FF0000>Erro ao deletar clan!"));
        }
        return true;
    }

    private boolean transferOwnership(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(mm.deserialize("<#fcc850>/clanAdmin transfer <tag> <player>")); return true;
        }

        Clan clan = plugin.getClanManager().getClanByTag(args[1].toUpperCase());
        if (clan == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Clan não encontrado!")); return true;
        }

        UUID targetUUID = Bukkit.getOfflinePlayer(args[2]).getUniqueId();
        boolean isMember = clan.getMembers().stream().anyMatch(m -> m.uuid().equals(targetUUID));
        if (!isMember) {
            sender.sendMessage(mm.deserialize("<#FF0000><bold>" + args[2] + "</bold> não é membro do clan!")); return true;
        }

        clan.setLeader(targetUUID);
        if (plugin.getClanManager().updateClanLeader(clan.getId(), targetUUID)) {
            sender.sendMessage(mm.deserialize("<#10fc46>Liderança de <bold>" + clan.getName() + "</bold> transferida para <bold>" + args[2] + "</bold>!"));
            Player newLeader = Bukkit.getPlayer(targetUUID);
            if (newLeader != null) {
                newLeader.sendMessage(mm.deserialize("<#fcc850>Você agora é o líder do clan <bold>" + clan.getName() + "</bold>!"));
            }
        } else {
            sender.sendMessage(mm.deserialize("<#FF0000>Erro ao transferir liderança!"));
        }
        return true;
    }

    private boolean manageColor(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<#fcc850>/clanAdmin color <list|add|remove|giveKey|givePacote|pacote> [args]")); return true;
        }

        switch (args[1].toLowerCase()) {
            case "list"       -> listColors(sender);
            case "add"        -> addColor(sender, args);
            case "remove"     -> removeColor(sender, args);
            case "givekey"    -> giveKey(sender, args);
            case "givepacote" -> givePacote(sender, args);
            case "pacote"     -> managePacote(sender, args);
            default           -> sender.sendMessage(mm.deserialize("<#FF0000>Ação desconhecida! Use: list, add, remove, giveKey, givePacote, pacote"));
        }
        return true;
    }

    private void listColors(CommandSender sender) {
        var colors = plugin.getColorManager().getAllColors();
        if (colors.isEmpty()) { sender.sendMessage(mm.deserialize("<#fcc850>Nenhuma cor registrada.")); return; }

        sender.sendMessage(mm.deserialize("<#fcc850><bold>═══════════════════════════════"));
        sender.sendMessage(mm.deserialize("<#fcc850><bold>Cores de Clan Disponíveis:"));
        for (ClanColor color : colors) {
            sender.sendMessage(mm.deserialize("  " + color.getColorHex() + color.getDisplayName()
                    + " <#848c94>(" + color.getName() + ")"));
        }
        sender.sendMessage(mm.deserialize("<#fcc850><bold>═══════════════════════════════"));
    }

    private void addColor(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(mm.deserialize("<#fcc850>/clanAdmin color add <name> <hex> <displayName>")); return;
        }

        String name        = args[2].toLowerCase();
        String hex         = args[3];
        String displayName = String.join(" ", Arrays.copyOfRange(args, 4, args.length));

        if (!hex.matches("^<#[0-9A-Fa-f]{6}>$")) {
            sender.sendMessage(mm.deserialize("<#FF0000>Formato inválido! Use: <#RRGGBB>")); return;
        }
        if (plugin.getColorManager().getColor(name) != null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Já existe uma cor com esse nome!")); return;
        }

        ClanColor color = new ClanColor(name, hex, displayName);
        color.setKeyDisplayName("<#fcc850>Chave de Cor - " + displayName);
        plugin.getColorManager().saveColor(color);
        sender.sendMessage(mm.deserialize("<#10fc46>Cor <bold>" + displayName + "</bold> criada!"));
    }

    private void removeColor(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(mm.deserialize("<#fcc850>/clanAdmin color remove <name>")); return;
        }

        String name = args[2].toLowerCase();
        if (plugin.getColorManager().getColor(name) == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Cor não encontrada!")); return;
        }

        plugin.getColorManager().deleteColor(name);
        sender.sendMessage(mm.deserialize("<#10fc46>Cor removida!"));
    }

    /**
     * Dá uma Chave de Cor física para um jogador.
     * Uso: /clanAdmin color giveKey <colorName> <player> [amount]
     */
    private void giveKey(CommandSender sender, String[] args) {
        // args: [color, givekey, <colorName>, <player>, [amount]]
        if (args.length < 4) {
            sender.sendMessage(mm.deserialize("<#fcc850>/clanAdmin color giveKey <colorName> <player> [amount]")); return;
        }

        String colorName = args[2].toLowerCase();
        ClanCommand.ClanColor color = ClanColorKeyManager.findCommandColor(colorName);
        if (color == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Cor <bold>" + colorName + "</bold> não encontrada nas listas de cores!"));
            sender.sendMessage(mm.deserialize("<#848c94>Use um nome exato das listas: branco, vermelho, arcoiris, fogo, rainbow, etc."));
            return;
        }

        Player target = Bukkit.getPlayer(args[3]);
        if (target == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Jogador não encontrado ou offline!")); return;
        }

        int amount = 1;
        if (args.length >= 5) {
            try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[4]))); }
            catch (NumberFormatException e) {
                sender.sendMessage(mm.deserialize("<#FF0000>Quantidade inválida!")); return;
            }
        }

        ClanColorKeyManager keyManager = new ClanColorKeyManager(plugin);
        ItemStack key = keyManager.createKeyItem(color, amount);
        var overflow = target.getInventory().addItem(key);
        if (!overflow.isEmpty()) {
            for (ItemStack drop : overflow.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), drop);
            }
            sender.sendMessage(mm.deserialize("<#fcc850>Inventário cheio — chave dropada no chão!"));
        }

        String colorDisplay = color.isGradient()
                ? color.hex() + color.name() + "</gradient>"
                : "<" + color.hex() + ">" + color.name();

        sender.sendMessage(mm.deserialize("<#10fc46>Chave de cor " + colorDisplay + " <#10fc46>enviada para <bold>" + target.getName() + "</bold>!"));
        target.sendMessage(mm.deserialize("<#fcc850>Você recebeu uma <bold>Chave de Cor</bold>: " + colorDisplay));
        target.sendMessage(mm.deserialize("<#848c94>Clique direito para desbloquear a cor!"));
    }

    /**
     * Dá um Pacotinho de Cor ao jogador.
     * Uso: /clanAdmin color givePacote <player> <solida|gradiente|animada|qualquer> [amount]
     */
    private void givePacote(CommandSender sender, String[] args) {
        // args: [color, givepacote, <player>, <tipo>, [amount]]
        if (args.length < 4) {
            sender.sendMessage(mm.deserialize("<#fcc850>/clanAdmin color givePacote <player> <solida|gradiente|animada|qualquer> [qtd]"));
            return;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Jogador não encontrado ou offline!"));
            return;
        }

        ClanColorKeyManager.PacketType type;
        try {
            type = ClanColorKeyManager.PacketType.valueOf(args[3].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(mm.deserialize("<#FF0000>Tipo inválido! Use: solida, gradiente, animada, qualquer"));
            return;
        }

        int amount = 1;
        if (args.length >= 5) {
            try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[4]))); }
            catch (NumberFormatException ex) {
                sender.sendMessage(mm.deserialize("<#FF0000>Quantidade inválida!")); return;
            }
        }

        ClanColorKeyManager keyManager = new ClanColorKeyManager(plugin);
        ItemStack pacote = keyManager.createPacketItem(type, amount);
        var overflow = target.getInventory().addItem(pacote);
        if (!overflow.isEmpty()) {
            overflow.values().forEach(drop -> target.getWorld().dropItemNaturally(target.getLocation(), drop));
            sender.sendMessage(mm.deserialize("<#fcc850>Inventário cheio! O pacotinho foi dropado no chão."));
        }
        sender.sendMessage(mm.deserialize("<#10fc46>Pacotinho <bold>" + type.label() + "</bold> × " + amount + " enviado para <bold>" + target.getName() + "</bold>!"));
        target.sendMessage(mm.deserialize("<#fcc850>Você recebeu " + amount + "× <bold>Pacotinho de Cor — " + type.label() + "</bold>! Clique com o botão direito para abrir."));
    }

    /**
     * /clanAdmin color pacote <create|delete|list|addcolor|removecolor|setnexo|setmat|giveitem>
     */
    private void managePacote(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(mm.deserialize("<#fcc850>/clanAdmin color pacote <create|delete|list|addcolor|removecolor|setnexo|setmat|giveitem>"));
            return;
        }
        ColorPacketManager cpm = plugin.getColorPacketManager();

        switch (args[2].toLowerCase()) {
            // ── list ──────────────────────────────────────
            case "list" -> {
                var packets = cpm.getAllPackets();
                if (packets.isEmpty()) { sender.sendMessage(mm.deserialize("<#fcc850>Nenhum pacotinho customizado.")); return; }
                sender.sendMessage(mm.deserialize("<#fcc850><bold>Pacotinhos:"));
                for (var p : packets) {
                    sender.sendMessage(mm.deserialize("  <white>" + p.name() + " <#848c94>- " + p.displayName()
                            + " <dark_gray>(" + p.colorNames().size() + " cores)"));
                }
            }
            // ── create <name> <displayName> ───────────────
            case "create" -> {
                if (args.length < 5) { sender.sendMessage(mm.deserialize("<#fcc850>/clanAdmin color pacote create <name> <displayName>")); return; }
                String name = args[3].toLowerCase();
                String display = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
                if (cpm.getPacket(name) != null) { sender.sendMessage(mm.deserialize("<#FF0000>Já existe um pacotinho com esse nome!")); return; }
                if (cpm.createPacket(name, display)) {
                    sender.sendMessage(mm.deserialize("<#10fc46>Pacotinho <bold>" + name + "</bold> criado!"));
                } else {
                    sender.sendMessage(mm.deserialize("<#FF0000>Erro ao criar pacotinho."));
                }
            }
            // ── delete <name> ─────────────────────────────
            case "delete" -> {
                if (args.length < 4) { sender.sendMessage(mm.deserialize("<#fcc850>/clanAdmin color pacote delete <name>")); return; }
                if (cpm.deletePacket(args[3])) {
                    sender.sendMessage(mm.deserialize("<#10fc46>Pacotinho deletado!"));
                } else {
                    sender.sendMessage(mm.deserialize("<#FF0000>Erro ao deletar."));
                }
            }
            // ── addcolor <pacote> <colorName> ─────────────
            case "addcolor" -> {
                if (args.length < 5) { sender.sendMessage(mm.deserialize("<#fcc850>/clanAdmin color pacote addcolor <pacote> <corName>")); return; }
                String pName = args[3];
                String cName = args[4].toLowerCase();
                if (cpm.getPacket(pName) == null) { sender.sendMessage(mm.deserialize("<#FF0000>Pacotinho não encontrado!")); return; }
                if (ClanColorKeyManager.findCommandColor(cName) == null) { sender.sendMessage(mm.deserialize("<#FF0000>Cor não encontrada nas listas!")); return; }
                if (cpm.addColor(pName, cName)) {
                    sender.sendMessage(mm.deserialize("<#10fc46>Cor <bold>" + cName + "</bold> adicionada ao pacotinho <bold>" + pName + "</bold>!"));
                } else {
                    sender.sendMessage(mm.deserialize("<#FF0000>Cor já existe no pacotinho."));
                }
            }
            // ── removecolor <pacote> <colorName> ──────────
            case "removecolor" -> {
                if (args.length < 5) { sender.sendMessage(mm.deserialize("<#fcc850>/clanAdmin color pacote removecolor <pacote> <corName>")); return; }
                if (cpm.removeColor(args[3], args[4])) {
                    sender.sendMessage(mm.deserialize("<#10fc46>Cor removida do pacotinho!"));
                } else {
                    sender.sendMessage(mm.deserialize("<#FF0000>Cor não encontrada no pacotinho."));
                }
            }
            // ── setnexo <pacote> <nexoId|reset> ───────────
            case "setnexo" -> {
                if (args.length < 5) { sender.sendMessage(mm.deserialize("<#fcc850>/clanAdmin color pacote setnexo <pacote> <nexoId|reset>")); return; }
                String nexoId = args[4].equalsIgnoreCase("reset") ? "" : args[4];
                if (cpm.setNexoId(args[3], nexoId)) {
                    sender.sendMessage(mm.deserialize("<#10fc46>Nexo ID atualizado!"));
                } else {
                    sender.sendMessage(mm.deserialize("<#FF0000>Erro ao atualizar."));
                }
            }
            // ── setmat <pacote> <material> ─────────────────
            case "setmat" -> {
                if (args.length < 5) { sender.sendMessage(mm.deserialize("<#fcc850>/clanAdmin color pacote setmat <pacote> <material>")); return; }
                String mat = args[4].toUpperCase();
                if (org.bukkit.Material.matchMaterial(mat) == null) { sender.sendMessage(mm.deserialize("<#FF0000>Material inválido!")); return; }
                if (cpm.setMaterial(args[3], mat)) {
                    sender.sendMessage(mm.deserialize("<#10fc46>Material atualizado para <bold>" + mat + "</bold>!"));
                } else {
                    sender.sendMessage(mm.deserialize("<#FF0000>Erro ao atualizar."));
                }
            }
            // ── giveitem <pacote> <player> [amount] ────────
            case "giveitem" -> {
                if (args.length < 5) { sender.sendMessage(mm.deserialize("<#fcc850>/clanAdmin color pacote giveitem <pacote> <player> [qtd]")); return; }
                var packet = cpm.getPacket(args[3]);
                if (packet == null) { sender.sendMessage(mm.deserialize("<#FF0000>Pacotinho não encontrado!")); return; }
                Player target = Bukkit.getPlayer(args[4]);
                if (target == null) { sender.sendMessage(mm.deserialize("<#FF0000>Jogador offline!")); return; }
                int amount = 1;
                if (args.length >= 6) {
                    try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[5]))); }
                    catch (NumberFormatException ex) { sender.sendMessage(mm.deserialize("<#FF0000>Quantidade inválida!")); return; }
                }
                ItemStack pItem = cpm.createItem(packet, amount);
                var overflow = target.getInventory().addItem(pItem);
                if (!overflow.isEmpty()) overflow.values().forEach(d -> target.getWorld().dropItemNaturally(target.getLocation(), d));
                sender.sendMessage(mm.deserialize("<#10fc46>Pacotinho <bold>" + packet.name() + "</bold> × " + amount + " enviado para <bold>" + target.getName() + "</bold>!"));
                target.sendMessage(mm.deserialize("<#fcc850>Você recebeu " + amount + "× <bold>" + packet.displayName() + "</bold>!"));
            }
            default -> sender.sendMessage(mm.deserialize("<#FF0000>Uso: /clanAdmin color pacote <create|delete|list|addcolor|removecolor|setnexo|setmat|giveitem>"));
        }
    }

    // ───────────────────── HELPERS ─────────────────────

    private void showHelp(CommandSender sender) {
        sender.sendMessage(mm.deserialize("<#fcc850><bold>═══════════════════════════════"));
        sender.sendMessage(mm.deserialize("<#fcc850><bold>Comandos Admin de Clan:"));
        sender.sendMessage(mm.deserialize("  <#a4a4a4>/clanAdmin delete <tag>                            <#848c94>Deletar clan"));
        sender.sendMessage(mm.deserialize("  <#a4a4a4>/clanAdmin transfer <tag> <player>                 <#848c94>Transferir liderança"));
        sender.sendMessage(mm.deserialize("  <#a4a4a4>/clanAdmin color list                              <#848c94>Listar cores"));
        sender.sendMessage(mm.deserialize("  <#a4a4a4>/clanAdmin color add <n> <hex> <display>           <#848c94>Criar cor"));
        sender.sendMessage(mm.deserialize("  <#a4a4a4>/clanAdmin color remove <name>                     <#848c94>Remover cor"));
        sender.sendMessage(mm.deserialize("  <#a4a4a4>/clanAdmin color giveKey <cor> <player> [qt]       <#848c94>Dar chave específica"));
        sender.sendMessage(mm.deserialize("  <#a4a4a4>/clanAdmin color givePacote <player> <tipo> [qt]   <#848c94>Dar pacotinho padrão"));
        sender.sendMessage(mm.deserialize("  <#a4a4a4>/clanAdmin color pacote <create|...>               <#848c94>Pacotinhos customizados"));
        sender.sendMessage(mm.deserialize("<#fcc850><bold>═══════════════════════════════"));
    }

    private List<String> colorNames() {
        return plugin.getColorManager().getAllColors()
                .stream().map(ClanColor::getName).collect(Collectors.toList());
    }

    private List<String> allCommandColorNames() {
        List<String> names = new ArrayList<>();
        ClanCommand.CLAN_COLORS.forEach(c -> names.add(c.name()));
        ClanCommand.CLAN_GRADIENTS.forEach(c -> names.add(c.name()));
        ClanCommand.CLAN_ANIMATED.forEach(c -> names.add(c.name()));
        return names;
    }

    private List<String> filter(List<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}