package com.psdk.region;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.stream.Collectors;

public class RegionCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;
    private final NamespacedKey wandKey;

    public RegionCommand(PSDK plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "psdk_region_wand");
    }

    private RegionManager rm() {
        return plugin.getRegionManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<#FF0000>Este comando é apenas para jogadores."));
            return true;
        }
        if (!player.hasPermission("psdk.regiao")) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não tem permissão para isso."));
            return true;
        }
        if (args.length == 0) { sendUsage(player); return true; }

        RegionManager rm = plugin.getRegionManager();

        switch (args[0].toLowerCase()) {
            case "wand" -> handleWand(player);
            case "criar" -> handleCriar(player, args, rm);
            case "deletar" -> handleDeletar(player, args, rm);
            case "flag" -> handleFlag(player, args, rm);
            case "info" -> handleInfo(player, args, rm);
            case "list" -> handleList(player, rm);
            case "flags" -> handleFlags(player);
            case "prioridade" -> handlePrioridade(player, args, rm);
            case "settp" -> handleSetTp(player, args, rm);
            case "removetp" -> handleRemoveTp(player, args, rm);
            default -> sendUsage(player);
        }
        return true;
    }

    private void handleWand(Player player) {
        player.getInventory().addItem(createWand());
        player.sendMessage(mm.deserialize("<#10fc46>Você recebeu a ferramenta de seleção de regiões."));
    }

    private void handleCriar(Player player, String[] args, RegionManager rm) {
        if (args.length < 2) {
            player.sendMessage(mm.deserialize("<#FF0000>Use: /regiao criar <nome>"));
            return;
        }
        String name = args[1];
        if (rm.hasRegion(name)) {
            player.sendMessage(mm.deserialize("<#FF0000>Já existe uma região com esse nome."));
            return;
        }
        if (!rm.hasBothPositions(player.getUniqueId())) {
            player.sendMessage(mm.deserialize("<#FF0000>Selecione pos1 e pos2 com a wand primeiro."));
            return;
        }
        Location p1 = rm.getPos1(player.getUniqueId());
        Location p2 = rm.getPos2(player.getUniqueId());
        if (p1.getWorld() == null || !p1.getWorld().equals(p2.getWorld())) {
            player.sendMessage(mm.deserialize("<#FF0000>As duas posições devem estar no mesmo mundo."));
            return;
        }
        Region region = new Region(name, p1.getWorld().getName(),
                p1.getBlockX(), p1.getBlockY(), p1.getBlockZ(),
                p2.getBlockX(), p2.getBlockY(), p2.getBlockZ());
        rm.saveRegion(region);
        player.sendMessage(mm.deserialize("<#10fc46>Regiao <#fcc850>" + name + " <#10fc46>criada com sucesso!"));
    }

    private void handleDeletar(Player player, String[] args, RegionManager rm) {
        if (args.length < 2) {
            player.sendMessage(mm.deserialize("<#FF0000>Use: /regiao deletar <nome>"));
            return;
        }
        String name = args[1];
        if (!rm.hasRegion(name)) {
            player.sendMessage(mm.deserialize("<#FF0000>Região não encontrada."));
            return;
        }
        rm.deleteRegion(name);
        player.sendMessage(mm.deserialize("<#10fc46>Regiao <#fcc850>" + name + " <#10fc46>deletada."));
    }

    private void handleFlag(Player player, String[] args, RegionManager rm) {
        if (args.length < 4) {
            player.sendMessage(mm.deserialize("<#FF0000>Use: /regiao flag <nome> <flag> <allow|deny>"));
            return;
        }
        Region region = rm.getRegion(args[1]);
        if (region == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Região não encontrada."));
            return;
        }
        RegionFlag flag;
        try {
            flag = RegionFlag.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(mm.deserialize("<#FF0000>Flag inválida. Use /regiao flags para ver as disponíveis."));
            return;
        }
        boolean allowed = args[3].equalsIgnoreCase("allow");
        region.setFlag(flag, allowed);
        rm.saveRegion(region);
        String state = allowed ? "<#10fc46>ALLOW" : "<#FF0000>DENY";
        player.sendMessage(mm.deserialize("<#fcc850>" + flag.name() + " <#a4a4a4>em <#fcc850>" + region.getName() + " <#a4a4a4>-> " + state));
    }

    private void handleInfo(Player player, String[] args, RegionManager rm) {
        if (args.length < 2) {
            player.sendMessage(mm.deserialize("<#FF0000>Use: /regiao info <nome>"));
            return;
        }
        Region region = rm.getRegion(args[1]);
        if (region == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Região não encontrada."));
            return;
        }
        player.sendMessage(mm.deserialize("<#efa600><bold>Regiao: " + region.getName()));
        player.sendMessage(mm.deserialize("<#a4a4a4>Mundo: <#fcc850>" + region.getWorld()));
        player.sendMessage(mm.deserialize("<#a4a4a4>De: <#fcc850>" + region.getX1() + ", " + region.getY1() + ", " + region.getZ1()
                + " <#a4a4a4>Ate: <#fcc850>" + region.getX2() + ", " + region.getY2() + ", " + region.getZ2()));
        player.sendMessage(mm.deserialize("<#a4a4a4>Prioridade: <#fcc850>" + region.getPriority()));
        if (region.hasEntryTp()) {
            player.sendMessage(mm.deserialize("<#a4a4a4>Entry TP: <#fcc850>" + region.getEntryTpWorld()
                    + " " + String.format("%.1f", region.getEntryTpX())
                    + ", " + String.format("%.1f", region.getEntryTpY())
                    + ", " + String.format("%.1f", region.getEntryTpZ())));
        }
        if (region.hasExitTp()) {
            player.sendMessage(mm.deserialize("<#a4a4a4>Exit TP: <#fcc850>" + region.getExitTpWorld()
                    + " " + String.format("%.1f", region.getExitTpX())
                    + ", " + String.format("%.1f", region.getExitTpY())
                    + ", " + String.format("%.1f", region.getExitTpZ())));
        }
        player.sendMessage(mm.deserialize("<#efa600>Flags:"));
        for (RegionFlag flag : RegionFlag.values()) {
            boolean allowed = region.isAllowed(flag);
            String state = allowed ? "<#10fc46>ALLOW" : "<#FF0000>DENY";
            player.sendMessage(mm.deserialize("  <#fcc850>" + flag.name() + " <#a4a4a4>-> " + state));
        }
    }

    private void handleList(Player player, RegionManager rm) {
        Collection<Region> all = rm.getAllRegions();
        if (all.isEmpty()) {
            player.sendMessage(mm.deserialize("<#FF0000>Nenhuma região cadastrada."));
            return;
        }
        player.sendMessage(mm.deserialize("<#efa600><bold>Regiões (" + all.size() + "):"));
        for (Region r : all) {
            player.sendMessage(mm.deserialize("<#fcc850>" + r.getName()
                    + " <#a4a4a4>- " + r.getWorld()
                    + " [" + r.getX1() + "," + r.getY1() + "," + r.getZ1()
                    + " -> " + r.getX2() + "," + r.getY2() + "," + r.getZ2()
                    + "] P:" + r.getPriority()));
        }
    }

    private void handleFlags(Player player) {
        player.sendMessage(mm.deserialize("<#efa600><bold>Flags disponíveis:"));
        for (RegionFlag flag : RegionFlag.values()) {
            player.sendMessage(mm.deserialize("  <#fcc850>" + flag.name()));
        }
    }

    private void handlePrioridade(Player player, String[] args, RegionManager rm) {
        if (args.length < 3) {
            player.sendMessage(mm.deserialize("<#FF0000>Use: /regiao prioridade <nome> <numero>"));
            return;
        }
        Region region = rm.getRegion(args[1]);
        if (region == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Região não encontrada."));
            return;
        }
        int prio;
        try {
            prio = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(mm.deserialize("<#FF0000>Número inválido."));
            return;
        }
        region.setPriority(prio);
        rm.saveRegion(region);
        player.sendMessage(mm.deserialize("<#10fc46>Prioridade de <#fcc850>" + region.getName()
                + " <#10fc46>definida para <#fcc850>" + prio + "<#10fc46>."));
    }

    private void handleSetTp(Player player, String[] args, RegionManager rm) {
        if (args.length < 3) {
            player.sendMessage(mm.deserialize("<#FF0000>Use: /regiao settp <nome> <entry|exit>"));
            return;
        }
        Region region = rm.getRegion(args[1]);
        if (region == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Região não encontrada."));
            return;
        }
        String type = args[2].toLowerCase();
        if (type.equals("entry")) {
            region.setEntryTp(player.getLocation());
            rm.saveRegion(region);
            player.sendMessage(mm.deserialize("<#10fc46>Entry TP de <#fcc850>" + region.getName()
                    + " <#10fc46>definido na sua posicao."));
        } else if (type.equals("exit")) {
            region.setExitTp(player.getLocation());
            rm.saveRegion(region);
            player.sendMessage(mm.deserialize("<#10fc46>Exit TP de <#fcc850>" + region.getName()
                    + " <#10fc46>definido na sua posicao."));
        } else {
            player.sendMessage(mm.deserialize("<#FF0000>Use 'entry' ou 'exit'."));
        }
    }

    private void handleRemoveTp(Player player, String[] args, RegionManager rm) {
        if (args.length < 3) {
            player.sendMessage(mm.deserialize("<#FF0000>Use: /regiao removetp <nome> <entry|exit>"));
            return;
        }
        Region region = rm.getRegion(args[1]);
        if (region == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Região não encontrada."));
            return;
        }
        String type = args[2].toLowerCase();
        if (type.equals("entry")) {
            region.clearEntryTp();
            rm.saveRegion(region);
            player.sendMessage(mm.deserialize("<#10fc46>Entry TP de <#fcc850>" + region.getName()
                    + " <#10fc46>removido."));
        } else if (type.equals("exit")) {
            region.clearExitTp();
            rm.saveRegion(region);
            player.sendMessage(mm.deserialize("<#10fc46>Exit TP de <#fcc850>" + region.getName()
                    + " <#10fc46>removido."));
        } else {
            player.sendMessage(mm.deserialize("<#FF0000>Use 'entry' ou 'exit'."));
        }
    }

    private void sendUsage(Player player) {
        player.sendMessage(mm.deserialize("<#efa600><bold>Comandos de Regiao:"));
        player.sendMessage(mm.deserialize("  <#fcc850>/regiao wand <#a4a4a4>- Receber ferramenta de seleção"));
        player.sendMessage(mm.deserialize("  <#fcc850>/regiao criar <nome> <#a4a4a4>- Criar regiao"));
        player.sendMessage(mm.deserialize("  <#fcc850>/regiao deletar <nome> <#a4a4a4>- Deletar regiao"));
        player.sendMessage(mm.deserialize("  <#fcc850>/regiao flag <nome> <flag> <allow|deny> <#a4a4a4>- Alterar flag"));
        player.sendMessage(mm.deserialize("  <#fcc850>/regiao info <nome> <#a4a4a4>- Ver detalhes da regiao"));
        player.sendMessage(mm.deserialize("  <#fcc850>/regiao list <#a4a4a4>- Listar todas as regioes"));
        player.sendMessage(mm.deserialize("  <#fcc850>/regiao flags <#a4a4a4>- Listar flags disponiveis"));
        player.sendMessage(mm.deserialize("  <#fcc850>/regiao prioridade <nome> <num> <#a4a4a4>- Definir prioridade"));
        player.sendMessage(mm.deserialize("  <#fcc850>/regiao settp <nome> <entry|exit> <#a4a4a4>- Definir teleporte"));
        player.sendMessage(mm.deserialize("  <#fcc850>/regiao removetp <nome> <entry|exit> <#a4a4a4>- Remover teleporte"));
    }

    // --- Wand helpers ---

    public ItemStack createWand() {
        ItemStack item = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(mm.deserialize("<!italic><#efa600><bold>Region Wand"));
            meta.lore(List.of(
                    mm.deserialize("<!italic><#fcc850>Clique Esquerdo: <#a4a4a4>Definir Pos1"),
                    mm.deserialize("<!italic><#fcc850>Clique Direito: <#a4a4a4>Definir Pos2")
            ));
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isRegionWand(ItemStack item) {
        if (item == null || item.getType() != Material.GOLDEN_AXE) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    public NamespacedKey getWandKey() {
        return wandKey;
    }

    // --- Tab complete ---

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("psdk.regiao")) return List.of();

        if (args.length == 1) {
            return filterStartsWith(List.of("wand", "criar", "deletar", "flag", "info", "list", "flags", "prioridade", "settp", "removetp"), args[0]);
        }

        String sub = args[0].toLowerCase();
        RegionManager rm = plugin.getRegionManager();

        if (args.length == 2) {
            return switch (sub) {
                case "deletar", "flag", "info", "prioridade", "settp", "removetp" ->
                        filterStartsWith(rm.getAllRegions().stream().map(Region::getName).toList(), args[1]);
                default -> List.of();
            };
        }

        if (args.length == 3) {
            return switch (sub) {
                case "flag" -> filterStartsWith(
                        Arrays.stream(RegionFlag.values()).map(f -> f.name().toLowerCase()).toList(), args[2]);
                case "settp", "removetp" -> filterStartsWith(List.of("entry", "exit"), args[2]);
                default -> List.of();
            };
        }

        if (args.length == 4 && sub.equals("flag")) {
            return filterStartsWith(List.of("allow", "deny"), args[3]);
        }

        return List.of();
    }

    private List<String> filterStartsWith(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(s -> s.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }
}
