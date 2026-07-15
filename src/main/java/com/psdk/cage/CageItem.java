package com.psdk.cage;

import com.psdk.PSDK;
import com.psdk.pitems.PSDKItems;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Item especial <b>Jaula</b> (vidro vermelho).
 *
 * <p>Ao clicar com o botão direito em um bloco de chão dentro da arena de PvP, cria
 * um cubo fechado de 8x8x8 de {@code RED_STAINED_GLASS} ao redor do local. A
 * habilidade e as validações ficam no {@link CageListener} + {@link CageManager};
 * esta classe é apenas a <i>factory</i>/identidade do item.
 *
 * <p><b>Sistema único:</b> o item é criado e identificado pelo {@link PSDKItems}
 * (enum {@link PSDKItems.ItemType#JAULA}), exatamente como a Cadeia e o Troque de
 * Posição. As versões permanente e temporária compartilham o mesmo {@code item_type}
 * no PDC, a lore segue o padrão da Caixa Eye e a expiração da versão temporária é
 * tratada pelo {@code PSDKItemExpireTask}.
 *
 * <p><b>Identidade segura:</b> reconhecido pelo {@code item_type} do PDC + material,
 * nunca por nome/lore/brilho — então um vidro vermelho comum jamais ativa a habilidade.
 */
public final class CageItem {

    /** Material físico do item. Vidros vermelhos comuns NÃO ativam a habilidade (ver PDC). */
    public static final Material MATERIAL = Material.RED_STAINED_GLASS;

    private CageItem() {}

    /** Cria a versão PERMANENTE do item (sem expiração, sem "ITEM EXCLUSIVO"). */
    public static ItemStack create(PSDK plugin) {
        return PSDKItems.create(PSDKItems.ItemType.JAULA);
    }

    /** Cria a versão TEMPORÁRIA do item (expira após {@code durationMillis}). */
    public static ItemStack create(PSDK plugin, long durationMillis) {
        return PSDKItems.create(PSDKItems.ItemType.JAULA, durationMillis);
    }

    /**
     * True somente se o item for a Jaula especial (identificada pelo {@code item_type}
     * do PDC + material). Não checa expiração — o {@link CageListener} valida isso
     * separadamente para não colocar um vidro vermelho comum ao usar item expirado.
     */
    public static boolean isCageItem(PSDK plugin, ItemStack item) {
        if (item == null || item.getType() != MATERIAL) return false;
        return PSDKItems.ItemType.JAULA.getId().equals(PSDKItems.getItemTypeId(item));
    }
}
