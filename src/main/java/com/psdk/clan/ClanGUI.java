package com.psdk.clan;

import com.psdk.PSDK;
import com.psdk.util.ClanText;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class ClanGUI implements InventoryHolder {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private Inventory inventory;
    private String guiType = "";
    private int clanId = -1;
    private int chestPage = 0;
    private int roleId = -1;
    private UUID targetUuid = null;
    private int returnPage = 0;

    /** Número máximo de páginas do baú do clan. */
    public static final int CHEST_MAX_PAGES = 3;

    /** Slots de armazenamento do baú do clan. */
    public static final int[] CHEST_SLOTS = {
            11, 12, 13, 14, 15,
            20, 21, 22, 23, 24,
            29, 30, 31, 32, 33};

    /** Anti-dupe: trava de uso do baú por clan — só 1 jogador por vez. clanId → jogador. */
    private static final Map<Integer, UUID> chestLocks = new java.util.concurrent.ConcurrentHashMap<>();

    // Construtor package-private para uso interno
    ClanGUI() {}

    @Override
    public Inventory getInventory() { return inventory; }

    public String getGuiType() { return guiType; }

    public int getClanId() { return clanId; }

    public int getChestPage() { return chestPage; }

    public int getRoleId() { return roleId; }

    public UUID getTargetUuid() { return targetUuid; }

    public int getReturnPage() { return returnPage; }

    public static boolean isChestStorageSlot(int slot) {
        for (int s : CHEST_SLOTS) if (s == slot) return true;
        return false;
    }

    /** Libera a trava do baú, mas só se pertencer ao jogador informado. */
    public static void releaseChestLock(int clanId, UUID player) {
        UUID owner = chestLocks.get(clanId);
        if (owner != null && owner.equals(player)) chestLocks.remove(clanId);
    }

    /** Remove TODAS as travas do jogador (chamado no quit — evita lock órfão). */
    public static void releaseAllLocksFor(UUID player) {
        chestLocks.values().removeIf(owner -> owner.equals(player));
    }

    /** Fecha qualquer GUI de clan aberta pelo jogador (ex.: ao ser expulso/clan desfeito). */
    public static void closeClanGuiFor(UUID playerId) {
        Player p = Bukkit.getPlayer(playerId);
        if (p == null) return;
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof ClanGUI) {
            p.closeInventory();
        }
    }

    // ─────────────────────────────────────────────
    //  Limite de membros (base 35, expansível por perm)
    // ─────────────────────────────────────────────
    public static final int MEMBERS_PER_PAGE = 28;

    /** Slots de conteúdo dos membros: 4 linhas × 7 colunas (28 cabeças por página). */
    public static final int[] MEMBER_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    public static int getMemberLimit(Player player) {
        for (int i = 50; i >= 36; i--) {
            if (player.hasPermission("clan.membros." + i)) return i;
        }
        return 35;
    }

    /** Limite do clan baseado na permissão do LÍDER (líder offline -> usa o máximo, não bloqueia injustamente). */
    public static int getClanMemberLimit(Clan clan) {
        Player leader = Bukkit.getPlayer(clan.getLeader());
        return leader != null ? getMemberLimit(leader) : 50;
    }

    // ─────────────────────────────────────────────
    //  Nick formatado via PlaceholderAPI + LuckPerms
    // ─────────────────────────────────────────────
    static Component resolveNick(Player player) {
        if (player == null) return mm.deserialize("???").decoration(TextDecoration.ITALIC, false);
        return com.psdk.util.PlayerDisplayFormat.displayName(player.getUniqueId(), player.getName());
    }

    private static void applyNickToItem(ItemStack item, Component nick, Component prefix) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .append(prefix)
                    .append(nick));
            item.setItemMeta(meta);
        }
    }

    // ─────────────────────────────────────────────
    //  Menu principal
    // ─────────────────────────────────────────────
    public static void openMainMenu(Player player, Clan clan) {
        if (clan == null) openPublicClans(player, 0);
        else openClanMenu(player, clan);
    }

    public static void openClanMenu(Player player, Clan clan) {
        ClanGUI holder = new ClanGUI();
        holder.guiType = "clan_menu";

        ClanManager cm = PSDK.getInstance().getClanManager();

        Inventory inv = Bukkit.createInventory(holder, 54,
                mm.deserialize(ClanUI.gradientTitle("#58A6FF", "#E6CFFE",
                        "[" + clan.getTag() + "] " + clan.getName())));
        holder.inventory = inv;

        String joinDate = new SimpleDateFormat("dd/MM/yyyy - HH:mm").format(new Date(getJoinDate(player, clan)));
        int limit = getMemberLimit(player);
        boolean isLeader = clan.getLeader().equals(player.getUniqueId());
        int allies = cm.getAllies(clan.getId()).size();
        int rivals = cm.getRivals(clan.getId()).size();
        double treasury = cm.getTreasuryCoins(clan.getId());
        String treasuryFmt = String.format("%,.0f", treasury).replace(",", ".");

        // Slot 10 - Cabeça do jogador
        ItemStack selfHead = buildItem(player, "{player}", "", List.of(
                ClanUI.divider(),
                ClanUI.stat("Tag", clan.getTag()),
                ClanUI.stat("Entrou em", joinDate),
                ClanUI.divider()
        ));
        applyNickToItem(selfHead, resolveNick(player), mm.deserialize("<gradient:#FFD250:#FCA503><bold>"));
        inv.setItem(10, selfHead);

        // Slot 11 - Informações
        inv.setItem(11, buildItem(null, "WHITE_BANNER", ClanUI.gradientTitle("#FFD250", "#FCA503", "Informações"), List.of(
                ClanUI.stat("Nome", clan.getName()),
                ClanUI.stat("Tag", clan.getTag()),
                ClanUI.stat("Membros", clan.getMembers().size() + "/" + limit),
                "",
                ClanUI.click("ᴠᴇʀ ᴅᴇᴛᴀʟʜᴇs")
        )));

        // Slot 12 - Membros
        inv.setItem(12, buildItem(null, "CHEST_MINECART", ClanUI.gradientTitle("#10FC46", "#3DFF8A", "Membros"), List.of(
                "<#8B949E>Gerencie membros e solicitações",
                "<#8B949E>de entrada do clan.",
                "",
                ClanUI.click("ᴀʙʀɪʀ ᴍᴇᴍʙʀᴏs")
        )));

        // Slot 13 - Privacidade (líder) — slot 37 no layout
        boolean pub = clan.isPublic();
        inv.setItem(37, buildItem(null, pub ? "SPRUCE_FENCE_GATE" : "OAK_FENCE_GATE",
                pub ? ClanUI.gradientTitle("#10FC46", "#3DFF8A", "Público") : ClanUI.gradientTitle("#FF6B6B", "#E22C27", "Privado"),
                isLeader ? List.of(
                        "<#8B949E>Jogadores podem entrar sem convite?",
                        "",
                        ClanUI.stat("Status", pub ? "Público" : "Privado"),
                        "",
                        pub ? ClanUI.click("ᴛᴏʀɴᴀʀ ᴘʀɪᴠᴀᴅᴏ") : ClanUI.click("ᴛᴏʀɴᴀʀ ᴘúʙʟɪᴄᴏ")
                ) : List.of("<#8B949E>Apenas o líder pode alterar.")));

        // Slot 14 - Cor do clan — slot 43
        String colorHex = clan.getColorHex();
        inv.setItem(43, buildItem(null, "WHITE_DYE", ClanUI.gradientTitle("#E6CFFE", "#C9A0FF", "Cor do Clan"), List.of(
                "<#8B949E>Personalize a tag do clan.",
                "",
                "<#8B949E>Cor atual: " + ClanText.colorize(colorHex, "■■■"),
                "",
                isLeader ? ClanUI.click("ᴀʟᴛᴇʀᴀʀ ᴄᴏʀ") : "<#8B949E>Apenas o líder pode alterar."
        )));

        // Slot 15 - Relações (FUNCIONAL — aliados & rivais)
        inv.setItem(15, buildItem(null, "BLUE_BANNER", ClanUI.gradientTitle("#76BCFF", "#4D9FFF", "Relações"), List.of(
                "<#8B949E>Aliados, rivais e pedidos de aliança.",
                "",
                "<#58A6FF>◈ Aliados: <white>" + allies,
                "<#FF6B6B>◈ Rivais: <white>" + rivals,
                "",
                ClanUI.click("ᴀʙʀɪʀ ʀᴇʟᴀçõᴇs")
        )));

        // Slot 16 - Permissões
        inv.setItem(16, buildItem(null, "COMMAND_BLOCK", ClanUI.gradientTitle("#FFA500", "#FFCC66", "Permissões"), List.of(
                "<#8B949E>Controle o que cada membro pode fazer.",
                "",
                isLeader ? ClanUI.click("ɢᴇʀᴇɴᴄɪᴀʀ") : "<#8B949E>Apenas o líder pode alterar."
        )));

        // Slot 28 - Baú
        inv.setItem(28, buildItem(null, "CHEST", ClanUI.gradientTitle("#AF83FF", "#D4B3FF", "Baú do Clan"), List.of(
                "<#8B949E>Itens compartilhados entre membros.",
                "",
                ClanUI.click("ᴀʙʀɪʀ ʙᴀú")
        )));

        // Slot 29 - Mercado
        inv.setItem(29, buildItem(null, "ENCHANTED_GOLDEN_APPLE", ClanUI.gradientTitle("#FFD250", "#FFE566", "Mercado"), List.of(
                "<#8B949E>Compre e venda entre membros.",
                "",
                ClanUI.click("ᴀᴄᴇssᴀʀ ᴍᴇʀᴄᴀᴅᴏ")
        )));

        // Slot 30 - PvP entre membros
        boolean ff = clan.isFriendlyFire();
        boolean canTogglePvp = isLeader || cm.hasPermission(clan.getId(), player.getUniqueId(), "pvp_toggle");
        inv.setItem(30, buildItem(null, ff ? "FIRE_CHARGE" : "FLINT_AND_STEEL",
                ff ? ClanUI.gradientTitle("#FF6B6B", "#FF9999", "PvP Interno [ON]") : ClanUI.gradientTitle("#8B949E", "#B0B8C0", "PvP Interno [OFF]"),
                canTogglePvp ? List.of(
                        "<#8B949E>Membros podem se atacar?",
                        "",
                        ClanUI.stat("Status", ff ? "Ativo" : "Inativo"),
                        "",
                        ff ? ClanUI.click("ᴅᴇsᴀᴛɪᴠᴀʀ") : ClanUI.click("ᴀᴛɪᴠᴀʀ")
                ) : List.of(
                        "<#8B949E>Membros podem se atacar?",
                        "",
                        "<#8B949E>Apenas líder ou autorizados."
                )));

        // Slot 38 - Convites recebidos
        inv.setItem(38, buildItem(null, "MAP", ClanUI.gradientTitle("#76BCFF", "#A8D4FF", "Convites"), List.of(
                "<#8B949E>Convites de outros clans.",
                "",
                ClanUI.click("ᴠᴇʀ ᴄᴏɴᴠɪᴛᴇs")
        )));

        // Slot 42 - Tops
        inv.setItem(42, buildItem(null, "EMERALD", ClanUI.gradientTitle("#008DFF", "#4DB8FF", "Tops"), List.of(
                "<#8B949E>Ranking de clans do servidor.",
                "",
                ClanUI.click("ᴠᴇʀ ʀᴀɴᴋɪɴɢ")
        )));

        // Slot 39 - Histórico
        inv.setItem(39, buildItem(null, "WRITABLE_BOOK", ClanUI.gradientTitle("#CBD1D7", "#FFFFFF", "Histórico"), List.of(
                "<#8B949E>Últimas ações do clan.",
                "",
                ClanUI.click("ᴠᴇʀ ʟᴏɢs")
        )));

        // Slot 40 - Cargos
        inv.setItem(40, buildItem(null, "NAME_TAG", ClanUI.gradientTitle("#FCC850", "#FFE08A", "Cargos"), List.of(
                "<#8B949E>Cargos personalizados e permissões.",
                "",
                isLeader ? ClanUI.click("ɢᴇʀᴇɴᴄɪᴀʀ ᴄᴀʀɢᴏs") : "<#8B949E>Apenas o líder pode gerenciar."
        )));

        // Slot 33 - Tesouro (FUNCIONAL)
        inv.setItem(33, buildItem(null, "RECOVERY_COMPASS", ClanUI.gradientTitle("#FC782C", "#FFAA66", "Tesouro"), List.of(
                "<#8B949E>Dinheiro compartilhado do clan.",
                "",
                ClanUI.stat("Saldo", treasuryFmt + " coins"),
                "",
                ClanUI.click("ᴀʙʀɪʀ ᴛᴇsᴏᴜʀᴏ")
        )));

        // Slot 34 - Sair / Desfazer
        inv.setItem(34, buildItem(null, "RED_DYE",
                isLeader ? ClanUI.gradientTitle("#EF2E2E", "#FF6B6B", "Desfazer Clan") : ClanUI.gradientTitle("#EF2E2E", "#FF6B6B", "Sair do Clan"),
                List.of(
                        "",
                        isLeader ? "<#FF6B6B>⚠ Apaga o clan permanentemente!" : "<#8B949E>Você sairá do clan.",
                        "",
                        ClanUI.click("ᴄᴏɴꜰɪʀᴍᴀʀ")
                )));

        ClanUI.fillBackground(inv, 54,
                10, 11, 12, 15, 16, 28, 29, 30, 33, 34, 37, 38, 39, 40, 42, 43);

        player.openInventory(inv);
        ClanUI.openSound(player);
    }

    // ─────────────────────────────────────────────
    //  Menu: Membros + Solicitações
    // ─────────────────────────────────────────────
    /** Wrapper de compatibilidade: legacyPage 0 = membros, 1 = solicitações. */
    public static void openMembersAndRequestsGUI(Player player, Clan clan, int legacyPage) {
        if (legacyPage == 0) openMembersGUI(player, clan, 0);
        else openRequestsGUI(player, clan);
    }

    /** Abre a página de membros do clan com 28 cabeças por página (4 linhas × 7). */
    public static void openMembersGUI(Player player, Clan clan, int page) {
        ClanGUI holder = new ClanGUI();
        holder.guiType = "members_page_" + page;

        List<ClanMember> members = clan.getMembers();
        int limit = getMemberLimit(player);
        int totalPages = Math.max(1, (int) Math.ceil(members.size() / (double) MEMBERS_PER_PAGE));

        String pageLabel = totalPages > 1 ? " <gray>(" + (page + 1) + "/" + totalPages + ")" : "";
        Inventory inv = Bukkit.createInventory(holder, 54,
                mm.deserialize("<dark_gray>Membros — " + clan.getTag() + pageLabel));
        holder.inventory = inv;

        ClanManager cm = PSDK.getInstance().getClanManager();
        // Garante que os cargos padrão existem (migra 'vice'/'membro' legados) e
        // recarrega os membros para exibir os nomes de cargo atualizados.
        cm.getRoles(clan.getId());
        clan.setMembers(cm.getMembers(clan.getId()));
        members = clan.getMembers();

        boolean canKick = cm.hasPermission(clan.getId(), player.getUniqueId(), "kick");
        boolean canInvite = cm.hasPermission(clan.getId(), player.getUniqueId(), "invite");
        int start = page * MEMBERS_PER_PAGE;

        for (int i = 0; i < MEMBER_SLOTS.length; i++) {
            int memberIdx = start + i;
            if (memberIdx < members.size()) {
                ClanMember member = members.get(memberIdx);
                Player mp = Bukkit.getPlayer(member.uuid());
                String status = mp != null ? "<#10fc46>Online" : "<#e22c27>Offline";
                String roleDisplay = member.role().equals("lider")
                        ? "<#ff9008>Líder"
                        : "<#fcc850>" + member.role();

                boolean isLeaderViewing = clan.getLeader().equals(player.getUniqueId());
                List<String> lore = new ArrayList<>(List.of(
                        "<#848c94>Cargo: " + roleDisplay,
                        "<#848c94>Status: " + status
                ));
                if (isLeaderViewing && !member.role().equals("lider")) {
                    lore.add("");
                    lore.add("<#fcc850>Clique direito para alterar o cargo");
                }
                if (canKick && !member.role().equals("lider") && !member.uuid().equals(player.getUniqueId())) {
                    if (!isLeaderViewing) lore.add("");
                    lore.add("<#e22c27>Shift+Clique para expulsar");
                }

                ItemStack head = buildItem(mp, "{player}", "", lore);
                Component nick = mp != null
                        ? resolveNick(mp)
                        : mm.deserialize("<#cbd1d7>" + member.name());
                applyNickToItem(head, nick, mm.deserialize("<#cbd1d7>"));
                inv.setItem(MEMBER_SLOTS[i], head);
            } else if (memberIdx < limit) {
                inv.setItem(MEMBER_SLOTS[i], buildVacantSlot(memberIdx + 1, limit));
            }
        }

        // Linha 6 — navegação
        if (page > 0) {
            inv.setItem(45, buildItem(null, "ARROW", "<#b1fcb6>← Anterior", List.of("<#848c94>Página anterior de membros")));
        }
        inv.setItem(48, buildItem(null, "ARROW", "<#b1fcb6>Voltar", List.of()));
        if (canInvite && members.size() < limit) {
            inv.setItem(50, buildItem(null, "MAP", "<#ef323d>Enviar convite", List.of(
                    "<#848c94>Clique para convidar",
                    "<#848c94>um jogador para o clan.",
                    "",
                    "<#ef323d>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴄᴏɴᴠɪᴅᴀʀ"
            )));
        }
        if (page + 1 < totalPages) {
            inv.setItem(53, buildItem(null, "ARROW", "<#b1fcb6>Próxima →", List.of("<#848c94>Próxima página de membros")));
        } else {
            inv.setItem(53, buildItem(null, "ARROW", "<#767AFF>Solicitações →", List.of("<#848c94>Ver solicitações de entrada")));
        }

        player.openInventory(inv);
    }

    /** Abre a página de solicitações pendentes do clan. */
    public static void openRequestsGUI(Player player, Clan clan) {
        ClanGUI holder = new ClanGUI();
        holder.guiType = "requests";

        Inventory inv = Bukkit.createInventory(holder, 54,
                mm.deserialize("<dark_gray>Solicitações — " + clan.getTag()));
        holder.inventory = inv;

        int[] slots = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33};

        List<ClanMember> pending = PSDK.getInstance().getClanManager().getPendingRequests(clan.getId());
        for (int i = 0; i < slots.length; i++) {
            if (i < pending.size()) {
                ClanMember req = pending.get(i);
                Player rp = Bukkit.getPlayer(req.uuid());
                ItemStack head = buildItem(rp, "{player}", "", List.of(
                        "<#10fc46>Clique para aceitar",
                        "<#e22c27>Shift+Clique para recusar"
                ));
                Component nick = rp != null
                        ? resolveNick(rp)
                        : mm.deserialize("<#cbd1d7>" + req.name());
                applyNickToItem(head, nick, mm.deserialize("<#cbd1d7>"));
                inv.setItem(slots[i], head);
            } else {
                inv.setItem(slots[i], buildVacantSlot(i + 1, -1));
            }
        }

        inv.setItem(45, buildItem(null, "ARROW", "<#b1fcb6>← Membros", List.of("<#848c94>Voltar à lista de membros")));
        inv.setItem(48, buildItem(null, "ARROW", "<#b1fcb6>Voltar", List.of()));
        player.openInventory(inv);
    }

    private static ItemStack buildVacantSlot(int index, int limit) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = skull.getItemMeta();
        if (meta != null) {
            meta.displayName(mm.deserialize("<#848c94>Vaga Livre").decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of());
            skull.setItemMeta(meta);
        }
        return skull;
    }

    // ─────────────────────────────────────────────
    //  Menu: Aliados & Rivais
    // ─────────────────────────────────────────────
    public static void openAlliesAndRivalsGUI(Player player, Clan clan, int page) {
        ClanGUI holder = new ClanGUI();
        holder.guiType = "allies_page_" + page;

        String title = page == 0
                ? ClanUI.gradientTitle("#76BCFF", "#4D9FFF", "Aliados — " + clan.getTag())
                : ClanUI.gradientTitle("#FF6B6B", "#E22C27", "Rivais — " + clan.getTag());
        Inventory inv = Bukkit.createInventory(holder, 54, mm.deserialize(title));
        holder.inventory = inv;

        int[] slots = {10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34};

        ClanManager cm = PSDK.getInstance().getClanManager();
        List<Clan> list = page == 0 ? cm.getAllies(clan.getId()) : cm.getRivals(clan.getId());
        boolean isLeader = clan.getLeader().equals(player.getUniqueId());

        // Página de aliados: pedidos de aliança recebidos (linha 5) + toggle de PvP aliado.
        if (page == 0) {
            List<Clan> requests = cm.getIncomingAllyRequests(clan.getId());
            int[] reqSlots = {37, 38, 39, 40, 41, 42, 43};
            for (int i = 0; i < reqSlots.length && i < requests.size(); i++) {
                Clan requester = requests.get(i);
                inv.setItem(reqSlots[i], buildItem(null, "PAPER",
                        ClanText.formatClanTag(requester.getColorHex(), requester.getTag()) + " <#cbd1d7>" + requester.getName(),
                        isLeader ? List.of(
                                "<#fcc850>Pedido de aliança pendente",
                                "",
                                "<#10fc46>Clique para aceitar",
                                "<#e22c27>Shift+Clique para recusar"
                        ) : List.of("<#fcc850>Pedido de aliança pendente", "<#848c94>Apenas o líder pode responder.")));
            }

            boolean allyFf = clan.isAllyFriendlyFire();
            inv.setItem(45, buildItem(null, allyFf ? "FIRE_CHARGE" : "FLINT_AND_STEEL",
                    allyFf ? "<#FF6B6B>PvP com Aliados <bold>[ATIVO]</bold>" : "<#848c94>PvP com Aliados <bold>[INATIVO]</bold>",
                    isLeader ? List.of(
                            "<#848c94>Permite PvP contra clans aliados.",
                            "<#848c94>Só libera se AMBOS os clans ativarem.",
                            "",
                            allyFf ? "<#e22c27>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴅᴇsᴀᴛɪᴠᴀʀ" : "<#10fc46>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴀᴛɪᴠᴀʀ"
                    ) : List.of(
                            "<#848c94>Permite PvP contra clans aliados.",
                            "<#848c94>Apenas o líder pode alterar."
                    )));
        }

        for (int i = 0; i < slots.length; i++) {
            if (i < list.size()) {
                Clan other = list.get(i);
                Player leader = Bukkit.getPlayer(other.getLeader());
                String color = page == 0 ? "<#76BCFF>" : "<#e22c27>";

                ItemStack head = buildItem(leader, leader != null ? "{player}" : "PLAYER_HEAD", "", List.of());
                Component displayName = mm.deserialize(
                        ClanText.formatClanTag(other.getColorHex(), other.getTag()) + " <#cbd1d7>" + other.getName())
                        .decoration(TextDecoration.ITALIC, false);
                ItemMeta meta = head.getItemMeta();
                if (meta != null) {
                    meta.displayName(displayName);
                    Component leaderNick = leader != null
                            ? resolveNick(leader)
                            : mm.deserialize("<#cbd1d7>" +
                                             (Bukkit.getOfflinePlayer(other.getLeader()).getName() != null
                                              ? Bukkit.getOfflinePlayer(other.getLeader()).getName() : "???"))
                                    .decoration(TextDecoration.ITALIC, false);
                    List<Component> lore = new ArrayList<>(List.of(
                            mm.deserialize("<#848c94>Membros: <#cbd1d7>" + other.getMembers().size()).decoration(TextDecoration.ITALIC, false),
                            Component.empty().decoration(TextDecoration.ITALIC, false).append(mm.deserialize("<#848c94>Líder: ")).append(leaderNick),
                            Component.empty(),
                            mm.deserialize(color + (page == 0 ? "ᴀʟɪᴀᴅᴏ" : "ʀɪᴠᴀʟ")).decoration(TextDecoration.ITALIC, false)
                    ));
                    if (isLeader) {
                        lore.add(mm.deserialize("<#e22c27>Shift+Clique para remover").decoration(TextDecoration.ITALIC, false));
                    }
                    meta.lore(lore);
                    head.setItemMeta(meta);
                }
                inv.setItem(slots[i], head);
            } else {
                inv.setItem(slots[i], buildVacantSlot(i + 1, -1));
            }
        }

        if (page > 0) {
            inv.setItem(18, buildItem(null, "ARROW", "<#76BCFF>Aliados", List.of("<#848c94>Ver clans aliados")));
        }
        if (page == 0) {
            inv.setItem(26, buildItem(null, "ARROW", "<#e22c27>Rivais", List.of("<#848c94>Ver clans rivais")));
        }

        if (isLeader) {
            String addTitle = page == 0
                    ? ClanUI.gradientTitle("#76BCFF", "#4D9FFF", "Adicionar Aliado")
                    : ClanUI.gradientTitle("#FF6B6B", "#E22C27", "Adicionar Rival");
            inv.setItem(47, buildItem(null, "NAME_TAG", addTitle, List.of(
                    "<#8B949E>Digite a tag do clan no chat.",
                    "",
                    ClanUI.click("ᴀᴅɪᴄɪᴏɴᴀʀ")
            )));
        }

        inv.setItem(49, buildItem(null, "ARROW", "<#b1fcb6>Voltar", List.of()));
        ClanUI.fillBackground(inv, 54, 10, 11, 12, 13, 14, 15, 16, 18, 19, 20, 21, 22, 23, 24, 25, 26, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43, 45, 47, 49);
        player.openInventory(inv);
        ClanUI.openSound(player);
    }

    // ─────────────────────────────────────────────
    //  Menu: Tesouro
    // ─────────────────────────────────────────────
    public static void openTreasuryGUI(Player player, Clan clan) {
        ClanGUI holder = new ClanGUI();
        holder.guiType = "treasury";
        Inventory inv = Bukkit.createInventory(holder, 27,
                mm.deserialize(ClanUI.gradientTitle("#FC782C", "#FFAA66", "Tesouro — " + clan.getTag())));
        holder.inventory = inv;

        ClanManager cm = PSDK.getInstance().getClanManager();
        double balance = cm.getTreasuryCoins(clan.getId());
        boolean canWithdraw = cm.hasPermission(clan.getId(), player.getUniqueId(), "treasury");

        inv.setItem(13, buildItem(null, "RECOVERY_COMPASS", ClanUI.gradientTitle("#FC782C", "#FFAA66", "Saldo do Tesouro"), List.of(
                "<#8B949E>Dinheiro compartilhado do clan.",
                "",
                ClanUI.stat("Saldo", String.format("%,.0f", balance).replace(",", ".") + " coins")
        )));

        inv.setItem(11, buildItem(null, "GOLD_INGOT", ClanUI.gradientTitle("#10FC46", "#3DFF8A", "Depositar"), List.of(
                "<#8B949E>Transfira coins do seu saldo",
                "<#8B949E>para o tesouro do clan.",
                "",
                ClanUI.click("ᴅᴇᴘᴏsɪᴛᴀʀ")
        )));

        inv.setItem(15, buildItem(null, "HOPPER", ClanUI.gradientTitle("#FF6B6B", "#FF9999", "Sacar"), canWithdraw ? List.of(
                "<#8B949E>Transfira coins do tesouro",
                "<#8B949E>para o seu saldo.",
                "",
                ClanUI.click("sᴀᴄᴀʀ")
        ) : List.of("<#8B949E>Apenas o líder ou membros", "<#8B949E>autorizados podem sacar.")));

        inv.setItem(22, buildItem(null, "ARROW", "<#b1fcb6>Voltar", List.of()));
        ClanUI.fillBackground(inv, 27, 11, 13, 15, 22);
        player.openInventory(inv);
        ClanUI.openSound(player);
    }

    // ─────────────────────────────────────────────
    //  Menu: Tops de clans
    // ─────────────────────────────────────────────
    /** tab: 0 = membros, 1 = kills, 2 = tesouro. */
    public static void openClanTopsGUI(Player player, int tab) {
        ClanGUI holder = new ClanGUI();
        holder.guiType = "clan_tops_" + tab;
        Inventory inv = Bukkit.createInventory(holder, 27, mm.deserialize(ClanUI.gradientTitle("#008DFF", "#4DB8FF", "Tops de Clans")));
        holder.inventory = inv;

        ClanTopQueryService.ClanTopType type = switch (tab) {
            case 1 -> ClanTopQueryService.ClanTopType.KILLS;
            case 2 -> ClanTopQueryService.ClanTopType.TREASURY;
            default -> ClanTopQueryService.ClanTopType.MEMBERS;
        };
        List<ClanTopQueryService.ClanTopEntry> entries =
                PSDK.getInstance().getClanTopQueryService().getTop(type);

        String[] tabNames = {"Membros", "Kills", "Tesouro"};
        String[] tabMats = {"PLAYER_HEAD", "DIAMOND_SWORD", "GOLD_INGOT"};
        int[] tabSlots = {11, 13, 15};
        for (int t = 0; t < 3; t++) {
            boolean active = t == tab;
            List<Component> lore = new ArrayList<>();
            if (active) {
                for (int i = 0; i < ClanTopQueryService.LIMIT; i++) {
                    if (i < entries.size() && entries.get(i).value() > 0) {
                        ClanTopQueryService.ClanTopEntry e = entries.get(i);
                        String value = t == 2
                                ? String.format("%,.0f", e.value()).replace(",", ".")
                                : String.format("%,d", (long) e.value()).replace(",", ".");
                        lore.add(mm.deserialize("<!italic><#848c94>" + (i + 1) + " - <reset>"
                                + ClanText.formatClanTag(e.color(), e.tag())
                                + " <#cbd1d7>" + e.name() + " <#848c94>: <#cbd1d7>" + value));
                    } else {
                        lore.add(mm.deserialize("<!italic><#848c94>" + (i + 1) + " - Vago"));
                    }
                }
            } else {
                lore.add(mm.deserialize("<!italic><#848c94>Clique para ver este top."));
            }
            inv.setItem(tabSlots[t], buildItemWithComponentLore(tabMats[t],
                    (active ? ClanUI.gradientTitle("#008DFF", "#4DB8FF", "Top " + tabNames[t])
                            : "<#8B949E>Top " + tabNames[t]), lore));
        }

        inv.setItem(22, buildItem(null, "ARROW", "<#b1fcb6>Voltar", List.of()));
        ClanUI.fillBackground(inv, 27, 11, 13, 15, 22);
        player.openInventory(inv);
        ClanUI.openSound(player);
    }

    // ─────────────────────────────────────────────
    //  Menu: Histórico (logs)
    // ─────────────────────────────────────────────
    public static void openLogsGUI(Player player, Clan clan) {
        ClanGUI holder = new ClanGUI();
        holder.guiType = "logs";
        Inventory inv = Bukkit.createInventory(holder, 54,
                mm.deserialize("<dark_gray>Histórico — " + clan.getTag()));
        holder.inventory = inv;

        List<ClanManager.ClanLog> logs = PSDK.getInstance().getClanManager().getLogs(clan.getId(), 45);
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");

        if (logs.isEmpty()) {
            inv.setItem(22, buildItem(null, "BARRIER", "<#848c94>Nenhum registro ainda", List.of(
                    "<#848c94>As ações do clan aparecerão aqui.")));
        }
        for (int i = 0; i < logs.size() && i < 45; i++) {
            ClanManager.ClanLog log = logs.get(i);
            inv.setItem(i, buildItem(null, logMaterial(log.action()),
                    "<#cbd1d7>" + logTitle(log.action()), List.of(
                            "<#848c94>" + log.detail(),
                            "",
                            "<#848c94>Por: <#cbd1d7>" + log.actor(),
                            "<#848c94>Em: <#cbd1d7>" + sdf.format(new Date(log.at()))
                    )));
        }

        inv.setItem(49, buildItem(null, "ARROW", "<#b1fcb6>Voltar", List.of()));
        player.openInventory(inv);
    }

    private static String logMaterial(String action) {
        return switch (action) {
            case "expulsar"           -> "IRON_SWORD";
            case "entrar"             -> "LIME_DYE";
            case "sair"               -> "GRAY_DYE";
            case "deposito", "saque"  -> "GOLD_INGOT";
            case "pvp"                -> "FLINT_AND_STEEL";
            case "ally", "unally"     -> "BLUE_DYE";
            case "rival", "unrival"   -> "RED_DYE";
            case "promover", "rebaixar", "cargo" -> "NAME_TAG";
            case "transferir"         -> "GOLDEN_HELMET";
            default -> "PAPER";
        };
    }

    private static String logTitle(String action) {
        return switch (action) {
            case "expulsar"   -> "Expulsão";
            case "entrar"     -> "Entrada";
            case "sair"       -> "Saída";
            case "deposito"   -> "Depósito no tesouro";
            case "saque"      -> "Saque do tesouro";
            case "pvp"        -> "PvP interno";
            case "ally"       -> "Aliança";
            case "unally"     -> "Fim de aliança";
            case "rival"      -> "Rivalidade";
            case "unrival"    -> "Fim de rivalidade";
            case "promover"   -> "Promoção";
            case "rebaixar"   -> "Rebaixamento";
            case "cargo"      -> "Cargo";
            case "transferir" -> "Transferência de liderança";
            default -> action;
        };
    }

    private static ItemStack buildItemWithComponentLore(String materialName, String name, List<Component> lore) {
        ItemStack item = buildItem(null, materialName, name, List.of());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ─────────────────────────────────────────────
    //  Clans públicos
    // ─────────────────────────────────────────────
    public static void openPublicClans(Player player, int page) {
        ClanGUI holder = new ClanGUI();
        holder.guiType = "public_clans_" + page;
        Inventory inv = Bukkit.createInventory(holder, 54, mm.deserialize("<dark_gray>Clans Públicos"));
        holder.inventory = inv;

        List<Clan> clans = PSDK.getInstance().getClanManager().getOnlineClans();
        int[] slots = {11, 12, 13, 14, 15,
                20, 21, 22, 23, 24,
                29, 30, 31, 32, 33};
        int perPage = slots.length;
        int start = page * perPage;
        int end = Math.min(start + perPage, clans.size());

        for (int i = start; i < end && (i - start) < slots.length; i++) {
            Clan clan = clans.get(i);
            Player leader = Bukkit.getPlayer(clan.getLeader());

            ItemStack head = buildItem(leader, leader != null ? "{player}" : "PLAYER_HEAD", "", List.of());
            Component displayName = mm.deserialize(
                    ClanText.formatClanTag(clan.getColorHex(), clan.getTag()) + " <#cbd1d7>" + clan.getName())
                    .decoration(TextDecoration.ITALIC, false);
            ItemMeta meta = head.getItemMeta();
            if (meta != null) {
                meta.displayName(displayName);
                Component leaderNick = leader != null
                        ? resolveNick(leader)
                        : mm.deserialize("<#cbd1d7>" +
                                         (Bukkit.getOfflinePlayer(clan.getLeader()).getName() != null
                                          ? Bukkit.getOfflinePlayer(clan.getLeader()).getName() : "???"))
                                .decoration(TextDecoration.ITALIC, false);
                boolean pub = clan.isPublic();
                meta.lore(List.of(
                        mm.deserialize("<#cbd1d7>Membros: <color:#777777>" + clan.getMembers().size() + "/" + getMemberLimit(player)).decoration(TextDecoration.ITALIC, false),
                        Component.empty().decoration(TextDecoration.ITALIC, false).append(mm.deserialize("<#cbd1d7>Líder:<reset> ")).append(leaderNick),
                        Component.empty(),
                        mm.deserialize(pub ? "<#10fc46>Acesso: <bold>PÚBLICO" : "<#e22c27>Acesso: <bold>PRIVADO").decoration(TextDecoration.ITALIC, false),
                        mm.deserialize(pub ? "<#ffd250>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴇɴᴛʀᴀʀ" : "<#ffd250>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ sᴏʟɪᴄɪᴛᴀʀ").decoration(TextDecoration.ITALIC, false)
                ));
                head.setItemMeta(meta);
            }
            inv.setItem(slots[i - start], head);
        }

        if (page > 0) inv.setItem(18, buildItem(null, "ARROW", "<#b1fcb6>Voltar", List.of()));
        if (end < clans.size()) inv.setItem(26, buildItem(null, "ARROW", "<#b1fcb6>Avançar", List.of()));
        inv.setItem(49, buildItem(null, "RED_DYE", "<color:#EF2E2E>Fechar", List.of()));

        player.openInventory(inv);
    }

    // ─────────────────────────────────────────────
    //  Convites recebidos
    // ─────────────────────────────────────────────
    public static void openInvitesGUI(Player player) {
        ClanGUI holder = new ClanGUI();
        holder.guiType = "invites";
        Inventory inv = Bukkit.createInventory(holder, 54, mm.deserialize("<dark_gray>Convites Recebidos"));
        holder.inventory = inv;

        List<Clan> invites = PSDK.getInstance().getClanManager().getInvitesFor(player.getUniqueId());
        int[] slots = {11, 12, 13, 14, 15,
                20, 21, 22, 23, 24,
                29, 30, 31, 32, 33};

        for (int i = 0; i < invites.size() && i < slots.length; i++) {
            Clan clan = invites.get(i);
            inv.setItem(slots[i], buildItem(null, "MAP",
                    ClanText.formatClanTag(clan.getColorHex(), clan.getTag()) + " <#cbd1d7>" + clan.getName(),
                    List.of(
                            "<#cbd1d7>Membros: <color:#777777>" + clan.getMembers().size() + "/" + getClanMemberLimit(clan),
                            "",
                            "<#10fc46>Clique para aceitar",
                            "<#e22c27>Shift+Clique para recusar"
                    )));
        }

        inv.setItem(49, buildItem(null, "ARROW", "<#b1fcb6>Voltar", List.of()));
        player.openInventory(inv);
    }

    // ─────────────────────────────────────────────
    //  Cor do clan — com paginação
    // ─────────────────────────────────────────────
    public static void openColorGUI(Player player, Clan clan, int page) {
        PSDK plugin = PSDK.getInstance();
        ClanColorManager colorManager = plugin.getClanColorManager();
        // Usar LinkedHashMap ou lista para ordem consistente
        List<ClanColorManager.ClanColorEntry> allColors = new ArrayList<>(colorManager.getAll().values());

        int[] slots = {11, 12, 13, 14, 15,
                20, 21, 22, 23, 24,
                29, 30, 31, 32, 33};

        int perPage = slots.length; // 21 por página
        int start = page * perPage;
        int end = Math.min(start + perPage, allColors.size());
        int totalPages = (int) Math.ceil((double) allColors.size() / perPage);

        ClanGUI holder = new ClanGUI();
        holder.guiType = "color_menu_" + page;
        Inventory inv = Bukkit.createInventory(holder, 54,
                mm.deserialize("<dark_gray>Cores do Clan" + (totalPages > 1 ? " — Pág. " + (page + 1) + "/" + totalPages : "")));
        holder.inventory = inv;

        for (int i = start; i < end; i++) {
            ClanColorManager.ClanColorEntry entry = allColors.get(i);
            int slot = slots[i - start];

            boolean isActive = clan.getColorHex().equals(entry.gradiente());
            boolean hasPerm  = player.hasPermission(entry.permissao()) || player.isOp();

            String mat = "PAPER";
            String displayName = entry.displayName();
            List<String> lore;

            if (isActive) {
                lore = List.of("<#10fc46>Selecionado!");
            } else if (hasPerm) {
                lore = List.of("", "<color:#E6CFFE>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴀᴛɪᴠᴀʀ");
            } else {
                lore = List.of("", "<#848c94>Utilize um item", "<#848c94>de cor para", "<#848c94>liberar essa tag!");
            }

            inv.setItem(slot, buildItem(null, mat, displayName, lore));
        }

        // Navegação
        if (page > 0) {
            inv.setItem(18, buildItem(null, "ARROW", "<#b1fcb6>← Página anterior", List.of()));
        }
        if (end < allColors.size()) {
            inv.setItem(26, buildItem(null, "ARROW", "<#b1fcb6>Próxima página →", List.of()));
        }
        inv.setItem(45, buildItem(null, "ARROW", "<#b1fcb6>Voltar", List.of("<#848c94>Voltar ao menu do clan")));
        inv.setItem(49, buildItem(null, "RED_DYE", "<#EF2E2E>Fechar", List.of()));
        player.openInventory(inv);
    }

    /** Sobrecarga para abrir a página 0 por padrão */
    public static void openColorGUI(Player player, Clan clan) {
        openColorGUI(player, clan, 0);
    }

    // ─────────────────────────────────────────────
    //  Permissões
    // ─────────────────────────────────────────────
    public static void openPermissionsGUI(Player player, Clan clan) {
        ClanGUI holder = new ClanGUI();
        holder.guiType = "permissions";
        Inventory inv = Bukkit.createInventory(holder, 54, mm.deserialize("<dark_gray>Permissões do Clan"));
        holder.inventory = inv;

        ClanManager cm = PSDK.getInstance().getClanManager();
        List<ClanMember> members = clan.getMembers();
        int[] slots = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33};

        int idx = 0;
        for (ClanMember member : members) {
            if (idx >= slots.length) break;
            ClanManager.ClanPerm perms = cm.getPermissions(clan.getId(), member.uuid());
            boolean isLeaderMember = member.role().equals("lider");
            Player mp = Bukkit.getPlayer(member.uuid());

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(mm.deserialize(permLine("Convidar", perms.invite())).decoration(TextDecoration.ITALIC, false));
            lore.add(mm.deserialize(permLine("Expulsar", perms.kick())).decoration(TextDecoration.ITALIC, false));
            lore.add(mm.deserialize(permLine("Baú", perms.chest())).decoration(TextDecoration.ITALIC, false));
            lore.add(mm.deserialize(permLine("Mercado", perms.market())).decoration(TextDecoration.ITALIC, false));
            lore.add(mm.deserialize(permLine("Tesouro", perms.treasury())).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(mm.deserialize(isLeaderMember
                    ? "<#ff9008>Líder — permissões totais"
                    : "<#ffd250>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ɢᴇʀᴇɴᴄɪᴀʀ").decoration(TextDecoration.ITALIC, false));

            ItemStack head = buildItem(mp, "{player}", "", List.of());
            ItemMeta meta = head.getItemMeta();
            if (meta != null) {
                Component nick = mp != null ? resolveNick(mp) : mm.deserialize("<#cbd1d7>" + member.name());
                Component namePrefix = isLeaderMember
                        ? mm.deserialize("<reset>")
                        : mm.deserialize("<#cbd1d7>");
                meta.displayName(Component.empty().decoration(TextDecoration.ITALIC, false).append(namePrefix).append(nick));
                meta.lore(lore);
                head.setItemMeta(meta);
            }

            inv.setItem(slots[idx], head);
            idx++;
        }

        inv.setItem(49, buildItem(null, "ARROW", "<#b1fcb6>Voltar", List.of()));
        player.openInventory(inv);
    }

    public static void openPermissionEditGUI(Player player, ClanMember target, Clan clan) {
        ClanGUI holder = new ClanGUI();
        holder.guiType = "perm_edit";
        Inventory inv = Bukkit.createInventory(holder, 36,
                mm.deserialize("<dark_gray>Permissões - " + target.name()));
        holder.inventory = inv;

        ClanManager cm = PSDK.getInstance().getClanManager();
        ClanManager.ClanPerm perms = cm.getPermissions(clan.getId(), target.uuid());

        inv.setItem(10, buildPermToggle("NETHER_STAR", "Convidar", perms.invite(), "Permitir convidar jogadores"));
        inv.setItem(12, buildPermToggle("IRON_SWORD", "Expulsar", perms.kick(), "Permitir expulsar membros"));
        inv.setItem(14, buildPermToggle("CHEST", "Baú", perms.chest(), "Permitir usar o baú do clan"));
        inv.setItem(16, buildPermToggle("EMERALD", "Mercado", perms.market(), "Permitir usar o mercado"));
        inv.setItem(20, buildPermToggle("FLINT_AND_STEEL", "Toggle PvP", perms.pvpToggle(), "Permitir ativar/desativar PvP aliado"));
        inv.setItem(24, buildPermToggle("RECOVERY_COMPASS", "Tesouro", perms.treasury(), "Permitir sacar do tesouro do clan"));

        inv.setItem(31, buildItem(null, "ARROW", "<#b1fcb6>Voltar", List.of()));
        player.openInventory(inv);
    }

    // ─────────────────────────────────────────────
    //  Cargos personalizados
    // ─────────────────────────────────────────────
    /** Slots onde os cargos são listados (2 linhas de 7). */
    public static final int[] ROLE_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    /** Lista os cargos do clan; líder pode criar, editar e excluir. */
    public static void openRolesGUI(Player player, Clan clan) {
        ClanGUI holder = new ClanGUI();
        holder.guiType = "roles";
        Inventory inv = Bukkit.createInventory(holder, 54,
                mm.deserialize("<dark_gray>Cargos — " + clan.getTag()));
        holder.inventory = inv;

        ClanManager cm = PSDK.getInstance().getClanManager();
        List<ClanManager.ClanRole> roles = cm.getRoles(clan.getId());
        boolean isLeader = clan.getLeader().equals(player.getUniqueId());

        // Item informativo do líder (cargo fixo, sempre no topo).
        inv.setItem(4, buildItem(null, "GOLDEN_HELMET", "<#ff9008>Líder", List.of(
                "<#848c94>Cargo fixo do dono do clan.",
                "<#848c94>Tem todas as permissões.",
                "",
                "<#848c94>Para trocar: <#cbd1d7>/clan transferir"
        )));

        for (int i = 0; i < ROLE_SLOTS.length; i++) {
            if (i >= roles.size()) break;
            ClanManager.ClanRole role = roles.get(i);
            List<String> lore = new ArrayList<>(List.of(
                    "<#848c94>Posição: <#cbd1d7>" + (i + 1) + "º (abaixo do líder)",
                    "",
                    permLine("Convidar", role.invite()),
                    permLine("Expulsar", role.kick()),
                    permLine("Baú", role.chest()),
                    permLine("Mercado", role.market()),
                    permLine("Toggle PvP", role.pvpToggle()),
                    permLine("Tesouro", role.treasury())
            ));
            if (isLeader) {
                lore.add("");
                lore.add("<#fcc850>Clique para editar");
                if (roles.size() > 1) lore.add("<#e22c27>Shift+Clique para excluir");
            }
            inv.setItem(ROLE_SLOTS[i], buildItem(null, "NAME_TAG", "<#fcc850>" + role.name(), lore));
        }

        if (isLeader && roles.size() < ClanManager.MAX_ROLES) {
            inv.setItem(40, buildItem(null, "LIME_DYE", "<#10fc46>Criar cargo", List.of(
                    "<#848c94>Digite o nome do novo cargo",
                    "<#848c94>no chat após clicar.",
                    "",
                    "<#10fc46>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴄʀɪᴀʀ"
            )));
        }
        inv.setItem(49, buildItem(null, "ARROW", "<#b1fcb6>Voltar", List.of()));
        player.openInventory(inv);
    }

    /** Edição de um cargo: toggles de permissão + renomear. */
    public static void openRoleEditGUI(Player player, Clan clan, ClanManager.ClanRole role) {
        ClanGUI holder = new ClanGUI();
        holder.guiType = "role_edit";
        holder.roleId = role.id();
        Inventory inv = Bukkit.createInventory(holder, 36,
                mm.deserialize("<dark_gray>Cargo - " + role.name()));
        holder.inventory = inv;

        inv.setItem(10, buildPermToggle("NETHER_STAR", "Convidar", role.invite(), "Permitir convidar jogadores"));
        inv.setItem(12, buildPermToggle("IRON_SWORD", "Expulsar", role.kick(), "Permitir expulsar membros"));
        inv.setItem(14, buildPermToggle("CHEST", "Baú", role.chest(), "Permitir usar o baú do clan"));
        inv.setItem(16, buildPermToggle("EMERALD", "Mercado", role.market(), "Permitir usar o mercado"));
        inv.setItem(20, buildPermToggle("FLINT_AND_STEEL", "Toggle PvP", role.pvpToggle(), "Permitir ativar/desativar PvP aliado"));
        inv.setItem(24, buildPermToggle("RECOVERY_COMPASS", "Tesouro", role.treasury(), "Permitir sacar do tesouro do clan"));

        inv.setItem(22, buildItem(null, "NAME_TAG", "<#fcc850>Renomear cargo", List.of(
                "<#848c94>Nome atual: <#cbd1d7>" + role.name(),
                "<#848c94>Digite o novo nome no chat",
                "<#848c94>após clicar (2-16 caracteres).",
                "",
                "<#fcc850>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ʀᴇɴᴏᴍᴇᴀʀ"
        )));

        inv.setItem(31, buildItem(null, "ARROW", "<#b1fcb6>Voltar", List.of()));
        player.openInventory(inv);
    }

    /** Escolha de cargo para um membro (aberto pelo líder com clique direito no membro). */
    public static void openRoleSelectGUI(Player player, Clan clan, ClanMember target, int returnPage) {
        ClanGUI holder = new ClanGUI();
        holder.guiType = "role_select";
        holder.targetUuid = target.uuid();
        holder.returnPage = returnPage;
        Inventory inv = Bukkit.createInventory(holder, 54,
                mm.deserialize("<dark_gray>Cargo de " + target.name()));
        holder.inventory = inv;

        List<ClanManager.ClanRole> roles = PSDK.getInstance().getClanManager().getRoles(clan.getId());
        for (int i = 0; i < ROLE_SLOTS.length; i++) {
            if (i >= roles.size()) break;
            ClanManager.ClanRole role = roles.get(i);
            boolean current = role.name().equals(target.role());
            inv.setItem(ROLE_SLOTS[i], buildItem(null, current ? "LIME_DYE" : "NAME_TAG",
                    "<#fcc850>" + role.name(), current
                            ? List.of("<#10fc46>Cargo atual deste membro.")
                            : List.of(
                                    "<#848c94>Posição: <#cbd1d7>" + (i + 1) + "º",
                                    "",
                                    "<#10fc46>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴀᴛʀɪʙᴜɪʀ"
                            )));
        }

        inv.setItem(49, buildItem(null, "ARROW", "<#b1fcb6>Voltar", List.of()));
        player.openInventory(inv);
    }

    private static ItemStack buildPermToggle(String material, String name, boolean enabled, String desc) {
        return buildItem(null, material, "<color:#FFA500>" + name, List.of(
                "<#cbd1d7>" + desc,
                "",
                "<#cbd1d7>Status: " + (enabled ? "<#10fc46>Ativa" : "<#e22c27>Desativada"),
                "",
                enabled ? "<#e22c27>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴅᴇsᴀᴛɪᴠᴀʀ" : "<#10fc46>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴀᴛɪᴠᴀʀ"
        ));
    }

    public static void openClanChest(Player player, Clan clan) {
        openClanChest(player, clan, 0);
    }

    public static void openClanChest(Player player, Clan clan, int page) {
        page = Math.max(0, Math.min(page, CHEST_MAX_PAGES - 1));

        // Anti-dupe: impede que dois jogadores abram o mesmo baú ao mesmo tempo.
        // ATÔMICO (putIfAbsent): dois jogadores no mesmo tick não conseguem abrir os dois.
        UUID current = chestLocks.putIfAbsent(clan.getId(), player.getUniqueId());
        if (current != null && !current.equals(player.getUniqueId())) {
            Player using = Bukkit.getPlayer(current);
            if (using != null && using.isOnline()) {
                player.sendMessage(mm.deserialize("<#FF0000>O baú do clan já está sendo usado por <#fcc850>" + using.getName() + "<#FF0000>. Aguarde ele fechar."));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
                return;
            }
            chestLocks.put(clan.getId(), player.getUniqueId());   // trava órfã (dono offline): assume
        }

        ClanGUI holder = new ClanGUI();
        holder.guiType = "chest";
        holder.clanId = clan.getId();
        holder.chestPage = page;
        Inventory inv = Bukkit.createInventory(holder, 54,
                mm.deserialize("<dark_gray>Baú do Clan - " + clan.getTag()
                        + " <gray>(" + (page + 1) + "/" + CHEST_MAX_PAGES + ")"));
        holder.inventory = inv;

        // Preenche o "vidro" decorativo nos slots que NÃO são de armazenamento nem botões,
        // evitando que itens sejam largados em slots que não são salvos.
        ItemStack pane = buildItem(null, "GRAY_STAINED_GLASS_PANE", " ", List.of());
        for (int i = 0; i < 54; i++) {
            if (!isChestStorageSlot(i) && i != 49 && i != 45 && i != 53) inv.setItem(i, pane);
        }

        ClanManager cm = PSDK.getInstance().getClanManager();
        String data = cm.getChestData(clan.getId(), page);
        if (data != null && !data.isEmpty()) {
            ItemStack[] contents = deserializeInventory(data);
            if (contents != null) {
                for (int i = 0; i < CHEST_SLOTS.length && i < contents.length; i++) {
                    if (contents[i] != null) inv.setItem(CHEST_SLOTS[i], contents[i]);
                }
            }
        }

        if (page > 0) {
            inv.setItem(45, buildItem(null, "ARROW", "<#b1fcb6>← Página " + page, List.of()));
        } else {
            inv.setItem(45, pane);
        }
        if (page + 1 < CHEST_MAX_PAGES) {
            inv.setItem(53, buildItem(null, "ARROW", "<#b1fcb6>Página " + (page + 2) + " →", List.of()));
        } else {
            inv.setItem(53, pane);
        }
        inv.setItem(49, buildItem(null, "RED_DYE", "<#EF2E2E>Fechar", List.of()));

        player.openInventory(inv);
        // O openInventory dispara o InventoryCloseEvent da página anterior, que salva
        // e LIBERA a trava — recoloca aqui para o baú continuar travado durante a navegação.
        chestLocks.put(clan.getId(), player.getUniqueId());
    }

    public static void saveClanChest(Inventory inv, int clanId, int page) {
        ItemStack[] items = new ItemStack[CHEST_SLOTS.length];
        for (int i = 0; i < CHEST_SLOTS.length; i++) items[i] = inv.getItem(CHEST_SLOTS[i]);
        PSDK.getInstance().getClanManager().saveChestData(clanId, page, serializeInventory(items));
    }

    // ─────────────────────────────────────────────
    //  Mercado
    // ─────────────────────────────────────────────
    public static void openMarketGUI(Player player, Clan clan) {
        ClanGUI holder = new ClanGUI();
        holder.guiType = "market";
        Inventory inv = Bukkit.createInventory(holder, 54,
                mm.deserialize("<dark_gray>Mercado - " + clan.getTag()));
        holder.inventory = inv;

        ClanManager cm = PSDK.getInstance().getClanManager();
        List<ClanManager.MarketItem> items = cm.getMarketItems(clan.getId());
        int[] slots = {10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34};

        for (int i = 0; i < items.size() && i < slots.length; i++) {
            ClanManager.MarketItem mi = items.get(i);
            ItemStack display = deserializeItem(mi.itemData());
            if (display == null) display = new ItemStack(Material.BARRIER);

            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<Component> lore = new ArrayList<>();
                if (meta.lore() != null) lore.addAll(meta.lore());
                lore.add(Component.empty());

                Player sellerPlayer = Bukkit.getPlayer(mi.seller());
                Component sellerNick = sellerPlayer != null
                        ? resolveNick(sellerPlayer)
                        : mm.deserialize("<#cbd1d7>" + mi.sellerName()).decoration(TextDecoration.ITALIC, false);

                lore.add(mm.deserialize("<#fc782c>Informações"));
                lore.add(Component.empty().decoration(TextDecoration.ITALIC, false)
                        .append(mm.deserialize("<#fc782c><bold>|</bold> <#cbd1d7>Vendedor: "))
                        .append(sellerNick));
                lore.add(mm.deserialize("<#fc782c><bold>|</bold> <#cbd1d7>Valor: <#00b231>$<#cbd1d7>" + String.format("%.0f", mi.price()) + " coins").decoration(TextDecoration.ITALIC, false));
                lore.add(Component.empty());
                lore.add(mm.deserialize(mi.seller().equals(player.getUniqueId())
                        ? "<#e22c27>Você é o vendedor"
                        : "<#10fc46>Clique para comprar").decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
                display.setItemMeta(meta);
            }
            inv.setItem(slots[i], display);
        }

        // Slot 50 - Vender item
        inv.setItem(50, buildItem(null, "GOLD_INGOT", "<#ffd250>Vender", List.of(
                "<#848c94>Segure o item na mão principal",
                "<#848c94>e clique aqui para listar.",
                "",
                "<#ffd250>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴠᴇɴᴅᴇʀ"
        )));
        // Slot 48 - Voltar
        inv.setItem(48, buildItem(null, "ARROW", "<#b1fcb6>Voltar", List.of()));
        player.openInventory(inv);
    }

    // ─────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────
    private static String permLine(String name, boolean enabled) {
        return "<#cbd1d7>" + name + ": " + (enabled ? "<#10fc46>✔" : "<red>✘");
    }

    public static ItemStack buildItem(Player owner, String materialName, String name, List<String> lore) {
        ItemStack item;
        if (materialName.equalsIgnoreCase("{player}")) {
            item = new ItemStack(Material.PLAYER_HEAD);
            if (owner != null) {
                SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
                if (skullMeta != null) {
                    skullMeta.setOwningPlayer(owner);
                    item.setItemMeta(skullMeta);
                }
            }
        } else {
            Material mat;
            try { mat = Material.valueOf(materialName.toUpperCase()); }
            catch (Exception e) { mat = Material.BARRIER; }
            item = new ItemStack(mat);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (!name.isEmpty()) {
                meta.displayName(mm.deserialize(name).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore.stream()
                    .map(line -> mm.deserialize(line).decoration(TextDecoration.ITALIC, false))
                    .collect(Collectors.toList()));
            item.setItemMeta(meta);
        }
        return item;
    }

    // ─────────────────────────────────────────────
    //  Serialização
    // ─────────────────────────────────────────────
    public static String serializeInventory(ItemStack[] items) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {
            oos.writeInt(items.length);
            for (ItemStack item : items) oos.writeObject(item);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) { return ""; }
    }

    public static ItemStack[] deserializeInventory(String data) {
        if (data == null || data.isEmpty()) return null;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bais)) {
            int size = ois.readInt();
            ItemStack[] items = new ItemStack[size];
            for (int i = 0; i < size; i++) items[i] = (ItemStack) ois.readObject();
            return items;
        } catch (Exception e) { return null; }
    }

    public static String serializeItem(ItemStack item) { return serializeInventory(new ItemStack[]{item}); }

    public static ItemStack deserializeItem(String data) {
        ItemStack[] items = deserializeInventory(data);
        return (items != null && items.length > 0) ? items[0] : null;
    }

    private static long getJoinDate(Player player, Clan clan) {
        for (ClanMember m : clan.getMembers()) {
            if (m.uuid().equals(player.getUniqueId())) return m.joinedAt();
        }
        return System.currentTimeMillis();
    }
}
