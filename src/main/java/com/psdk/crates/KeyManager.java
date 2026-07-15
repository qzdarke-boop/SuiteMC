package com.psdk.crates;

import com.psdk.PSDK;
import com.nexomc.nexo.api.NexoItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class KeyManager {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;
    private static final String CRATE_NAME_PDC = "psdk_crate_name";

    public KeyManager(PSDK plugin) {
        this.plugin = plugin;
    }

    public ItemStack createKeyItem(Crate crate, int amount) {
        String nexoId = crate.getNexoKeyId();
        if (nexoId != null && !nexoId.isEmpty()) {
            return createNexoKeyItem(crate, nexoId, amount);
        }

        CrateKey crateKey = crate.getItemChave();
        if (crateKey == null) return null;

        ItemStack item = new ItemStack(crateKey.getMaterial(), Math.max(1, Math.min(amount, 64)));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(mm.deserialize(crateKey.getDisplayName()));

        List<Component> lore = new ArrayList<>();
        lore.addAll(buildActivateLore(crate));
        meta.lore(lore);

        NamespacedKey nbtKey = new NamespacedKey(plugin, crateKey.getNbtKey());
        NamespacedKey crateNameKey = new NamespacedKey(plugin, CRATE_NAME_PDC);
        meta.getPersistentDataContainer().set(nbtKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(crateNameKey, PersistentDataType.STRING, crate.getNome());

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNexoKeyItem(Crate crate, String nexoId, int amount) {
        var builder = NexoItems.itemFromId(nexoId);
        if (builder == null) {
            plugin.getLogger().warning("O item'" + nexoId + "' não foi encontrado para crate: " + crate.getNome());
            return null;
        }

        ItemStack item = builder.build();
        item.setAmount(Math.max(1, Math.min(amount, 64)));

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Mantém o lore original do Nexo e adiciona o hover de ativar abaixo
        List<Component> lore = new ArrayList<>();
        List<Component> nexoLore = meta.lore();
        if (nexoLore != null && !nexoLore.isEmpty()) {
            lore.addAll(nexoLore);
            lore.add(Component.empty());
        }
        lore.addAll(buildActivateLore(crate));
        meta.lore(lore);

        NamespacedKey crateNameKey = new NamespacedKey(plugin, CRATE_NAME_PDC);
        meta.getPersistentDataContainer().set(crateNameKey, PersistentDataType.STRING, crate.getNome());

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Gera as linhas de hover de ativação usando a cor configurada na crate.
     */
    private List<Component> buildActivateLore(Crate crate) {
        String cor = crate.getCor();
        String nome = crate.getNome();
        String nomeCapital = nome.isEmpty() ? nome : Character.toUpperCase(nome.charAt(0)) + nome.substring(1);

        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());
        lines.add(mm.deserialize("<!italic><#848c94>Essa é uma chave da caixa " + cor + nomeCapital + "<#848c94>."));
        lines.add(Component.empty());
        lines.add(mm.deserialize("<!italic><#848c94>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴀᴛɪᴠᴀʀ"));
        return lines;
    }

    public boolean isValidKey(ItemStack item, Crate crate) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        NamespacedKey crateNameKey = new NamespacedKey(plugin, CRATE_NAME_PDC);
        String storedName = meta.getPersistentDataContainer().get(crateNameKey, PersistentDataType.STRING);
        if (crate.getNome().equals(storedName)) return true;

        CrateKey crateKey = crate.getItemChave();
        if (crateKey == null) return false;
        NamespacedKey nbtKey = new NamespacedKey(plugin, crateKey.getNbtKey());
        return meta.getPersistentDataContainer().has(nbtKey, PersistentDataType.BOOLEAN);
    }

    public String getCrateNameFromKey(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        NamespacedKey crateNameKey = new NamespacedKey(plugin, CRATE_NAME_PDC);
        return meta.getPersistentDataContainer().get(crateNameKey, PersistentDataType.STRING);
    }
}