package com.psdk.crates;

import com.psdk.util.SkullUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CrateGUI implements InventoryHolder {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    public static final Component TITLE = mm.deserialize("<#fcc850>Escolha sua recompensa abaixo");

    private static final String HEAD_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDAxYWZlOTczYzU0ODJmZGM3MWU2YWExMDY5ODgzM2M3OWM0MzdmMjEzMDhlYTlhMWEwOTU3NDZlYzI3NGEwZiJ9fX0=";

    private static final List<Integer> REWARD_SLOTS = List.of(10, 11, 12, 13, 14, 15, 16, 20, 21, 22);

    public static final int INFO_HEAD_SLOT = 28;

    public static final int[] BUY_SLOTS   = {29, 30, 31, 32, 33};
    public static final int[] BUY_AMOUNTS = {1, 3, 5, 10, 25};
    private static final Set<Integer> BUY_SLOT_SET = Set.of(29, 30, 31, 32, 33);

    private static final double[][] PACOTES = {
            {1,  0.00},
            {3,  0.05},
            {5,  0.10},
            {10, 0.15},
            {25, 0.25}
    };

    public double getPreco(int quantidade) {
        double total = crate.getPrecoToken() * quantidade;
        for (double[] p : PACOTES) {
            if ((int) p[0] == quantidade) return total * (1 - p[1]);
        }
        return total;
    }

    public double getDesconto(int quantidade) {
        for (double[] p : PACOTES) {
            if ((int) p[0] == quantidade) return p[1];
        }
        return 0;
    }

    private final Crate crate;
    private Inventory inventory;

    public CrateGUI(Crate crate, Player player, int saldo, double tokens) {
        this.crate = crate;
        this.inventory = Bukkit.createInventory(this, 36, TITLE);
        buildBase();
        refreshDynamic(player, saldo, tokens);
    }

    private void buildBase() {
        List<ItemStack> itens = crate.getItens();
        for (int i = 0; i < REWARD_SLOTS.size(); i++) {
            int slot = REWARD_SLOTS.get(i);
            if (i < itens.size() && itens.get(i) != null && !itens.get(i).getType().isAir()) {
                // Cópia APENAS para exibição: recebe o aviso "CLIQUE PARA COLETAR".
                // O item armazenado (crate.getItens()) permanece limpo, então o item
                // efetivamente entregue ao coletar não carrega essa lore.
                inventory.setItem(slot, decorateForDisplay(itens.get(i)));
            }
        }

    }

    /** Devolve uma cópia do item de recompensa com uma linha vazia + "CLIQUE PARA COLETAR" no fim da lore. */
    private static ItemStack decorateForDisplay(ItemStack original) {
        ItemStack display = original.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;

        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(mm.deserialize("<!italic><#848c94>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴄᴏʟᴇᴛᴀʀ"));
        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    public void refreshDynamic(Player player, int saldo, double tokens) {
        inventory.setItem(INFO_HEAD_SLOT, createInfoHead(player, saldo));

        int totalItens = (int) crate.getItens().stream()
                .filter(i -> i != null && !i.getType().isAir()).count();

        for (int i = 0; i < BUY_SLOTS.length; i++) {
            inventory.setItem(BUY_SLOTS[i], createBuyButton(BUY_AMOUNTS[i], tokens, totalItens));
        }
    }

    private ItemStack createInfoHead(Player player, int saldo) {
        boolean exclusiva = crate.getTipo() == Crate.Tipo.EXCLUSIVA;
        String cor = crate.getCor();

        ItemStack head = SkullUtils.createTextured(HEAD_TEXTURE);
        ItemMeta meta = head.getItemMeta();
        if (meta == null) return head;

        meta.displayName(exclusiva
                ? mm.deserialize("<!italic>" + cor + "<bold>" + crate.getNome().toUpperCase() + "</bold> <#848c94>[ᴇxᴄʟᴜsɪᴠᴀ]")
                : mm.deserialize("<!italic>" + cor + "<bold>" + crate.getNome().toUpperCase()));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(mm.deserialize("<!italic><#848c94>Você precisa de chaves ativas"));
        lore.add(mm.deserialize("<!italic><#848c94>deste tipo para escolher"));
        lore.add(mm.deserialize("<!italic><#848c94>uma das recompensas."));
        lore.add(Component.empty());
        lore.add(mm.deserialize("  <!italic><#cbd1d7>Você tem " + cor + saldo + " <#cbd1d7>chave(s)."));
        lore.add(Component.empty());

        if (exclusiva) {
            int restantes = crate.getLimiteGlobal();
            lore.add(Component.empty());
            lore.add(mm.deserialize("  <!italic><#cc0000>✨ <bold>EXCLUSIVA</bold> ✨"));
            lore.add(mm.deserialize(" <!italic><#848c94>Apenas <#cbd1d7>" + restantes + " <#848c94>restantes!"));
            lore.add(Component.empty());
        }

        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack createBuyButton(int quantidade, double playerTokens, int totalItens) {
        String cor = crate.getCor();
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK, quantidade);
        ItemMeta meta = key.getItemMeta();
        if (meta == null) return key;

        double precoOriginal = crate.getPrecoToken() * quantidade;
        double desconto = getDesconto(quantidade);
        double precoFinal = getPreco(quantidade);
        boolean podeComprar = playerTokens >= precoFinal;

        String corStatus = podeComprar ? "<#10fc46>" : "<#e22c27>";
        String label = quantidade == 1 ? "chave" : "chaves";
        meta.displayName(mm.deserialize("<!italic>" + corStatus + "<bold>Comprar " + quantidade + " " + label + "!"));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(mm.deserialize("<!italic><#a4a4a4>Escolha uma das " + cor + totalItens));
        lore.add(mm.deserialize("<!italic><#a4a4a4>recompensas acima com"));
        lore.add(mm.deserialize("<!italic><#a4a4a4>estas chaves."));
        lore.add(Component.empty());
        lore.add(mm.deserialize("<!italic>" + corStatus + "Adquira já por apenas:"));

        if (desconto > 0) {
            lore.add(mm.deserialize("<!italic><#484f56><strikethrough>" + (int) precoOriginal +
                    "</strikethrough> <#cbd1d7>" + (int) precoFinal + " tokens."));
            lore.add(mm.deserialize("<!italic><bold><gradient:#FF4B4B:#FF8B2D:#FFD031:#DCFF39:#6DFF45:#3BFFC7:#58D0FF:#5489FF>DESCONTO DE " + (int) (desconto * 100) + "%</gradient>"));
        } else {
            lore.add(mm.deserialize("<!italic><#cbd1d7>" + (int) precoFinal + " tokens."));
        }

        lore.add(Component.empty());
        lore.add(podeComprar
                ? mm.deserialize("<!italic><#10fc46>Clique para comprar!")
                : mm.deserialize("<!italic><#e22c27>Tokens insuficientes."));

        meta.lore(lore);
        key.setItemMeta(meta);
        return key;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public boolean isRewardSlot(int slot) { return REWARD_SLOTS.contains(slot); }
    public boolean isBuySlot(int slot)    { return BUY_SLOT_SET.contains(slot); }
    public int getRewardIndex(int slot)   { return REWARD_SLOTS.indexOf(slot); }

    public int getBuyAmount(int slot) {
        for (int i = 0; i < BUY_SLOTS.length; i++) {
            if (BUY_SLOTS[i] == slot) return BUY_AMOUNTS[i];
        }
        return 0;
    }

    public Crate getCrate() { return crate; }

    @Override
    public Inventory getInventory() { return inventory; }

    public static List<Integer> getRewardSlots() { return REWARD_SLOTS; }
}
