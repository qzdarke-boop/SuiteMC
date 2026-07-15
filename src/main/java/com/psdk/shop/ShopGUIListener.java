package com.psdk.shop;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShopGUIListener implements Listener {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private enum Screen { MAIN, SUBMENU, CATEGORY, QUANTITY, SPECIAL }
    private record ShopState(Screen screen, String category, int index, int page) {}

    /**
     * Retorna a categoria-pai para navegação de "Voltar" no sub-menu.
     * armas_* → armas | armaduras_* → armaduras | utilitarios_* → utilitarios
     * misto_* (misto_variados / misto_especiais) → misto (seletor intermediário)
     */
    private static String parentOf(String category) {
        if (category == null) return null;
        if (category.startsWith("misto_"))                  return "misto";
        if (category.startsWith("armaduras_"))              return "armaduras";
        if (category.startsWith("armas_diamante_"))         return "armas_diamante";
        if (category.startsWith("armas_netherite_"))        return "armas_netherite";
        if (category.startsWith("armas_"))                  return "armas";
        if (category.startsWith("utilitarios_picaretas_"))  return "utilitarios_picaretas";
        if (category.startsWith("utilitarios_pas_"))        return "utilitarios_pas";
        if (category.startsWith("utilitarios_"))            return "utilitarios";
        return null;
    }

    private final PSDK plugin;
    private final Map<UUID, ShopState> states = new HashMap<>();
    private final java.util.Set<UUID> transitioning = new java.util.HashSet<>();

    public ShopGUIListener(PSDK plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopGUI)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getInventory()) return;

        int slot = event.getSlot();
        ShopState state = states.getOrDefault(player.getUniqueId(), new ShopState(Screen.MAIN, null, -1, 0));
        ShopManager sm  = plugin.getShopManager();

        switch (state.screen) {
            case MAIN -> {
                if (slot == 31) { player.closeInventory(); return; }

                // Categorias que têm sub-menu: armas (10), armaduras (11), utilitarios (15), misto (16)
                if (slot == 10) { open(player, new ShopState(Screen.SUBMENU, "armas",       -1, 0), ShopGUI.buildSubMenu(sm, "armas"));       return; }
                if (slot == 11) { open(player, new ShopState(Screen.SUBMENU, "armaduras",   -1, 0), ShopGUI.buildSubMenu(sm, "armaduras"));   return; }
                if (slot == 15) { open(player, new ShopState(Screen.SUBMENU, "utilitarios", -1, 0), ShopGUI.buildSubMenu(sm, "utilitarios")); return; }
                // Misto agora abre o SELETOR (Variados / Itens Especiais), não a lista direto.
                if (slot == 16) { open(player, new ShopState(Screen.SUBMENU, "misto",       -1, 0), ShopGUI.buildSubMenu(sm, "misto"));       return; }

                // Categorias diretas: blocos(12), pocoes(13), comida(14)
                String cat = getCategoryForSlot(slot);
                if (cat != null) openCategory(player, sm, cat, 0);
            }

            case SUBMENU -> {
                if (slot == 31) {
                    // Se estiver num sub-menu de nível 2 (picaretas/pás), volta pro nível 1
                    String grandParent = parentOf(state.category);
                    if (grandParent != null && !grandParent.equals(state.category)) {
                        open(player, new ShopState(Screen.SUBMENU, grandParent, -1, 0), ShopGUI.buildSubMenu(sm, grandParent));
                    } else {
                        open(player, new ShopState(Screen.MAIN, null, -1, 0), ShopGUI.buildMain(sm));
                    }
                    return;
                }
                String sub = subCategoryForSlot(state.category, slot);
                if (sub != null) {
                    if (sub.equals("misto_especiais")) {
                        // Itens Especiais: tela própria (itens reais da factory), não é categoria comum.
                        open(player, new ShopState(Screen.SPECIAL, "misto_especiais", -1, 0), ShopGUI.buildSpecial(sm));
                    } else if (hasSubMenu(sub)) {
                        // Se o sub também tem sub-menu próprio, abre sub-menu.
                        open(player, new ShopState(Screen.SUBMENU, sub, -1, 0), ShopGUI.buildSubMenu(sm, sub));
                    } else {
                        openCategory(player, sm, sub, 0);
                    }
                }
            }

            case SPECIAL -> {
                if (slot == 31) { // Voltar → seletor do Misto
                    open(player, new ShopState(Screen.SUBMENU, "misto", -1, 0), ShopGUI.buildSubMenu(sm, "misto"));
                    return;
                }
                ShopManager.SpecialEntry se = sm.getSpecialBySlot(slot);
                if (se != null) buySpecial(player, se);
            }

            case CATEGORY -> {
                if (slot == 49) { // Voltar
                    String parent = parentOf(state.category);
                    if (parent != null) open(player, new ShopState(Screen.SUBMENU, parent, -1, 0), ShopGUI.buildSubMenu(sm, parent));
                    else                open(player, new ShopState(Screen.MAIN, null, -1, 0),       ShopGUI.buildMain(sm));
                    return;
                }
                if (slot == 48 && state.page > 0) {
                    openCategory(player, sm, state.category, state.page - 1);
                    return;
                }
                if (slot == 50) {
                    int maxPage = ShopGUI.maxPage(state.category, sm.getItems(state.category).size());
                    if (state.page < maxPage) openCategory(player, sm, state.category, state.page + 1);
                    return;
                }
                int itemIndex = getItemIndexForSlot(state.category, slot, state.page);
                if (itemIndex >= 0 && itemIndex < sm.getItems(state.category).size()
                        && sm.getEntry(state.category, itemIndex) != null) {
                    open(player,
                         new ShopState(Screen.QUANTITY, state.category, itemIndex, state.page),
                         ShopGUI.buildQuantity(sm, state.category, itemIndex, player));
                }
            }

            case QUANTITY -> {
                if (slot == 27) {
                    openCategory(player, sm, state.category, state.page);
                    return;
                }
                int qty = switch (slot) {
                    case 12 -> 1;
                    case 13 -> 5;
                    case 14 -> 10;
                    case 15 -> 32;
                    default -> 0;
                };
                if (qty > 0) buy(player, state.category, state.index, qty);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ShopGUI) event.setCancelled(true);
    }

    private void open(Player player, ShopState state, org.bukkit.inventory.Inventory inv) {
        states.put(player.getUniqueId(), state);
        transitioning.add(player.getUniqueId());
        player.openInventory(inv);
        transitioning.remove(player.getUniqueId());
    }

    private void openCategory(Player player, ShopManager sm, String category, int page) {
        open(player, new ShopState(Screen.CATEGORY, category, -1, page), ShopGUI.buildCategory(sm, category, page));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Mapeamento de slots → sub-categorias
    // ─────────────────────────────────────────────────────────────────────────

    /** Sub-categorias que abrem outro sub-menu (mais um nível) em vez de ir direto pra lista. */
    private static boolean hasSubMenu(String cat) {
        return cat.equals("armas_diamante") || cat.equals("armas_netherite")
                || cat.equals("utilitarios_picaretas") || cat.equals("utilitarios_pas");
    }

    private String subCategoryForSlot(String parent, int slot) {
        return switch (parent) {
            case "misto" -> {
                if (slot == ShopManager.MISTO_VARIADOS_SLOT)  yield "misto_variados";
                if (slot == ShopManager.MISTO_ESPECIAIS_SLOT) yield "misto_especiais";
                yield null;
            }
            case "armas" -> switch (slot) {
                case 10 -> "armas_diamante";
                case 13 -> "armas_netherite";
                case 16 -> "armas_outros";
                default -> null;
            };
            case "armas_diamante" -> switch (slot) {
                case 11 -> "armas_diamante_espada";
                case 15 -> "armas_diamante_machado";
                default -> null;
            };
            case "armas_netherite" -> switch (slot) {
                case 11 -> "armas_netherite_espada";
                case 15 -> "armas_netherite_machado";
                default -> null;
            };
            case "armaduras" -> switch (slot) {
                case 11 -> "armaduras_diamante";
                case 15 -> "armaduras_netherite";
                default -> null;
            };
            case "utilitarios" -> switch (slot) {
                case 13 -> "utilitarios_picaretas";   // botão Picaretas (slot 13) — alinhado com o GUI
                default -> null;
            };
            case "utilitarios_picaretas" -> switch (slot) {
                case 11 -> "utilitarios_picaretas_diamante";
                case 15 -> "utilitarios_picaretas_netherite";
                default -> null;
            };
            case "utilitarios_pas" -> switch (slot) {
                case 11 -> "utilitarios_pas_diamante";
                case 15 -> "utilitarios_pas_netherite";
                default -> null;
            };
            default -> null;
        };
    }

    /** Categorias sem sub-menu acessíveis direto do menu principal. */
    private String getCategoryForSlot(int slot) {
        return switch (slot) {
            case 12 -> "blocos";
            case 13 -> "pocoes";
            case 14 -> "comida";
            // slot 16 (Misto) agora abre o seletor Variados/Itens Especiais — tratado no MAIN.
            case 22 -> "encantamentos"; // slot destacado, logo acima do Fechar
            default -> null;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Compra
    // ─────────────────────────────────────────────────────────────────────────

    private void buy(Player player, String category, int index, int qty) {
        ShopManager.ShopEntry entry = plugin.getShopManager().getEntry(category, index);
        if (entry == null) return;

        double total = qty * entry.price();
        if (!plugin.getEconomyManager().removeCoins(player.getUniqueId(), total)) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não tem coins suficientes!"));
            return;
        }

        ItemStack item = new ItemStack(entry.material(), qty);
        if (!entry.enchantments().isEmpty()) {
            var meta = item.getItemMeta();
            if (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta storageMeta) {
                // Livros encantados guardam o encantamento como STORED (aplicável em
                // bigorna), não como encantamento ativo do próprio item.
                for (Map.Entry<Enchantment, Integer> ench : entry.enchantments().entrySet()) {
                    storageMeta.addStoredEnchant(ench.getKey(), ench.getValue(), true);
                }
                item.setItemMeta(storageMeta);
            } else if (meta != null) {
                for (Map.Entry<Enchantment, Integer> ench : entry.enchantments().entrySet()) {
                    meta.addEnchant(ench.getKey(), ench.getValue(), true);
                }
                item.setItemMeta(meta);
            }
        }
        ShopManager.applyPotion(item, entry.potion());

        // TNT da loja: explode (dano + knockback) mas NÃO quebra blocos. Tagueia o
        // item para o SafeTntListener reconhecer quando for colocada e detonada.
        if (entry.material() == org.bukkit.Material.TNT) {
            SafeTntListener.tagItem(plugin, item);
        }

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack drop : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }

        player.sendMessage(mm.deserialize("<#10fc46>Compra realizada!"));

        // Reabre a tela de quantidade com saldo atualizado
        ShopState state = states.get(player.getUniqueId());
        if (state != null) {
            transitioning.add(player.getUniqueId());
            player.openInventory(ShopGUI.buildQuantity(plugin.getShopManager(), category, index, player));
            transitioning.remove(player.getUniqueId());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Compra de item especial (Misto → Itens Especiais)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Compra um item especial: mesmo fluxo de economia/entrega das demais categorias.
     * O item entregue é uma instância NOVA e limpa da factory oficial (versão
     * PERMANENTE, com PDC/habilidade corretos) — sem as linhas de exibição do menu e
     * SEM iniciar cooldown (o cooldown só começa no uso válido da habilidade).
     */
    private void buySpecial(Player player, ShopManager.SpecialEntry entry) {
        if (entry == null) return;

        double price = entry.price();
        if (!plugin.getEconomyManager().removeCoins(player.getUniqueId(), price)) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não tem coins suficientes!"));
            return;
        }

        // Item real da factory (permanente): ID interno, PDC, brilho, lore e habilidade.
        ItemStack item = com.psdk.pitems.PSDKItems.create(entry.type());

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack drop : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }

        player.sendMessage(mm.deserialize("<#10fc46>Compra realizada!"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Index de item por slot
    // ─────────────────────────────────────────────────────────────────────────

    private int getItemIndexForSlot(String category, int slot, int page) {
        if (ShopGUI.isColumnArmor(category)) {
            int total = plugin.getShopManager().getItems(category).size();
            int varPerPiece = total / ShopManager.ARMOR_PIECES;
            return armorIndex(slot, page, varPerPiece);
        }
        int[] slots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) return (page * 28) + i;
        }
        return -1;
    }

    /**
     * Mapeia um slot do layout das armaduras → índice na lista agrupada.
     * Cada COLUNA é um set: linha 1=capacete, 2=peitoral, 3=calça, 4=bota.
     * A coluna (offset 1..7) indica a variação; colunas à direita = melhores.
     */
    private static int armorIndex(int slot, int page, int varPerPiece) {
        int guiRow = slot / 9;   // 1=capacete, 2=peitoral, 3=calça, 4=bota
        int colOffset = slot % 9; // 1..7
        if (guiRow < 1 || guiRow > ShopManager.ARMOR_PIECES) return -1;
        if (colOffset < 1 || colOffset > ShopGUI.ARMOR_COLS_PER_PAGE) return -1;
        int pieceIdx = guiRow - 1;       // 0=capacete .. 3=bota
        int c = colOffset - 1;           // 0..6
        int varIdx = page * ShopGUI.ARMOR_COLS_PER_PAGE + c;
        if (varIdx >= varPerPiece) return -1;
        return pieceIdx * varPerPiece + varIdx;
    }

    // ─────────────────────────────────────────────────────────────────────────

    public void trackOpen(UUID uuid) {
        states.put(uuid, new ShopState(Screen.MAIN, null, -1, 0));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ShopGUI) {
            UUID uuid = event.getPlayer().getUniqueId();
            if (!transitioning.contains(uuid)) {
                states.remove(uuid);
            }
        }
    }
}