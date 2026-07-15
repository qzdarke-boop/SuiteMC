package com.psdk.clan;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

/**
 * Confirmação de ações destrutivas do clan (padrão BountyConfirmGUI):
 * desfazer o clan e expulsar membro.
 */
public class ClanConfirmGUI implements InventoryHolder {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    public static final int CANCEL_SLOT = 11;
    public static final int INFO_SLOT = 13;
    public static final int CONFIRM_SLOT = 15;

    public enum Action { DISBAND, KICK }

    private final Inventory inventory;
    private final Action action;
    private final int clanId;
    private final UUID targetUuid;   // só para KICK
    private final String targetName; // só para KICK
    private final int returnPage;    // página de membros para reabrir após KICK

    private ClanConfirmGUI(Action action, int clanId, UUID targetUuid, String targetName, int returnPage) {
        this.action = action;
        this.clanId = clanId;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.returnPage = returnPage;
        this.inventory = Bukkit.createInventory(this, 27, mm.deserialize(
                action == Action.DISBAND ? "<dark_gray>Desfazer o clan?" : "<dark_gray>Expulsar " + targetName + "?"));
    }

    public static void openDisband(Player player, Clan clan) {
        ClanConfirmGUI holder = new ClanConfirmGUI(Action.DISBAND, clan.getId(), null, null, 0);
        holder.inventory.setItem(CANCEL_SLOT, pane(Material.RED_STAINED_GLASS_PANE, "<#FF5555>Cancelar"));
        holder.inventory.setItem(INFO_SLOT, item(Material.RED_BANNER, "<#e22c27>Desfazer o clan", List.of(
                line("<#848c94>O clan <white>[" + clan.getTag() + "] " + clan.getName()),
                line("<#848c94>será apagado permanentemente,"),
                line("<#848c94>incluindo baú, tesouro e mercado."),
                Component.empty(),
                line("<#e22c27>Esta ação não pode ser desfeita!")
        )));
        holder.inventory.setItem(CONFIRM_SLOT, pane(Material.GREEN_STAINED_GLASS_PANE, "<#10fc46>Confirmar"));
        player.openInventory(holder.inventory);
    }

    public static void openKick(Player player, Clan clan, ClanMember target, int returnPage) {
        ClanConfirmGUI holder = new ClanConfirmGUI(Action.KICK, clan.getId(), target.uuid(), target.name(), returnPage);
        holder.inventory.setItem(CANCEL_SLOT, pane(Material.RED_STAINED_GLASS_PANE, "<#FF5555>Cancelar"));
        holder.inventory.setItem(INFO_SLOT, item(Material.IRON_SWORD, "<#e22c27>Expulsar membro", List.of(
                line("<#848c94>Expulsar <white>" + target.name() + "<#848c94> do clan"),
                line("<#848c94><white>[" + clan.getTag() + "]<#848c94>?")
        )));
        holder.inventory.setItem(CONFIRM_SLOT, pane(Material.GREEN_STAINED_GLASS_PANE, "<#10fc46>Confirmar"));
        player.openInventory(holder.inventory);
    }

    private static ItemStack pane(Material mat, String name) {
        return item(mat, name, List.of());
    }

    private static ItemStack item(Material mat, String name, List<Component> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(line(name));
            if (!lore.isEmpty()) meta.lore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    private static Component line(String mini) {
        return mm.deserialize(mini).decoration(TextDecoration.ITALIC, false);
    }

    public Action getAction() { return action; }
    public int getClanId() { return clanId; }
    public UUID getTargetUuid() { return targetUuid; }
    public String getTargetName() { return targetName; }
    public int getReturnPage() { return returnPage; }

    @Override
    public Inventory getInventory() { return inventory; }
}
