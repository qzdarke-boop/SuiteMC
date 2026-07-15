package com.psdk.clan;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Trata clique direito em:
 *  1. Chave de Cor específica  → concede a permissão da cor
 *  2. Pacotinho de Cor         → sorteia uma cor do pool e concede
 */
public class ClanColorActivateListener implements Listener {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private final PSDK plugin;
    private final ClanColorKeyManager keyManager;

    public ClanColorActivateListener(PSDK plugin) {
        this.plugin     = plugin;
        this.keyManager = new ClanColorKeyManager(plugin);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) return;

        // ── Pacotinho customizado (admin-created) ──────────
        String customPacketName = plugin.getColorPacketManager().getPacketNameFromItem(item);
        if (customPacketName != null) {
            event.setCancelled(true);
            handleCustomPacket(player, item, customPacketName);
            return;
        }

        // ── Pacotinho aleatório (padrão) ───────────────────
        ClanColorKeyManager.PacketType packetType = keyManager.getPacketTypeFromItem(item);
        if (packetType != null) {
            event.setCancelled(true);
            handlePacket(player, item, packetType);
            return;
        }

        // ── Chave específica ───────────────────────────────
        String colorName = keyManager.getColorNameFromKey(item);
        if (colorName != null) {
            event.setCancelled(true);
            handleSpecificKey(player, item, colorName);
        }
    }

    // ════════════════════════════════════════════════════════
    //  PACOTINHO CUSTOMIZADO
    // ════════════════════════════════════════════════════════

    private void handleCustomPacket(Player player, ItemStack item, String packetName) {
        ColorPacketManager cpm = plugin.getColorPacketManager();
        ColorPacketManager.ColorPacket packet = cpm.getPacket(packetName);
        if (packet == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Este pacotinho não existe mais no servidor."));
            return;
        }
        if (packet.colorNames().isEmpty()) {
            player.sendMessage(mm.deserialize("<#FF0000>Este pacotinho não tem cores configuradas!"));
            return;
        }

        ClanCommand.ClanColor color = cpm.rollColor(packet, player);
        if (color == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Nenhuma cor válida encontrada neste pacotinho."));
            return;
        }

        boolean alreadyOwned = ClanCommand.hasColorPermission(player, color);
        if (grantPermission(player, color.permission(), () -> {
            String colorDisplay = buildColorDisplay(color);
            if (alreadyOwned) {
                player.sendMessage(mm.deserialize("<#fcc850>Você já tinha todas! Recebeu novamente: " + colorDisplay));
            } else {
                player.sendMessage(mm.deserialize("<#10fc46>Pacotinho aberto! Cor desbloqueada: " + colorDisplay));
                player.sendMessage(mm.deserialize("<#848c94>Use <white>/clan <#848c94>→ <white>Cor do Clan <#848c94>para aplicar."));
            }
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        })) consumeItem(player, item);
    }

    // ════════════════════════════════════════════════════════
    //  PACOTINHO PADRÃO
    // ════════════════════════════════════════════════════════

    private void handlePacket(Player player, ItemStack item, ClanColorKeyManager.PacketType packetType) {
        ClanCommand.ClanColor color = keyManager.rollColor(packetType, player);
        if (color == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Não há cores disponíveis neste pacotinho!"));
            return;
        }

        boolean alreadyOwned = ClanCommand.hasColorPermission(player, color);

        if (grantPermission(player, color.permission(), () -> {
            String colorDisplay = buildColorDisplay(color);
            if (alreadyOwned) {
                player.sendMessage(mm.deserialize("<#fcc850>Você já tinha todas! Recebeu novamente: " + colorDisplay));
            } else {
                player.sendMessage(mm.deserialize("<#10fc46>Pacotinho aberto! Cor desbloqueada: " + colorDisplay));
                player.sendMessage(mm.deserialize("<#848c94>Use <white>/clan <#848c94>→ <white>Cor do Clan <#848c94>para aplicar."));
            }
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        })) consumeItem(player, item);
    }

    // ════════════════════════════════════════════════════════
    //  CHAVE ESPECÍFICA
    // ════════════════════════════════════════════════════════

    private void handleSpecificKey(Player player, ItemStack item, String colorName) {
        ClanCommand.ClanColor color = ClanColorKeyManager.findCommandColor(colorName);

        if (color != null) {
            // Cor está nas listas estáticas — fluxo normal
            if (ClanCommand.hasColorPermission(player, color)) {
                player.sendMessage(mm.deserialize(
                        "<#fcc850>Você já possui a cor " + buildColorDisplay(color) + " <#fcc850>desbloqueada!"));
                return;
            }
            if (grantPermission(player, color.permission(), () -> {
                player.sendMessage(mm.deserialize(
                        "<#10fc46>Cor " + buildColorDisplay(color) + " <#10fc46>desbloqueada!"));
                player.sendMessage(mm.deserialize("<#848c94>Use <white>/clan <#848c94>→ <white>Cor do Clan <#848c94>para aplicar."));
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
            })) consumeItem(player, item);
            return;
        }

        // Cor não está nas listas estáticas — pode ser uma cor do banco (via /clanAdmin color add).
        // A permissão fica gravada no PDC pelo createKeyItem, então lemos direto de lá.
        String perm = keyManager.getPermissionFromKey(item);
        if (perm == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Essa chave pertence a uma cor que não existe mais."));
            return;
        }

        if (player.hasPermission(perm) || player.isOp()) {
            player.sendMessage(mm.deserialize("<#fcc850>Você já possui esta cor desbloqueada!"));
            return;
        }

        if (grantPermission(player, perm, () -> {
            player.sendMessage(mm.deserialize("<#10fc46>Cor <white>" + colorName + " <#10fc46>desbloqueada!"));
            player.sendMessage(mm.deserialize("<#848c94>Use <white>/clan <#848c94>→ <white>Cor do Clan <#848c94>para aplicar."));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        })) consumeItem(player, item);
    }

    // ════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════

    /** Retorna true se a concessão foi iniciada (pré-condições OK); false (e avisa) caso contrário. */
    private boolean grantPermission(Player player, String permission, Runnable callback) {
        RegisteredServiceProvider<LuckPerms> provider =
                Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Erro: LuckPerms não encontrado no servidor."));
            return false;
        }
        LuckPerms lp   = provider.getProvider();
        User lpUser    = lp.getUserManager().getUser(player.getUniqueId());
        if (lpUser == null) {
            player.sendMessage(mm.deserialize("<#FF0000>Erro ao carregar seu perfil de permissões."));
            return false;
        }
        lpUser.data().add(Node.builder(permission).build());
        lp.getUserManager().saveUser(lpUser).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.recalculatePermissions();
                    callback.run();
                })
        );
        return true;
    }

    private String buildColorDisplay(ClanCommand.ClanColor color) {
        if (color.isGradient()) return color.hex() + color.name() + "</gradient>";
        return "<" + color.hex() + ">" + color.name();
    }

    private void consumeItem(Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }
}