package com.psdk.settings;

import com.psdk.PSDK;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class SettingsGUI implements InventoryHolder {

    private Inventory inventory;

    private SettingsGUI() {}

    @Override
    public Inventory getInventory() { return inventory; }

    public static Inventory build(PSDK plugin, Player player) {
        SettingsGUI holder = new SettingsGUI();
        Inventory inv = Bukkit.createInventory(holder, 36, MiniMessage.miniMessage().deserialize(""));
        holder.inventory = inv;

        boolean tell = plugin.getSettingsManager().getSetting(player.getUniqueId(), "tell");
        ItemStack tellItem = new ItemStack(Material.OAK_SIGN);
        ItemMeta tellMeta = tellItem.getItemMeta();
        tellMeta.displayName(MiniMessage.miniMessage().deserialize("<!italic><#fcc850>Receber mensagens privadas de outros jogadores"));
        tellMeta.lore(List.of(
                Component.empty(),
                MiniMessage.miniMessage().deserialize("<!italic>  <#a4a4a4>Permite receber mensagens"),
                MiniMessage.miniMessage().deserialize("<!italic>  <#a4a4a4>privadas de outros jogadores."),
                Component.empty(),
                MiniMessage.miniMessage().deserialize("<!italic>  <bold><#cbd1d7>STATUS:</bold>"),
                MiniMessage.miniMessage().deserialize(tell ? "<!italic>  <#848c94>▸ ᴀᴛɪᴠᴀᴅᴏ" : "<!italic>  <#848c94>▸ Dᴇsᴀᴛɪᴠᴀᴅᴏ"),
                Component.empty(),
                MiniMessage.miniMessage().deserialize("<!italic>  <#a4a4a4>Clique para " + (tell ? "desativar" : "ativar"))
        ));
        tellItem.setItemMeta(tellMeta);
        inv.setItem(11, tellItem);

        boolean mentions = plugin.getSettingsManager().getSetting(player.getUniqueId(), "mentions");
        ItemStack mentionsItem = new ItemStack(Material.BELL);
        ItemMeta mentionsMeta = mentionsItem.getItemMeta();
        mentionsMeta.displayName(MiniMessage.miniMessage().deserialize("<!italic><#fcc850>Menções no chat"));
        mentionsMeta.lore(List.of(
                Component.empty(),
                MiniMessage.miniMessage().deserialize("<!italic>  <#a4a4a4>Receber notificação quando"),
                MiniMessage.miniMessage().deserialize("<!italic>  <#a4a4a4>alguém menciona seu nome."),
                Component.empty(),
                MiniMessage.miniMessage().deserialize("<!italic>  <bold><#cbd1d7>STATUS:</bold>"),
                MiniMessage.miniMessage().deserialize(mentions ? "<!italic>  <#848c94>▸ ᴀᴛɪᴠᴀᴅᴏ" : "<!italic>  <#848c94>▸ Dᴇsᴀᴛɪᴠᴀᴅᴏ"),
                Component.empty(),
                MiniMessage.miniMessage().deserialize("<!italic>  <#a4a4a4>Clique para " + (mentions ? "desativar" : "ativar"))
        ));
        mentionsItem.setItemMeta(mentionsMeta);
        inv.setItem(12, mentionsItem);

        boolean mentionSound = plugin.getSettingsManager().getSetting(player.getUniqueId(), "mention_sound");
        ItemStack soundItem = new ItemStack(Material.NOTE_BLOCK);
        ItemMeta soundMeta = soundItem.getItemMeta();
        soundMeta.displayName(MiniMessage.miniMessage().deserialize("<!italic><#fcc850>Som de menção"));
        soundMeta.lore(List.of(
                Component.empty(),
                MiniMessage.miniMessage().deserialize("<!italic>  <#a4a4a4>Tocar um som quando você"),
                MiniMessage.miniMessage().deserialize("<!italic>  <#a4a4a4>for mencionado no chat."),
                Component.empty(),
                MiniMessage.miniMessage().deserialize("<!italic>  <bold><#cbd1d7>STATUS:</bold>"),
                MiniMessage.miniMessage().deserialize(mentionSound ? "<!italic>  <#848c94>▸ ᴀᴛɪᴠᴀᴅᴏ" : "<!italic>  <#848c94>▸ Dᴇsᴀᴛɪᴠᴀᴅᴏ"),
                Component.empty(),
                MiniMessage.miniMessage().deserialize("<!italic>  <#a4a4a4>Clique para " + (mentionSound ? "desativar" : "ativar"))
        ));
        soundItem.setItemMeta(soundMeta);
        inv.setItem(13, soundItem);

        boolean chatVisible = plugin.getSettingsManager().getSetting(player.getUniqueId(), "chat_visible");
        ItemStack chatItem = new ItemStack(Material.PAPER);
        ItemMeta chatMeta = chatItem.getItemMeta();
        chatMeta.displayName(MiniMessage.miniMessage().deserialize("<!italic><#fcc850>Ver chat"));
        chatMeta.lore(List.of(
                Component.empty(),
                MiniMessage.miniMessage().deserialize("<!italic>  <#a4a4a4>Exibir mensagens do chat"),
                MiniMessage.miniMessage().deserialize("<!italic>  <#a4a4a4>global no seu cliente."),
                Component.empty(),
                MiniMessage.miniMessage().deserialize("<!italic>  <bold><#cbd1d7>STATUS:</bold>"),
                MiniMessage.miniMessage().deserialize(chatVisible ? "<!italic>  <#848c94>▸ ᴀᴛɪᴠᴀᴅᴏ" : "<!italic>  <#848c94>▸ Dᴇsᴀᴛɪᴠᴀᴅᴏ"),
                Component.empty(),
                MiniMessage.miniMessage().deserialize("<!italic>  <#a4a4a4>Clique para " + (chatVisible ? "desativar" : "ativar"))
        ));
        chatItem.setItemMeta(chatMeta);
        inv.setItem(14, chatItem);

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.displayName(MiniMessage.miniMessage().deserialize("<!italic><#e22c27>Fechar"));
        closeMeta.lore(List.of(
                Component.empty(),
                MiniMessage.miniMessage().deserialize("<!italic>  <#848c94>Clique para fechar o menu.")
        ));
        close.setItemMeta(closeMeta);
        inv.setItem(31, close);

        return inv;
    }
}
