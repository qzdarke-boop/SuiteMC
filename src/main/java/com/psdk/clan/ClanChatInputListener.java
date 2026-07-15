package com.psdk.clan;

import com.psdk.PSDK;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClanChatInputListener implements Listener {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private final PSDK plugin;
    private final Map<UUID, InputMode> waitingForInput = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> marketItems = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> renamingRoleIds = new ConcurrentHashMap<>();

    private enum InputMode { INVITE, MARKET_PRICE, ADD_ALLY, ADD_RIVAL, TREASURY_DEPOSIT, TREASURY_WITHDRAW, ROLE_CREATE, ROLE_RENAME }

    public ClanChatInputListener(PSDK plugin) {
        this.plugin = plugin;
    }

    public void startListening(Player player) {
        waitingForInput.put(player.getUniqueId(), InputMode.INVITE);
    }

    public void startMarketListing(Player player, ItemStack item) {
        waitingForInput.put(player.getUniqueId(), InputMode.MARKET_PRICE);
        marketItems.put(player.getUniqueId(), item);
    }

    /** Aguarda a tag do clan a adicionar como aliado ({@code rival=false}) ou rival. */
    public void startRelationInput(Player player, boolean rival) {
        waitingForInput.put(player.getUniqueId(), rival ? InputMode.ADD_RIVAL : InputMode.ADD_ALLY);
    }

    /** Aguarda o valor em coins do depósito ({@code deposit=true}) ou saque do tesouro. */
    public void startTreasuryInput(Player player, boolean deposit) {
        waitingForInput.put(player.getUniqueId(), deposit ? InputMode.TREASURY_DEPOSIT : InputMode.TREASURY_WITHDRAW);
    }

    /** Aguarda o nome do novo cargo. */
    public void startRoleCreate(Player player) {
        waitingForInput.put(player.getUniqueId(), InputMode.ROLE_CREATE);
    }

    /** Aguarda o novo nome do cargo {@code roleId}. */
    public void startRoleRename(Player player, int roleId) {
        waitingForInput.put(player.getUniqueId(), InputMode.ROLE_RENAME);
        renamingRoleIds.put(player.getUniqueId(), roleId);
    }

    public boolean isListening(UUID uuid) {
        return waitingForInput.containsKey(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        InputMode mode = waitingForInput.get(player.getUniqueId());
        if (mode == null) return;

        event.setCancelled(true);
        waitingForInput.remove(player.getUniqueId());

        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        if (input.equalsIgnoreCase("cancelar")) {
            // O item NUNCA saiu do inventário (a GUI passa um clone) -> NÃO devolver, senão duplica.
            marketItems.remove(player.getUniqueId());
            renamingRoleIds.remove(player.getUniqueId());
            player.sendMessage(mm.deserialize("<gray>Processo cancelado."));
            return;
        }

        switch (mode) {
            case INVITE -> handleInviteInput(player, input);
            case MARKET_PRICE -> handleMarketPriceInput(player, input);
            case ADD_ALLY -> handleRelationInput(player, input, false);
            case ADD_RIVAL -> handleRelationInput(player, input, true);
            case TREASURY_DEPOSIT -> handleTreasuryInput(player, input, true);
            case TREASURY_WITHDRAW -> handleTreasuryInput(player, input, false);
            case ROLE_CREATE -> handleRoleCreateInput(player, input);
            case ROLE_RENAME -> handleRoleRenameInput(player, input);
        }
    }

    private void handleRoleCreateInput(Player player, String input) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            ClanManager cm = plugin.getClanManager();
            Clan clan = cm.getClanByPlayer(player.getUniqueId());
            if (clan == null || !clan.getLeader().equals(player.getUniqueId())) {
                player.sendMessage(mm.deserialize("<#FF0000>Apenas o líder pode gerenciar cargos!"));
                return;
            }
            ClanManager.ClanRole role = cm.createRole(clan.getId(), input);
            if (role == null) {
                player.sendMessage(mm.deserialize("<#FF0000>Não foi possível criar o cargo. Verifique: nome com 2-16 caracteres, sem duplicar, e limite de "
                        + ClanManager.MAX_ROLES + " cargos."));
                ClanGUI.openRolesGUI(player, clan);
                return;
            }
            player.sendMessage(mm.deserialize("<#10fc46>Cargo <white>" + role.name() + "<#10fc46> criado! Configure as permissões."));
            cm.log(clan.getId(), player.getName(), "cargo", "Cargo '" + role.name() + "' criado");
            ClanGUI.openRoleEditGUI(player, clan, role);
        });
    }

    private void handleRoleRenameInput(Player player, String input) {
        Integer roleId = renamingRoleIds.remove(player.getUniqueId());
        if (roleId == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            ClanManager cm = plugin.getClanManager();
            Clan clan = cm.getClanByPlayer(player.getUniqueId());
            if (clan == null || !clan.getLeader().equals(player.getUniqueId())) {
                player.sendMessage(mm.deserialize("<#FF0000>Apenas o líder pode gerenciar cargos!"));
                return;
            }
            ClanManager.ClanRole role = cm.getRole(roleId);
            if (role == null || role.clanId() != clan.getId()) {
                player.sendMessage(mm.deserialize("<#FF0000>Cargo não encontrado."));
                return;
            }
            String oldName = role.name();
            if (cm.renameRole(roleId, input)) {
                ClanManager.ClanRole renamed = cm.getRole(roleId);
                player.sendMessage(mm.deserialize("<#10fc46>Cargo <white>" + oldName + "<#10fc46> renomeado para <white>"
                        + (renamed != null ? renamed.name() : input) + "<#10fc46>!"));
                cm.log(clan.getId(), player.getName(), "cargo",
                        "Cargo '" + oldName + "' renomeado para '" + (renamed != null ? renamed.name() : input) + "'");
                if (renamed != null) ClanGUI.openRoleEditGUI(player, clan, renamed);
                else ClanGUI.openRolesGUI(player, clan);
            } else {
                player.sendMessage(mm.deserialize("<#FF0000>Nome inválido ou já em uso (2-16 caracteres, sem < >)."));
                ClanGUI.openRoleEditGUI(player, clan, role);
            }
        });
    }

    private void handleRelationInput(Player player, String input, boolean rival) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            ClanManager cm = plugin.getClanManager();
            Clan clan = cm.getClanByPlayer(player.getUniqueId());
            if (clan == null || !clan.getLeader().equals(player.getUniqueId())) {
                player.sendMessage(mm.deserialize("<#FF0000>Apenas o líder pode gerenciar relações!"));
                return;
            }
            Clan other = cm.getClanByTag(input.toUpperCase());
            if (other == null) other = cm.getClanByTag(input);
            if (other == null) {
                player.sendMessage(mm.deserialize("<#FF0000>Clan com tag <white>" + input + "<#FF0000> não encontrado."));
                return;
            }
            if (other.getId() == clan.getId()) {
                player.sendMessage(mm.deserialize("<#FF0000>Você não pode adicionar o próprio clan!"));
                return;
            }

            if (rival) {
                if (cm.addRival(clan.getId(), other.getId())) {
                    player.sendMessage(mm.deserialize("<#e22c27>Clan <white>[" + other.getTag() + "]<#e22c27> agora é rival!"));
                    cm.log(clan.getId(), player.getName(), "rival", "[" + other.getTag() + "] marcado como rival");
                } else {
                    player.sendMessage(mm.deserialize("<#FF0000>Não foi possível adicionar a relação."));
                }
            } else {
                // Aliança agora é por PEDIDO: forma na hora só se o outro clan já tinha pedido pendente.
                String result = cm.requestAlly(clan.getId(), other.getId());
                Player otherLeader = Bukkit.getPlayer(other.getLeader());
                if ("allied".equals(result)) {
                    player.sendMessage(mm.deserialize("<#10fc46>Aliança formada com <white>[" + other.getTag() + "]<#10fc46>!"));
                    if (otherLeader != null) {
                        otherLeader.sendMessage(mm.deserialize("<#10fc46>Aliança formada com <white>[" + clan.getTag() + "]<#10fc46>!"));
                    }
                    cm.log(clan.getId(), player.getName(), "ally", "Aliança formada com [" + other.getTag() + "]");
                    cm.log(other.getId(), player.getName(), "ally", "Aliança formada com [" + clan.getTag() + "]");
                } else if ("requested".equals(result)) {
                    player.sendMessage(mm.deserialize("<#fcc850>Pedido de aliança enviado para <white>[" + other.getTag() + "]<#fcc850>. Aguardando aceite."));
                    if (otherLeader != null) {
                        otherLeader.sendMessage(mm.deserialize("<#fcc850>O clan <white>[" + clan.getTag() + "]<#fcc850> quer ser seu aliado! Veja em Relações."));
                    }
                } else {
                    player.sendMessage(mm.deserialize("<#FF0000>Não foi possível enviar o pedido (já são aliados?)."));
                }
            }
            ClanGUI.openAlliesAndRivalsGUI(player, clan, rival ? 1 : 0);
        });
    }

    private void handleTreasuryInput(Player player, String input, boolean deposit) {
        double amount;
        try {
            amount = Double.parseDouble(input.replace(",", "."));
        } catch (NumberFormatException e) {
            player.sendMessage(mm.deserialize("<#FF0000>Valor inválido! Digite um número."));
            return;
        }
        if (!Double.isFinite(amount) || amount < 1) {
            player.sendMessage(mm.deserialize("<#FF0000>O valor mínimo é 1 coin."));
            return;
        }
        final double value = Math.floor(amount);

        Bukkit.getScheduler().runTask(plugin, () -> {
            ClanManager cm = plugin.getClanManager();
            Clan clan = cm.getClanByPlayer(player.getUniqueId());
            if (clan == null) {
                player.sendMessage(mm.deserialize("<#FF0000>Você não está em um clan!"));
                return;
            }
            if (!deposit && !cm.hasPermission(clan.getId(), player.getUniqueId(), "treasury")) {
                player.sendMessage(mm.deserialize("<#FF0000>Apenas o líder ou membros autorizados podem sacar do tesouro!"));
                return;
            }

            String formatted = String.format("%,.0f", value).replace(",", ".");
            boolean ok = deposit
                    ? cm.depositToTreasury(clan.getId(), player.getUniqueId(), player.getName(), value)
                    : cm.withdrawFromTreasury(clan.getId(), player.getUniqueId(), player.getName(), value);
            if (ok) {
                player.sendMessage(mm.deserialize(deposit
                        ? "<#10fc46>Você depositou <#fcc850>" + formatted + " coins<#10fc46> no tesouro!"
                        : "<#10fc46>Você sacou <#fcc850>" + formatted + " coins<#10fc46> do tesouro!"));
                cm.log(clan.getId(), player.getName(), deposit ? "deposito" : "saque",
                        (deposit ? "Depositou " : "Sacou ") + formatted + " coins");
            } else {
                player.sendMessage(mm.deserialize(deposit
                        ? "<#FF0000>Você não tem coins suficientes!"
                        : "<#FF0000>O tesouro não tem coins suficientes!"));
            }
            ClanGUI.openTreasuryGUI(player, clan);
        });
    }

    private void handleInviteInput(Player player, String input) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            ClanManager cm = plugin.getClanManager();
            Clan clan = cm.getClanByPlayer(player.getUniqueId());
            if (clan == null) {
                player.sendMessage(mm.deserialize("<#FF0000>Você não está em um clan!"));
                return;
            }
            if (!cm.hasPermission(clan.getId(), player.getUniqueId(), "invite")) {
                player.sendMessage(mm.deserialize("<#FF0000>Você não tem permissão para convidar jogadores!"));
                return;
            }

            // Funciona com jogador ONLINE ou OFFLINE (busca o UUID mesmo offline).
            Player onlineTarget = Bukkit.getPlayer(input);
            UUID targetUuid;
            String targetName;
            if (onlineTarget != null) {
                targetUuid = onlineTarget.getUniqueId();
                targetName = onlineTarget.getName();
            } else {
                org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayer(input);
                if (!off.hasPlayedBefore()) {
                    player.sendMessage(mm.deserialize("<#FF0000>Jogador não encontrado (nunca entrou no servidor)."));
                    return;
                }
                targetUuid = off.getUniqueId();
                targetName = off.getName() != null ? off.getName() : input;
            }
            if (targetUuid.equals(player.getUniqueId())) {
                player.sendMessage(mm.deserialize("<#FF0000>Você não pode convidar a si mesmo!"));
                return;
            }
            if (cm.getClanByPlayer(targetUuid) != null) {
                player.sendMessage(mm.deserialize("<#FF0000>Este jogador já está em um clan!"));
                return;
            }
            int limite = ClanGUI.getMemberLimit(player);   // mesmo limite dinâmico do GUI
            if (clan.getMembers().size() >= limite) {
                player.sendMessage(mm.deserialize("<#FF0000>Seu clan já atingiu o limite de " + limite + " membros!"));
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
        });
    }

    private void handleMarketPriceInput(Player player, String input) {
        double price;
        try {
            price = Double.parseDouble(input);
        } catch (NumberFormatException e) {
            player.sendMessage(mm.deserialize("<#FF0000>Preço inválido! Digite um número."));
            marketItems.remove(player.getUniqueId());   // item segue no inventário — não devolver (evita dupe)
            return;
        }

        if (!Double.isFinite(price) || price <= 0 || price > 1000000) {
            player.sendMessage(mm.deserialize("<#FF0000>O preço deve ser entre 1 e 1.000.000 coins!"));
            marketItems.remove(player.getUniqueId());   // item segue no inventário — não devolver (evita dupe)
            return;
        }

        ItemStack item = marketItems.remove(player.getUniqueId());
        if (item == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Erro: item não encontrado."));
            return;
        }

        final double finalPrice = price;
        Bukkit.getScheduler().runTask(plugin, () -> {
            ClanManager cm = plugin.getClanManager();
            Clan clan = cm.getClanByPlayer(player.getUniqueId());
            if (clan == null) {
                player.sendMessage(mm.deserialize("<#FF0000>Você não está em um clan!"));
                return;   // item ainda NÃO foi removido aqui — não devolver (evita dupe)
            }

            // Só lista se o item REALMENTE saiu do inventário (senão duplica).
            ItemStack handItem = player.getInventory().getItemInMainHand();
            boolean removed;
            if (handItem.isSimilar(item)) {
                player.getInventory().setItemInMainHand(null);
                removed = true;
            } else {
                removed = player.getInventory().removeItem(item).isEmpty();   // map vazio = removeu tudo
            }
            if (!removed) {
                player.sendMessage(mm.deserialize("<#FF0000>O item não está mais na sua mão. Listagem cancelada."));
                return;
            }

            String serialized = ClanGUI.serializeItem(item);
            if (serialized.isEmpty()) {
                player.sendMessage(mm.deserialize("<#FF0000>Erro ao serializar o item."));
                player.getInventory().addItem(item);
                return;
            }

            if (cm.listMarketItem(clan.getId(), player.getUniqueId(), serialized, finalPrice)) {
                player.sendMessage(mm.deserialize("<#10fc46>Item listado no mercado por <#fcc850>" + String.format("%.0f", finalPrice) + " coins<#10fc46>!"));
            } else {
                player.sendMessage(mm.deserialize("<#FF0000>Erro ao listar item no mercado."));
                player.getInventory().addItem(item);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        waitingForInput.remove(uuid);
        marketItems.remove(uuid);   // item nunca saiu do inventário — só limpa o estado (não devolver = não duplica)
        renamingRoleIds.remove(uuid);
    }
}
