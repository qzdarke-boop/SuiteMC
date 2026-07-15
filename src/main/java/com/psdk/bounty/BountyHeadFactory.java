package com.psdk.bounty;

import com.psdk.util.ClanText;
import com.psdk.util.SkullUtils;
import com.psdk.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

/** Cria a cabeça do jogador com o nome/rank colorido — usado nas duas GUIs de bounty. */
final class BountyHeadFactory {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private BountyHeadFactory() {}

    /** Componente do nome com rank (online via LuckPerms; offline = nome puro). */
    static Component displayName(UUID uuid, String name) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            try {
                String tag = ClanText.resolvePlayerTag(online);
                return mm.deserialize(TextUtil.legacyToMiniMessage(tag)).decoration(TextDecoration.ITALIC, false);
            } catch (Exception ignored) {}
        }
        return Component.text(name).decoration(TextDecoration.ITALIC, false);
    }

    /** Cabeça do jogador com nome aplicado (lore deve ser setada pelo chamador). */
    static ItemStack head(UUID uuid, String name) {
        ItemStack head = SkullUtils.createOwned(Bukkit.getOfflinePlayer(uuid));
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName(uuid, name));
            head.setItemMeta(meta);
        }
        return head;
    }
}
