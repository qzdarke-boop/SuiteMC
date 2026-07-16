package com.psdk.clan;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Listener da GUI de clan. Roteamento por {@link ClanGUI#getGuiType()} para acompanhar
 * o layout novo (slots dispersos, baú de página única com trava, membros & solicitações,
 * aliados & rivais).
 */
public class ClanGUIListener implements Listener {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    /** Slots de conteúdo (5×3 central) compartilhados por vários menus. */
    private static final int[] GRID = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33};
    /** Slots de itens do mercado / aliados-rivais (3 linhas de 7). */
    private static final int[] WIDE_GRID = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final PSDK plugin;

    public ClanGUIListener(PSDK plugin) {
        this.plugin = plugin;
    }

    private static int indexOf(int[] slots, int slot) {
        for (int i = 0; i < slots.length; i++) if (slots[i] == slot) return i;
        return -1;
    }

    private static int pageSuffix(String guiType, String prefix) {
        if (guiType.startsWith(prefix)) {
            try { return Integer.parseInt(guiType.substring(prefix.length())); }
            catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ClanGUI gui)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String type = gui.getGuiType();

        // ── Baú: tratamento especial (slots de armazenamento livres) ──
        if (type.equals("chest")) {
            handleChestClick(event, player, gui);
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory()) return;

        int slot = event.getSlot();
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());

        if (type.equals("clan_menu")) {
            if (clan != null) handleClanMenu(player, clan, slot);
        } else if (type.startsWith("members_page_")) {
            if (clan != null) handleMembers(player, clan, slot, pageSuffix(type, "members_page_"), event.getClick());
        } else if (type.equals("requests")) {
            if (clan != null) handleRequests(player, clan, slot, event.getClick());
        } else if (type.startsWith("allies_page_")) {
            if (clan != null) handleAllies(player, clan, slot, pageSuffix(type, "allies_page_"), event.getClick());
        } else if (type.startsWith("public_clans_")) {
            handlePublicClans(player, slot, pageSuffix(type, "public_clans_"));
        } else if (type.equals("invites")) {
            handleInvites(player, slot, event.getClick());
        } else if (type.startsWith("color_menu_")) {
            if (clan != null) handleColor(player, clan, slot, pageSuffix(type, "color_menu_"));
        } else if (type.equals("permissions")) {
            if (clan != null) handlePermissions(player, clan, slot);
        } else if (type.equals("perm_edit")) {
            if (clan != null) handlePermEdit(player, clan, slot, event.getView().title());
        } else if (type.equals("market")) {
            if (clan != null) handleMarket(player, clan, slot, event.getClick());
        } else if (type.equals("treasury")) {
            if (clan != null) handleTreasury(player, clan, slot);
        } else if (type.startsWith("clan_tops_")) {
            handleClanTops(player, clan, slot, pageSuffix(type, "clan_tops_"));
        } else if (type.equals("logs")) {
            if (clan != null && slot == 49) ClanGUI.openClanMenu(player, clan);
        } else if (type.equals("roles")) {
            if (clan != null) handleRoles(player, clan, slot, event.getClick());
        } else if (type.equals("role_edit")) {
            if (clan != null) handleRoleEdit(player, clan, slot, gui.getRoleId());
        } else if (type.equals("role_select")) {
            if (clan != null) handleRoleSelect(player, clan, slot, gui);
        }
    }

    // ───────────────────────── Menu principal ─────────────────────────
    private void handleClanMenu(Player player, Clan clan, int slot) {
        ClanManager cm = plugin.getClanManager();
        boolean isLeader = clan.getLeader().equals(player.getUniqueId());

        switch (slot) {
            case 12 -> { ClanUI.clickSound(player); ClanGUI.openMembersAndRequestsGUI(player, clan, 0); }
            case 38 -> { ClanUI.clickSound(player); ClanGUI.openInvitesGUI(player); }
            case 43 -> {
                if (isLeader) { ClanUI.clickSound(player); ClanGUI.openColorGUI(player, clan); }
                else player.sendMessage(mm.deserialize("<gradient:#FF6B6B:#E22C27>✘</gradient> <#FF6B6B>Apenas o <white>líder <#FF6B6B>pode alterar a cor!"));
            }
            case 16 -> {
                if (isLeader) { ClanUI.clickSound(player); ClanGUI.openPermissionsGUI(player, clan); }
                else player.sendMessage(mm.deserialize("<gradient:#FF6B6B:#E22C27>✘</gradient> <#FF6B6B>Apenas o <white>líder <#FF6B6B>pode gerenciar permissões!"));
            }
            case 37 -> {
                if (isLeader) {
                    ClanUI.clickSound(player);
                    boolean next = !clan.isPublic();
                    cm.setClanPublic(clan.getId(), next);
                    clan.setPublic(next);
                    player.sendMessage(mm.deserialize(next
                            ? "<gradient:#10FC46:#3DFF8A>✔</gradient> <#10FC46>Seu clan agora é <bold>público</bold>!"
                            : "<gradient:#FF6B6B:#E22C27>✔</gradient> <#FF6B6B>Seu clan agora é <bold>privado</bold>!"));
                    ClanGUI.openClanMenu(player, clan);
                }
            }
            case 28 -> {
                if (isLeader || cm.hasPermission(clan.getId(), player.getUniqueId(), "chest")) {
                    ClanUI.clickSound(player);
                    ClanGUI.openClanChest(player, clan);
                } else {
                    player.sendMessage(mm.deserialize("<gradient:#FF6B6B:#E22C27>✘</gradient> <#FF6B6B>Sem permissão para o <white>baú<#FF6B6B>!"));
                }
            }
            case 29 -> {
                if (isLeader || cm.hasPermission(clan.getId(), player.getUniqueId(), "market")) {
                    ClanUI.clickSound(player);
                    ClanGUI.openMarketGUI(player, clan);
                } else {
                    player.sendMessage(mm.deserialize("<gradient:#FF6B6B:#E22C27>✘</gradient> <#FF6B6B>Sem permissão para o <white>mercado<#FF6B6B>!"));
                }
            }
            case 30 -> {
                if (isLeader || cm.hasPermission(clan.getId(), player.getUniqueId(), "pvp_toggle")) {
                    ClanUI.clickSound(player);
                    boolean next = !clan.isFriendlyFire();
                    cm.setFriendlyFire(clan.getId(), next);
                    clan.setFriendlyFire(next);
                    player.sendMessage(mm.deserialize(next
                            ? "<gradient:#FF6B6B:#FF9999>⚔</gradient> <#FF6B6B>PvP interno <bold>ativado</bold>!"
                            : "<gradient:#10FC46:#3DFF8A>🛡</gradient> <#10FC46>PvP interno <bold>desativado</bold>!"));
                    cm.log(clan.getId(), player.getName(), "pvp", "PvP interno " + (next ? "ativado" : "desativado"));
                    ClanGUI.openClanMenu(player, clan);
                } else {
                    player.sendMessage(mm.deserialize("<gradient:#FF6B6B:#E22C27>✘</gradient> <#FF6B6B>Sem permissão para alterar o <white>PvP<#FF6B6B>!"));
                }
            }
            case 34 -> {
                ClanUI.clickSound(player);
                if (isLeader) {
                    ClanConfirmGUI.openDisband(player, clan);
                } else {
                    cm.removeMember(clan.getId(), player.getUniqueId());
                    cm.log(clan.getId(), player.getName(), "sair", player.getName() + " saiu do clan");
                    player.closeInventory();
                    player.sendMessage(mm.deserialize("<gradient:#FF6B6B:#E22C27>✔</gradient> <#FF6B6B>Você saiu do clan."));
                }
            }
            case 15 -> { ClanUI.clickSound(player); ClanGUI.openAlliesAndRivalsGUI(player, clan, 0); }
            case 33 -> { ClanUI.clickSound(player); ClanGUI.openTreasuryGUI(player, clan); }
            case 39 -> { ClanUI.clickSound(player); ClanGUI.openLogsGUI(player, clan); }
            case 40 -> { ClanUI.clickSound(player); ClanGUI.openRolesGUI(player, clan); }
            case 42 -> { ClanUI.clickSound(player); ClanGUI.openClanTopsGUI(player, 0); }
        }
    }

    // ───────────────────────── Membros (paginado) ─────────────────────
    private void handleMembers(Player player, Clan clan, int slot, int page, ClickType click) {
        ClanManager cm = plugin.getClanManager();

        if (slot == 48) { ClanGUI.openClanMenu(player, clan); return; }
        if (slot == 45 && page > 0) { ClanGUI.openMembersGUI(player, clan, page - 1); return; }
        if (slot == 53) {
            List<ClanMember> members = clan.getMembers();
            int totalPages = Math.max(1, (int) Math.ceil(members.size() / (double) ClanGUI.MEMBERS_PER_PAGE));
            if (page + 1 < totalPages) {
                ClanGUI.openMembersGUI(player, clan, page + 1);
            } else {
                ClanGUI.openRequestsGUI(player, clan);
            }
            return;
        }
        if (slot == 50 && cm.hasPermission(clan.getId(), player.getUniqueId(), "invite")) {
            player.closeInventory();
            plugin.getClanChatInputListener().startListening(player);
            player.sendMessage(mm.deserialize("<#10fc46>Digite o nick do jogador para convidar para o clan."));
            player.sendMessage(mm.deserialize("<gray>Para anular o processo, digite 'cancelar'."));
            return;
        }

        int slotIdx = indexOf(ClanGUI.MEMBER_SLOTS, slot);
        if (slotIdx < 0) return;

        int memberIdx = page * ClanGUI.MEMBERS_PER_PAGE + slotIdx;
        List<ClanMember> members = clan.getMembers();
        if (memberIdx >= members.size()) return;
        ClanMember target = members.get(memberIdx);

        // Clique direito (sem shift): líder escolhe o cargo do membro.
        if (click == ClickType.RIGHT) {
            if (!clan.getLeader().equals(player.getUniqueId())) return;
            if (target.role().equals("lider")) return;
            ClanGUI.openRoleSelectGUI(player, clan, target, page);
            return;
        }

        if (!click.isShiftClick()) return;

        if (target.role().equals("lider")) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não pode expulsar o líder!"));
            return;
        }
        if (target.uuid().equals(player.getUniqueId())) {
            player.sendMessage(mm.deserialize("<#FF0000>Use o menu principal para sair do clan."));
            return;
        }
        if (!cm.hasPermission(clan.getId(), player.getUniqueId(), "kick")) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não tem permissão para expulsar membros!"));
            return;
        }

        ClanConfirmGUI.openKick(player, clan, target, page);
    }

    // ───────────────────────── Confirmações ───────────────────────────
    @EventHandler(priority = EventPriority.HIGH)
    public void onConfirmClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ClanConfirmGUI gui)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getInventory()) return;

        int slot = event.getSlot();
        ClanManager cm = plugin.getClanManager();
        Clan clan = cm.getClanByPlayer(player.getUniqueId());

        if (slot == ClanConfirmGUI.CANCEL_SLOT) {
            if (clan != null) ClanGUI.openClanMenu(player, clan);
            else player.closeInventory();
            return;
        }
        if (slot != ClanConfirmGUI.CONFIRM_SLOT) return;

        if (clan == null || clan.getId() != gui.getClanId()) {
            player.closeInventory();
            return;
        }

        if (gui.getAction() == ClanConfirmGUI.Action.DISBAND) {
            if (!clan.getLeader().equals(player.getUniqueId())) {
                player.closeInventory();
                return;
            }
            // Fecha GUIs de clan de todos os membros antes de apagar.
            for (ClanMember m : clan.getMembers()) ClanGUI.closeClanGuiFor(m.uuid());
            cm.deleteClan(clan.getId());
            player.closeInventory();
            player.sendMessage(mm.deserialize("<#FF0000>Seu clan foi desfeito."));
            return;
        }

        // KICK
        if (!cm.hasPermission(clan.getId(), player.getUniqueId(), "kick")) {
            player.closeInventory();
            return;
        }
        if (!cm.removeMember(clan.getId(), gui.getTargetUuid())) {
            player.sendMessage(mm.deserialize("<#FF0000>Não foi possível expulsar o membro."));
            player.closeInventory();
            return;
        }
        ClanGUI.closeClanGuiFor(gui.getTargetUuid());
        Player kicked = Bukkit.getPlayer(gui.getTargetUuid());
        if (kicked != null) {
            kicked.sendMessage(mm.deserialize("<#FF0000>Você foi expulso do clan <white>[" + clan.getTag() + "]<#FF0000>."));
        }
        player.sendMessage(mm.deserialize("<#10fc46>" + gui.getTargetName() + " foi expulso do clan."));
        cm.log(clan.getId(), player.getName(), "expulsar", gui.getTargetName() + " foi expulso do clan");

        Clan refreshed = cm.getClanByPlayer(player.getUniqueId());
        if (refreshed != null) ClanGUI.openMembersGUI(player, refreshed, gui.getReturnPage());
        else player.closeInventory();
    }

    // ───────────────────────── Solicitações ───────────────────────────
    private void handleRequests(Player player, Clan clan, int slot, ClickType click) {
        ClanManager cm = plugin.getClanManager();

        if (slot == 48) { ClanGUI.openClanMenu(player, clan); return; }
        if (slot == 45) { ClanGUI.openMembersGUI(player, clan, 0); return; }

        int idx = indexOf(GRID, slot);
        if (idx < 0) return;
        if (!cm.hasPermission(clan.getId(), player.getUniqueId(), "invite")) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não tem permissão para gerenciar solicitações!"));
            return;
        }
        List<ClanMember> pending = cm.getPendingRequests(clan.getId());
        if (idx >= pending.size()) return;
        ClanMember req = pending.get(idx);

        if (click.isShiftClick()) {
            cm.denyRequest(clan.getId(), req.uuid());
            player.sendMessage(mm.deserialize("<#FF0000>Solicitação de <#cbd1d7>" + req.name() + " <#FF0000>recusada."));
        } else {
            if (cm.acceptRequest(clan.getId(), req.uuid())) {
                player.sendMessage(mm.deserialize("<#10fc46>" + req.name() + " entrou no clan!"));
                Player joined = Bukkit.getPlayer(req.uuid());
                if (joined != null) joined.sendMessage(mm.deserialize("<#10fc46>Sua solicitação para <white>[" + clan.getTag() + "]<#10fc46> foi aceita!"));
                cm.log(clan.getId(), player.getName(), "entrar", req.name() + " entrou no clan");
            } else {
                player.sendMessage(mm.deserialize("<#FF0000>Não foi possível aceitar (clan cheio ou jogador já em um clan)."));
            }
        }
        ClanGUI.openRequestsGUI(player, clan);
    }

    // ───────────────────────── Aliados & Rivais ───────────────────────
    private void handleAllies(Player player, Clan clan, int slot, int page, ClickType click) {
        if (slot == 49) { ClanGUI.openClanMenu(player, clan); return; }
        if (page == 0 && slot == 26) { ClanGUI.openAlliesAndRivalsGUI(player, clan, 1); return; }
        if (page > 0 && slot == 18) { ClanGUI.openAlliesAndRivalsGUI(player, clan, 0); return; }

        boolean isLeader = clan.getLeader().equals(player.getUniqueId());
        boolean rivalPage = page > 0;

        if (slot == 47 && isLeader) {
            player.closeInventory();
            plugin.getClanChatInputListener().startRelationInput(player, rivalPage);
            player.sendMessage(mm.deserialize("<#10fc46>Digite a tag do clan para adicionar como "
                    + (rivalPage ? "rival" : "aliado") + "."));
            player.sendMessage(mm.deserialize("<gray>Para anular o processo, digite 'cancelar'."));
            return;
        }

        ClanManager cmEarly = plugin.getClanManager();

        // Página de aliados: toggle de PvP aliado (slot 45) e pedidos de aliança (linha 5).
        if (!rivalPage && slot == 45 && isLeader) {
            boolean next = !clan.isAllyFriendlyFire();
            cmEarly.setAllyFriendlyFire(clan.getId(), next);
            clan.setAllyFriendlyFire(next);
            player.sendMessage(mm.deserialize(next
                    ? "<#FF6B6B>PvP contra aliados foi <bold>ativado<reset><#FF6B6B> (o outro clan também precisa ativar)."
                    : "<#10fc46>PvP contra aliados foi <bold>desativado<reset><#10fc46>."));
            cmEarly.log(clan.getId(), player.getName(), "pvp", "PvP com aliados " + (next ? "ativado" : "desativado"));
            ClanGUI.openAlliesAndRivalsGUI(player, clan, 0);
            return;
        }
        if (!rivalPage && slot >= 37 && slot <= 43) {
            if (!isLeader) return;
            List<Clan> requests = cmEarly.getIncomingAllyRequests(clan.getId());
            int reqIdx = slot - 37;
            if (reqIdx >= requests.size()) return;
            Clan requester = requests.get(reqIdx);

            if (click.isShiftClick()) {
                cmEarly.removeAllyRequest(requester.getId(), clan.getId());
                player.sendMessage(mm.deserialize("<#FF0000>Pedido de aliança de <white>[" + requester.getTag() + "]<#FF0000> recusado."));
            } else {
                cmEarly.removeAllyRequest(requester.getId(), clan.getId());
                if (cmEarly.addAlly(clan.getId(), requester.getId())) {
                    player.sendMessage(mm.deserialize("<#10fc46>Aliança formada com <white>[" + requester.getTag() + "]<#10fc46>!"));
                    Player otherLeader = Bukkit.getPlayer(requester.getLeader());
                    if (otherLeader != null) {
                        otherLeader.sendMessage(mm.deserialize("<#10fc46>Seu pedido de aliança foi aceito por <white>[" + clan.getTag() + "]<#10fc46>!"));
                    }
                    cmEarly.log(clan.getId(), player.getName(), "ally", "Aliança formada com [" + requester.getTag() + "]");
                    cmEarly.log(requester.getId(), player.getName(), "ally", "Aliança formada com [" + clan.getTag() + "]");
                } else {
                    player.sendMessage(mm.deserialize("<#FF0000>Não foi possível formar a aliança."));
                }
            }
            ClanGUI.openAlliesAndRivalsGUI(player, clan, 0);
            return;
        }

        // Shift+Clique num clan listado remove a relação (só líder).
        if (!click.isShiftClick() || !isLeader) return;
        int idx = indexOf(WIDE_GRID, slot);
        if (idx < 0) return;

        ClanManager cm = plugin.getClanManager();
        List<Clan> list = rivalPage ? cm.getRivals(clan.getId()) : cm.getAllies(clan.getId());
        if (idx >= list.size()) return;
        Clan other = list.get(idx);

        boolean ok = rivalPage
                ? cm.removeRival(clan.getId(), other.getId())
                : cm.removeAlly(clan.getId(), other.getId());
        if (ok) {
            player.sendMessage(mm.deserialize(rivalPage
                    ? "<#10fc46>Rivalidade com <white>[" + other.getTag() + "]<#10fc46> removida."
                    : "<#10fc46>Aliança com <white>[" + other.getTag() + "]<#10fc46> removida."));
            cm.log(clan.getId(), player.getName(), rivalPage ? "unrival" : "unally",
                    (rivalPage ? "Rivalidade removida com [" : "Aliança removida com [") + other.getTag() + "]");
        } else {
            player.sendMessage(mm.deserialize("<#FF0000>Não foi possível remover a relação."));
        }
        ClanGUI.openAlliesAndRivalsGUI(player, clan, page);
    }

    // ───────────────────────── Tesouro ────────────────────────────────
    private void handleTreasury(Player player, Clan clan, int slot) {
        if (slot == 22) { ClanGUI.openClanMenu(player, clan); return; }

        if (slot == 11) {
            player.closeInventory();
            plugin.getClanChatInputListener().startTreasuryInput(player, true);
            player.sendMessage(mm.deserialize("<#10fc46>Digite o valor em coins para depositar no tesouro:"));
            player.sendMessage(mm.deserialize("<gray>Para cancelar, digite 'cancelar'."));
            return;
        }
        if (slot == 15) {
            if (!plugin.getClanManager().hasPermission(clan.getId(), player.getUniqueId(), "treasury")) {
                player.sendMessage(mm.deserialize("<#FF0000>Apenas o líder ou membros autorizados podem sacar do tesouro!"));
                return;
            }
            player.closeInventory();
            plugin.getClanChatInputListener().startTreasuryInput(player, false);
            player.sendMessage(mm.deserialize("<#10fc46>Digite o valor em coins para sacar do tesouro:"));
            player.sendMessage(mm.deserialize("<gray>Para cancelar, digite 'cancelar'."));
        }
    }

    // ───────────────────────── Cargos personalizados ──────────────────
    private void handleRoles(Player player, Clan clan, int slot, ClickType click) {
        ClanManager cm = plugin.getClanManager();
        if (slot == 49) { ClanGUI.openClanMenu(player, clan); return; }

        boolean isLeader = clan.getLeader().equals(player.getUniqueId());
        if (!isLeader) return;

        List<ClanManager.ClanRole> roles = cm.getRoles(clan.getId());

        if (slot == 40) {
            if (roles.size() >= ClanManager.MAX_ROLES) {
                player.sendMessage(mm.deserialize("<#FF0000>Limite de " + ClanManager.MAX_ROLES + " cargos atingido!"));
                return;
            }
            player.closeInventory();
            plugin.getClanChatInputListener().startRoleCreate(player);
            player.sendMessage(mm.deserialize("<#10fc46>Digite o nome do novo cargo (2-16 caracteres):"));
            player.sendMessage(mm.deserialize("<gray>Para cancelar, digite 'cancelar'."));
            return;
        }

        int idx = indexOf(ClanGUI.ROLE_SLOTS, slot);
        if (idx < 0 || idx >= roles.size()) return;
        ClanManager.ClanRole role = roles.get(idx);

        if (click.isShiftClick()) {
            if (roles.size() <= 1) {
                player.sendMessage(mm.deserialize("<#FF0000>O clan precisa ter pelo menos um cargo!"));
                return;
            }
            if (cm.deleteRole(clan.getId(), role.id())) {
                player.sendMessage(mm.deserialize("<#FF0000>Cargo <white>" + role.name() + "<#FF0000> excluído. Membros movidos para o cargo padrão."));
                cm.log(clan.getId(), player.getName(), "cargo", "Cargo '" + role.name() + "' excluído");
            } else {
                player.sendMessage(mm.deserialize("<#FF0000>Não foi possível excluir o cargo."));
            }
            ClanGUI.openRolesGUI(player, clan);
            return;
        }

        ClanGUI.openRoleEditGUI(player, clan, role);
    }

    private void handleRoleEdit(Player player, Clan clan, int slot, int roleId) {
        ClanManager cm = plugin.getClanManager();
        if (!clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(mm.deserialize("<#FF0000>Apenas o líder pode gerenciar cargos!"));
            ClanGUI.openClanMenu(player, clan);
            return;
        }
        if (slot == 31) { ClanGUI.openRolesGUI(player, clan); return; }

        ClanManager.ClanRole role = cm.getRole(roleId);
        if (role == null || role.clanId() != clan.getId()) { ClanGUI.openRolesGUI(player, clan); return; }

        if (slot == 22) {
            player.closeInventory();
            plugin.getClanChatInputListener().startRoleRename(player, roleId);
            player.sendMessage(mm.deserialize("<#10fc46>Digite o novo nome do cargo <white>" + role.name() + "<#10fc46> (2-16 caracteres):"));
            player.sendMessage(mm.deserialize("<gray>Para cancelar, digite 'cancelar'."));
            return;
        }

        String perm = switch (slot) {
            case 10 -> "invite";
            case 12 -> "kick";
            case 14 -> "chest";
            case 16 -> "market";
            case 20 -> "pvp_toggle";
            case 24 -> "treasury";
            default -> null;
        };
        if (perm == null) return;

        boolean current = switch (perm) {
            case "invite"     -> role.invite();
            case "kick"       -> role.kick();
            case "chest"      -> role.chest();
            case "market"     -> role.market();
            case "pvp_toggle" -> role.pvpToggle();
            case "treasury"   -> role.treasury();
            default -> false;
        };
        if (cm.setRolePerm(roleId, perm, !current)) {
            player.sendMessage(mm.deserialize("<#10fc46>Permissão do cargo atualizada!"));
            cm.log(clan.getId(), player.getName(), "cargo",
                    "Permissão '" + perm + "' do cargo '" + role.name() + "' " + (!current ? "ativada" : "desativada"));
        }
        ClanManager.ClanRole refreshed = cm.getRole(roleId);
        if (refreshed != null) ClanGUI.openRoleEditGUI(player, clan, refreshed);
        else ClanGUI.openRolesGUI(player, clan);
    }

    private void handleRoleSelect(Player player, Clan clan, int slot, ClanGUI gui) {
        ClanManager cm = plugin.getClanManager();
        if (slot == 49) { ClanGUI.openMembersGUI(player, clan, gui.getReturnPage()); return; }
        if (!clan.getLeader().equals(player.getUniqueId())) return;

        java.util.UUID targetUuid = gui.getTargetUuid();
        if (targetUuid == null) return;

        int idx = indexOf(ClanGUI.ROLE_SLOTS, slot);
        if (idx < 0) return;
        List<ClanManager.ClanRole> roles = cm.getRoles(clan.getId());
        if (idx >= roles.size()) return;
        ClanManager.ClanRole role = roles.get(idx);

        // Confere que o alvo ainda é membro (e não o líder).
        ClanMember target = null;
        for (ClanMember m : clan.getMembers()) {
            if (m.uuid().equals(targetUuid)) { target = m; break; }
        }
        if (target == null || target.role().equals("lider")) {
            ClanGUI.openMembersGUI(player, clan, gui.getReturnPage());
            return;
        }

        if (cm.setMemberRole(clan.getId(), targetUuid, role)) {
            player.sendMessage(mm.deserialize("<#10fc46>" + target.name() + " agora é <white>" + role.name() + "<#10fc46>."));
            Player tp = Bukkit.getPlayer(targetUuid);
            if (tp != null) {
                tp.sendMessage(mm.deserialize("<#fcc850>Seu cargo no clan agora é <white>" + role.name() + "<#fcc850>."));
            }
            cm.log(clan.getId(), player.getName(), "cargo", target.name() + " agora é " + role.name());
        } else {
            player.sendMessage(mm.deserialize("<#FF0000>Não foi possível alterar o cargo."));
        }
        Clan refreshed = cm.getClanByPlayer(player.getUniqueId());
        ClanGUI.openMembersGUI(player, refreshed != null ? refreshed : clan, gui.getReturnPage());
    }

    // ───────────────────────── Tops de clans ──────────────────────────
    private void handleClanTops(Player player, Clan clan, int slot, int tab) {
        if (slot == 22) {
            if (clan != null) ClanGUI.openClanMenu(player, clan);
            else player.closeInventory();
            return;
        }
        int next = switch (slot) {
            case 11 -> 0;
            case 13 -> 1;
            case 15 -> 2;
            default -> -1;
        };
        if (next >= 0 && next != tab) ClanGUI.openClanTopsGUI(player, next);
    }

    // ───────────────────────── Clans públicos ─────────────────────────
    private void handlePublicClans(Player player, int slot, int page) {
        if (slot == 49) { player.closeInventory(); return; }
        if (slot == 18) { ClanGUI.openPublicClans(player, Math.max(0, page - 1)); return; }
        if (slot == 26) { ClanGUI.openPublicClans(player, page + 1); return; }

        ClanManager cm = plugin.getClanManager();
        int idx = indexOf(GRID, slot);
        if (idx < 0) return;

        List<Clan> clans = cm.getOnlineClans();
        int clanIndex = page * GRID.length + idx;
        if (clanIndex >= clans.size()) return;
        Clan clan = clans.get(clanIndex);

        if (cm.getClanByPlayer(player.getUniqueId()) != null) {
            player.sendMessage(mm.deserialize("<#FF0000>Você já está em um clan!"));
            return;
        }

        if (clan.isPublic()) {
            if (cm.joinPublicClan(player.getUniqueId(), clan.getId())) {
                Clan joined = cm.getClanByTag(clan.getTag());
                if (joined != null) {
                    for (ClanMember m : joined.getMembers()) {
                        Player online = Bukkit.getPlayer(m.uuid());
                        if (online != null && !online.getUniqueId().equals(player.getUniqueId())) {
                            online.sendMessage(mm.deserialize("<#10fc46>" + player.getName() + " entrou no clan!"));
                        }
                    }
                }
                player.sendMessage(mm.deserialize("<#10fc46>Você entrou no clan <white>[" + clan.getTag() + "]<#10fc46>!"));
                player.closeInventory();
            } else {
                player.sendMessage(mm.deserialize("<#FF0000>Não foi possível entrar no clan (cheio ou erro)."));
            }
        } else {
            // Clan privado — cria solicitação de entrada (aparece em Membros & Solicitações do líder).
            if (cm.requestJoin(clan.getId(), player.getUniqueId())) {
                player.sendMessage(mm.deserialize("<#10fc46>Solicitação enviada para <white>[" + clan.getTag() + "]<#10fc46>! Aguarde a aprovação do líder."));
                Player leader = Bukkit.getPlayer(clan.getLeader());
                if (leader != null) leader.sendMessage(mm.deserialize("<#ffd250>" + player.getName() + " solicitou entrada no clan! Veja em Membros & Solicitações."));
            } else {
                player.sendMessage(mm.deserialize("<gray>Você já solicitou entrada ou não pode entrar neste clan."));
            }
            player.closeInventory();
        }
    }

    // ───────────────────────── Convites recebidos ─────────────────────
    private void handleInvites(Player player, int slot, ClickType click) {
        ClanManager cm = plugin.getClanManager();
        if (slot == 49) {
            Clan clan = cm.getClanByPlayer(player.getUniqueId());
            if (clan != null) ClanGUI.openClanMenu(player, clan);
            else ClanGUI.openPublicClans(player, 0);
            return;
        }

        int idx = indexOf(GRID, slot);
        if (idx < 0) return;
        List<Clan> invites = cm.getInvitesFor(player.getUniqueId());
        if (idx >= invites.size()) return;
        Clan clan = invites.get(idx);

        if (click.isShiftClick()) {
            cm.removeInvite(player.getUniqueId(), clan.getTag());
            player.sendMessage(mm.deserialize("<#FF0000>Convite de <white>" + clan.getTag() + " <#FF0000>recusado."));
            ClanGUI.openInvitesGUI(player);
        } else {
            if (cm.acceptInvite(player.getUniqueId(), clan.getTag())) {
                Clan joined = cm.getClanByTag(clan.getTag());
                if (joined != null) {
                    for (ClanMember m : joined.getMembers()) {
                        Player online = Bukkit.getPlayer(m.uuid());
                        if (online != null && !online.getUniqueId().equals(player.getUniqueId())) {
                            online.sendMessage(mm.deserialize("<#10fc46>" + player.getName() + " entrou no clan!"));
                        }
                    }
                }
                player.sendMessage(mm.deserialize("<#10fc46>Você entrou no clan <white>[" + clan.getTag() + "]<#10fc46>!"));
                player.closeInventory();
            } else {
                player.sendMessage(mm.deserialize("<#FF0000>Não foi possível aceitar o convite."));
                ClanGUI.openInvitesGUI(player);
            }
        }
    }

    // ───────────────────────── Cores ──────────────────────────────────
    private void handleColor(Player player, Clan clan, int slot, int page) {
        if (slot == 45) { ClanGUI.openClanMenu(player, clan); return; }
        if (slot == 49) { player.closeInventory(); return; }
        if (slot == 18) { ClanGUI.openColorGUI(player, clan, Math.max(0, page - 1)); return; }
        if (slot == 26) { ClanGUI.openColorGUI(player, clan, page + 1); return; }

        if (!clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(mm.deserialize("<#FF0000>Apenas o líder pode alterar a cor!"));
            return;
        }

        int idx = indexOf(GRID, slot);
        if (idx < 0) return;

        List<ClanColorManager.ClanColorEntry> all =
                new ArrayList<>(plugin.getClanColorManager().getAll().values());
        int colorIndex = page * GRID.length + idx;
        if (colorIndex >= all.size()) return;
        ClanColorManager.ClanColorEntry entry = all.get(colorIndex);

        if (!(player.hasPermission(entry.permissao()) || player.isOp())) {
            player.sendMessage(mm.deserialize("<#FF0000>Você não tem permissão para esta cor!"));
            player.sendMessage(mm.deserialize("<dark_gray>Permissão: " + entry.permissao()));
            return;
        }

        ClanManager cm = plugin.getClanManager();
        cm.setClanColor(clan.getId(), entry.gradiente());
        player.sendMessage(mm.deserialize("<#10fc46>Cor do clan alterada!"));

        clan = cm.getClanByPlayer(player.getUniqueId());
        if (clan != null) ClanGUI.openColorGUI(player, clan, page);
    }

    // ───────────────────────── Permissões ─────────────────────────────
    private void handlePermissions(Player player, Clan clan, int slot) {
        if (!clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(mm.deserialize("<#FF0000>Apenas o líder pode gerenciar permissões!"));
            ClanGUI.openClanMenu(player, clan);
            return;
        }
        if (slot == 49) { ClanGUI.openClanMenu(player, clan); return; }
        int idx = indexOf(GRID, slot);
        if (idx < 0) return;
        List<ClanMember> members = clan.getMembers();
        if (idx >= members.size()) return;
        ClanMember target = members.get(idx);
        if (target.role().equals("lider")) return;
        ClanGUI.openPermissionEditGUI(player, target, clan);
    }

    private void handlePermEdit(Player player, Clan clan, int slot, net.kyori.adventure.text.Component titleComp) {
        if (!clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(mm.deserialize("<#FF0000>Apenas o líder pode gerenciar permissões!"));
            ClanGUI.openClanMenu(player, clan);
            return;
        }
        if (slot == 31) { ClanGUI.openPermissionsGUI(player, clan); return; }

        String title = PlainTextComponentSerializer.plainText().serialize(titleComp);
        String memberName = title.replace("Permissões - ", "");

        ClanMember target = null;
        for (ClanMember m : clan.getMembers()) {
            if (m.name().equals(memberName)) { target = m; break; }
        }
        if (target == null) return;

        String perm = switch (slot) {
            case 10 -> "invite";
            case 12 -> "kick";
            case 14 -> "chest";
            case 16 -> "market";
            case 20 -> "pvp_toggle";
            case 24 -> "treasury";
            default -> null;
        };
        if (perm == null) return;

        ClanManager cm = plugin.getClanManager();
        ClanManager.ClanPerm perms = cm.getPermissions(clan.getId(), target.uuid());
        boolean current = switch (perm) {
            case "invite"     -> perms.invite();
            case "kick"       -> perms.kick();
            case "chest"      -> perms.chest();
            case "market"     -> perms.market();
            case "pvp_toggle" -> perms.pvpToggle();
            case "treasury"   -> perms.treasury();
            default -> false;
        };
        cm.setPermission(clan.getId(), target.uuid(), perm, !current);
        player.sendMessage(mm.deserialize("<#10fc46>Permissão atualizada!"));
        ClanGUI.openPermissionEditGUI(player, target, clan);
    }

    // ───────────────────────── Mercado ────────────────────────────────
    private void handleMarket(Player player, Clan clan, int slot, ClickType click) {
        ClanManager cm = plugin.getClanManager();

        if (slot == 48) { ClanGUI.openClanMenu(player, clan); return; }
        if (slot == 50) {
            ItemStack handItem = player.getInventory().getItemInMainHand();
            if (handItem.getType().isAir()) {
                player.sendMessage(mm.deserialize("<#FF0000>Segure um item na mão principal para vender!"));
                return;
            }
            plugin.getClanChatInputListener().startMarketListing(player, handItem.clone());
            player.closeInventory();
            player.sendMessage(mm.deserialize("<#10fc46>Digite o preço em coins para vender o item:"));
            player.sendMessage(mm.deserialize("<gray>Para cancelar, digite 'cancelar'."));
            return;
        }

        int idx = indexOf(WIDE_GRID, slot);
        if (idx < 0) return;
        List<ClanManager.MarketItem> items = cm.getMarketItems(clan.getId());
        if (idx >= items.size()) return;
        ClanManager.MarketItem mi = items.get(idx);

        if (mi.seller().equals(player.getUniqueId())) {
            if (!cm.removeMarketItem(mi.id())) { ClanGUI.openMarketGUI(player, clan); return; }
            ItemStack returned = ClanGUI.deserializeItem(mi.itemData());
            if (returned != null) giveOrDrop(player, returned);
            player.sendMessage(mm.deserialize("<#10fc46>Item removido do mercado!"));
        } else {
            double price = mi.price();
            if (!cm.removeMarketItem(mi.id())) {
                player.sendMessage(mm.deserialize("<#FF0000>Este item não está mais disponível."));
                ClanGUI.openMarketGUI(player, clan);
                return;
            }
            if (!plugin.getEconomyManager().removeCoins(player.getUniqueId(), price)) {
                player.sendMessage(mm.deserialize("<#FF0000>Você não tem coins suficientes! Necessário: <white>" + String.format("%.0f", price)));
                cm.relistMarketItem(mi);
                ClanGUI.openMarketGUI(player, clan);
                return;
            }
            // Venda no mercado do clã = transferência entre jogadores: não conta no Top Coins.
            plugin.getEconomyManager().addCoinsNoStat(mi.seller(), mi.sellerName(), price);
            ItemStack bought = ClanGUI.deserializeItem(mi.itemData());
            if (bought != null) giveOrDrop(player, bought);
            player.sendMessage(mm.deserialize("<#10fc46>Item comprado por <#fcc850>" + String.format("%.0f", price) + " coins<#10fc46>!"));

            Player seller = Bukkit.getPlayer(mi.seller());
            if (seller != null) {
                seller.sendMessage(mm.deserialize("<#10fc46>" + player.getName() + " comprou seu item por <#fcc850>" + String.format("%.0f", price) + " coins<#10fc46>!"));
            }
        }
        ClanGUI.openMarketGUI(player, clan);
    }

    /** Dá o item ao jogador; o que não couber no inventário é dropado no chão (nunca some). */
    private void giveOrDrop(Player player, ItemStack item) {
        for (ItemStack left : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    // ───────────────────────── Baú ────────────────────────────────────
    private void handleChestClick(InventoryClickEvent event, Player player, ClanGUI gui) {
        Inventory top = event.getView().getTopInventory();
        boolean inTop = event.getRawSlot() < top.getSize();

        if (!inTop) {
            // shift-click do inventário do jogador iria pro 1º slot livre do topo (pode ser vidro -> item some).
            if (event.isShiftClick()) event.setCancelled(true);
            return;
        }

        int slot = event.getSlot();
        if (slot == 49) { event.setCancelled(true); player.closeInventory(); return; }

        // Navegação entre páginas (o InventoryCloseEvent salva a página atual ao trocar).
        int page = gui.getChestPage();
        if (slot == 45 && page > 0) {
            event.setCancelled(true);
            Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
            if (clan != null && clan.getId() == gui.getClanId()) ClanGUI.openClanChest(player, clan, page - 1);
            return;
        }
        if (slot == 53 && page + 1 < ClanGUI.CHEST_MAX_PAGES) {
            event.setCancelled(true);
            Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
            if (clan != null && clan.getId() == gui.getClanId()) ClanGUI.openClanChest(player, clan, page + 1);
            return;
        }

        if (!ClanGUI.isChestStorageSlot(slot)) { event.setCancelled(true); return; }
        // slot de armazenamento: interação livre
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ClanConfirmGUI) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getInventory().getHolder() instanceof ClanGUI gui)) return;
        if (!gui.getGuiType().equals("chest")) {
            // Em menus normais, qualquer arrasto no inventário superior é bloqueado.
            int topSize = event.getView().getTopInventory().getSize();
            for (int raw : event.getRawSlots()) {
                if (raw < topSize) { event.setCancelled(true); return; }
            }
            return;
        }
        // No baú, só permite arrastar para slots de armazenamento.
        int topSize = event.getView().getTopInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < topSize && !ClanGUI.isChestStorageSlot(raw)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ClanGUI gui)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!gui.getGuiType().equals("chest")) return;

        int clanId = gui.getClanId();
        if (clanId < 0) return;
        ClanGUI.saveClanChest(event.getInventory(), clanId, gui.getChestPage());
        ClanGUI.releaseChestLock(clanId, player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof ClanGUI gui
                && gui.getGuiType().equals("chest") && gui.getClanId() >= 0) {
            ClanGUI.saveClanChest(player.getOpenInventory().getTopInventory(), gui.getClanId(), gui.getChestPage());
            ClanGUI.releaseChestLock(gui.getClanId(), player.getUniqueId());
        }
        // Garante que nenhuma trava órfã fique para trás (anti-leak).
        ClanGUI.releaseAllLocksFor(player.getUniqueId());
    }
}
