package com.psdk.kits;

import com.psdk.social.SuiteStore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class KitsGUI {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public static final String TITLE      = "<#a4a4a4>Kits";
    public static final int    SLOT_BASICOS = 11;
    public static final int    SLOT_VIP     = 13;
    public static final int    SLOT_WEBSITE = 15;

    public static Inventory build() {
        KitsInventoryHolder holder = KitsInventoryHolder.main();
        Inventory inv = Bukkit.createInventory(holder, 27, MM.deserialize(TITLE));
        holder.bind(inv);

        inv.setItem(SLOT_BASICOS, makeButton(Material.CHEST,
                "<!italic><#fcc850>Kits Básicos",
                List.of(
                    "<!italic><#848c94>Veja os kits disponíveis",
                    "<!italic><#848c94>para todos os jogadores!",
                    "",
                    "<!italic><#10fc46>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴀᴄᴇssᴀʀ"
                )));

        inv.setItem(SLOT_VIP, makeButton(Material.NETHER_STAR,
                "<!italic><#fcc850>Kits VIP",
                List.of(
                    "<!italic><#848c94>Kits exclusivos para",
                    "<!italic><#848c94>jogadores VIP!",
                    "",
                    "<!italic><#10fc46>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴀᴄᴇssᴀʀ"
                )));

        // Loja da Suite: substitui o antigo ícone "Kits Website" (baú do ender) pela
        // cabeça personalizada. Ao clicar, envia o site no chat (ver KitsGUIListener).
        inv.setItem(SLOT_WEBSITE, SuiteStore.createHead(SuiteStore.Context.KITS_MAIN));

        return inv;
    }

    private static ItemStack makeButton(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(name));
        meta.lore(lore.stream().map(MM::deserialize).toList());
        item.setItemMeta(meta);
        return item;
    }
}
