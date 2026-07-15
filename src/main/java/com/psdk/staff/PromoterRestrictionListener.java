package com.psdk.staff;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trava o cargo "promoter" (grupo do LuckPerms) enquanto ele está ONLINE:
 * desativa as permissões de abuso (criativo, tp, give, fly, op, vanish...) para
 * que o promoter não abuse jogando.
 *
 * <p>NÃO mexe no LuckPerms — a desativação é só em memória (PermissionAttachment
 * + interceptação de comando como rede de segurança). Ao SAIR, a trava some e o
 * jogador volta a ter tudo normalmente (o cargo continua intacto no LuckPerms).
 */
public class PromoterRestrictionListener implements Listener {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    /** Nome do grupo do LuckPerms que é travado. */
    private static final String PROMOTER_GROUP = "promoter";

    /** Permissões de abuso DESATIVADAS pro promoter enquanto online (negadas via attachment). */
    private static final String[] BLOCKED_PERMS = {
            // Gamemode / criativo
            "minecraft.command.gamemode", "minecraft.command.defaultgamemode", "bukkit.command.gamemode",
            "essentials.gamemode", "essentials.gamemode.creative", "essentials.gamemode.survival",
            "essentials.gamemode.adventure", "essentials.gamemode.spectator",
            // Teleporte
            "minecraft.command.teleport", "minecraft.command.tp", "bukkit.command.teleport",
            "essentials.tp", "essentials.tp.other", "essentials.tp.others", "essentials.tphere",
            "essentials.tpo", "essentials.tpohere", "essentials.tppos", "essentials.tpall",
            "essentials.tpa", "essentials.tpaall", "essentials.back", "essentials.warp", "essentials.home",
            // Voo / god / vanish / xray
            "essentials.fly", "essentials.god", "essentials.vanish", "essentials.invsee", "essentials.ext",
            "psdk.invsee", "psdk.ecsee",
            // Itens / efeitos
            "minecraft.command.give", "essentials.give", "essentials.item", "minecraft.command.enchant",
            "minecraft.command.effect", "minecraft.command.experience", "minecraft.command.xp",
            // Mundo / op
            "minecraft.command.summon", "minecraft.command.setblock", "minecraft.command.fill",
            "minecraft.command.clear", "minecraft.command.op", "minecraft.command.deop",
            "minecraft.command.kill", "minecraft.command.time", "minecraft.command.weather",
            "minecraft.command.gamerule", "minecraft.command.attribute", "bukkit.command.attribute",
            // Voz / voice chat
            "voicechat.admin", "voicechat.command.admin",
    };

    /** Rede de segurança: nomes de comando bloqueados pro promoter (caso a negação de perm não pegue). */
    private static final Set<String> BLOCKED_CMDS = Set.of(
            "gamemode", "gm", "gmc", "gms", "gma", "gmsp", "egamemode", "defaultgamemode",
            "tp", "teleport", "tphere", "tpo", "tpohere", "tpall", "tppos", "tpaall", "tpa", "etp",
            "give", "i", "item", "give2", "fly", "efly", "god", "egod", "vanish", "v", "evanish",
            "invsee", "op", "deop", "enchant", "effect", "xp", "experience", "exp",
            "summon", "setblock", "fill", "clear", "kill", "gamerule", "ext",
            "attribute", "voice"
    );

    private final PSDK plugin;
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();

    public PromoterRestrictionListener(PSDK plugin) {
        this.plugin = plugin;
        // Caso o plugin recarregue com jogadores já online.
        for (Player p : Bukkit.getOnlinePlayers()) apply(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        // 1 tick depois: garante que o LuckPerms já carregou os perms do jogador.
        plugin.getServer().getScheduler().runTask(plugin, () -> { if (p.isOnline()) apply(p); });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        restore(event.getPlayer());   // ao SAIR: reativa tudo (tira a trava)
    }

    // Rede de segurança: cancela os comandos de abuso pro promoter travado.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (!attachments.containsKey(p.getUniqueId())) return;   // só promoter travado
        String raw = event.getMessage();
        if (raw.length() < 2) return;
        String cmd = raw.substring(1).split(" ", 2)[0].toLowerCase();
        int colon = cmd.indexOf(':');                            // tira namespace ex: "minecraft:gamemode"
        if (colon >= 0) cmd = cmd.substring(colon + 1);
        if (BLOCKED_CMDS.contains(cmd)) {
            event.setCancelled(true);
            p.sendMessage(mm.deserialize("<#FF0000>Esse comando está <bold>desativado</bold> para o cargo promoter."));
        }
    }

    /**
     * Verdade SÓ se o jogador for REALMENTE do grupo promoter (via LuckPerms).
     * NÃO usa hasPermission("group.promoter") porque isso é true pra qualquer OP
     * (OP tem todas as permissões) — o que fazia o dev/admin ser tratado como promoter.
     */
    private boolean isPromoter(Player p) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(p.getUniqueId());
            if (user == null) return false;
            if (PROMOTER_GROUP.equalsIgnoreCase(user.getPrimaryGroup())) return true;
            // grupo atribuído DIRETO no usuário (não pega herança de grupos tipo dev->promoter)
            return user.getNodes(NodeType.INHERITANCE).stream()
                    .anyMatch(n -> PROMOTER_GROUP.equalsIgnoreCase(n.getGroupName()));
        } catch (Throwable t) {
            return false;   // LuckPerms ausente/erro -> não trava ninguém (seguro)
        }
    }

    /** Aplica a trava se for promoter; senão, garante que está sem trava. */
    private void apply(Player p) {
        if (isPromoter(p)) {
            if (attachments.containsKey(p.getUniqueId())) return;   // já travado
            PermissionAttachment att = p.addAttachment(plugin);
            for (String node : BLOCKED_PERMS) att.setPermission(node, false);
            attachments.put(p.getUniqueId(), att);
            p.recalculatePermissions();
            // Se estava em criativo/spectator (abuso), volta pra sobrevivência.
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
                p.setGameMode(GameMode.SURVIVAL);
            }
        } else {
            restore(p);   // não é mais promoter -> tira a trava
        }
    }

    private void restore(Player p) {
        PermissionAttachment att = attachments.remove(p.getUniqueId());
        if (att != null) {
            try { p.removeAttachment(att); } catch (IllegalArgumentException ignored) { }
            if (p.isOnline()) p.recalculatePermissions();
        }
    }
}
