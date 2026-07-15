package com.psdk.pitems;

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
import java.util.Map;
import java.util.stream.Collectors;

public class PItemCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public PItemCommand(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("psdk.pitem")) {
            sender.sendMessage(mm.deserialize("<#FF0000>Voce nao tem permissao para isso!"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<#fcc850>Uso: /pitem <jogador> <item> [tempo]"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(mm.deserialize("<#FF0000>Jogador nao encontrado!"));
            return true;
        }

        String itemArg = args[1];

        // O tempo é OPCIONAL:
        //   sem tempo   → item PERMANENTE (não expira, sem "ITEM EXCLUSIVO"/tempo restante);
        //   com tempo   → item TEMPORÁRIO (sistema de expiração já existente).
        // Entregar o item NUNCA inicia o cooldown da habilidade.
        boolean hasTime = args.length >= 3;
        long duration = 0L;
        String timeArg = null;
        if (hasTime) {
            timeArg = args[2];
            duration = PSDKItems.parseTime(timeArg);
            if (duration <= 0) {
                sender.sendMessage(mm.deserialize("<#FF0000>Tempo invalido! Exemplos: 10d, 5h, 1d12h"));
                return true;
            }
        }

        ItemStack item;

        // nexo:<id>  →  cria item Nexo puro sem tipo registrado no enum (sempre temporário)
        if (itemArg.startsWith("nexo:")) {
            if (!hasTime) {
                sender.sendMessage(mm.deserialize("<#FF0000>Itens nexo exigem um tempo! Uso: /pitem <jogador> nexo:<id> <tempo>"));
                return true;
            }
            String nexoId = itemArg.substring(5);
            item = tryCreateNexoRaw(nexoId, duration);
            if (item == null) {
                sender.sendMessage(mm.deserialize("<#FF0000>Item Nexo <#fcc850>" + nexoId + " <#FF0000>nao encontrado!"));
                return true;
            }
        } else {
            PSDKItems.ItemType type = resolveType(itemArg);
            if (type == null) {
                sender.sendMessage(mm.deserialize("<#FF0000>Item <#fcc850>" + itemArg + " <#FF0000>nao encontrado!"));
                sender.sendMessage(mm.deserialize("<#a4a4a4>Itens disponiveis: " + Arrays.stream(PSDKItems.ItemType.values())
                        .map(PSDKItems.ItemType::getId)
                        .collect(Collectors.joining(", "))));
                return true;
            }

            // PSDKItems.create() aplica lore, PDC (item_type) e brilho corretamente.
            // duration <= 0 → versão permanente; duration > 0 → versão temporária.
            item = PSDKItems.create(type, duration);
        }

        Map<Integer, ItemStack> overflow = target.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            for (ItemStack drop : overflow.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), drop);
            }
            sender.sendMessage(mm.deserialize("<#fcc850>Inventario cheio, item dropado no chao!"));
        }

        if (hasTime) {
            sender.sendMessage(mm.deserialize("<#10fc46>Item <#fcc850>" + itemArg + " <#10fc46>dado para <#fcc850>"
                    + target.getName() + " <#10fc46>com duracao de <#fcc850>" + timeArg + "<#10fc46>!"));
        } else {
            sender.sendMessage(mm.deserialize("<#10fc46>Item <#fcc850>" + itemArg + " <#10fc46>(permanente) dado para <#fcc850>"
                    + target.getName() + "<#10fc46>!"));
        }
        return true;
    }

    /**
     * Resolve o argumento do item em um {@link PSDKItems.ItemType}, aceitando o id
     * canônico, o nexoId e os aliases amigáveis dos três itens especiais.
     */
    private PSDKItems.ItemType resolveType(String itemArg) {
        switch (itemArg.toLowerCase()) {
            // Os TRÊS nomes oficiais dos itens especiais (sem aliases/duplicatas).
            case "jaula":
                return PSDKItems.ItemType.JAULA;
            case "cadeia":
                return PSDKItems.ItemType.TRAP;
            case "troca_posicao":
                return PSDKItems.ItemType.TROCA_POSICAO;
            default:
                // Demais itens: id canônico (case-insensitive) ou nexoId.
                PSDKItems.ItemType type = PSDKItems.ItemType.fromId(itemArg);
                if (type == null) {
                    for (PSDKItems.ItemType t : PSDKItems.ItemType.values()) {
                        if (t.getNexoId() != null && t.getNexoId().equalsIgnoreCase(itemArg)) {
                            type = t;
                            break;
                        }
                    }
                }
                // Os três itens especiais SÓ podem ser entregues pelos nomes oficiais acima
                // (bloqueia nomes internos/antigos como "TRAP" de entregarem a Cadeia).
                if (type == PSDKItems.ItemType.JAULA
                        || type == PSDKItems.ItemType.TRAP
                        || type == PSDKItems.ItemType.TROCA_POSICAO) {
                    return null;
                }
                return type;
        }
    }

    /**
     * Cria um item Nexo "puro" (sem tipo registrado no enum) e grava
     * apenas o keyItemType e keyExpireTime no PDC para que o expire-task
     * consiga removê-lo quando o tempo acabar.
     */
    private ItemStack tryCreateNexoRaw(String nexoId, long durationMillis) {
        try {
            ItemStack result = PSDKItems.buildNexoItem(nexoId);
            if (result == null) return null;

            var meta = result.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(
                        PSDKItems.getKeyItemType(),
                        org.bukkit.persistence.PersistentDataType.STRING,
                        "nexo:" + nexoId);
                meta.getPersistentDataContainer().set(
                        PSDKItems.getKeyExpireTime(),
                        org.bukkit.persistence.PersistentDataType.LONG,
                        System.currentTimeMillis() + durationMillis);
                result.setItemMeta(meta);
            }
            return result;

        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("psdk.pitem")) return List.of();

        if (args.length == 1) {
            return filter(
                    Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()),
                    args[0]);
        }

        if (args.length == 2) {
            List<String> items = new ArrayList<>();
            for (PSDKItems.ItemType type : PSDKItems.ItemType.values()) {
                // Os três itens especiais entram só pelos nomes oficiais (adicionados abaixo),
                // evitando sugerir nomes internos/antigos como "TRAP".
                if (type == PSDKItems.ItemType.TRAP
                        || type == PSDKItems.ItemType.JAULA
                        || type == PSDKItems.ItemType.TROCA_POSICAO) continue;
                items.add(type.getId());
            }
            items.add("jaula");
            items.add("cadeia");
            items.add("troca_posicao");
            return filter(items, args[1]);
        }

        if (args.length == 3) {
            return filter(List.of("10d", "7d", "5d", "3d", "1d", "12h", "1d12h"), args[2]);
        }

        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}