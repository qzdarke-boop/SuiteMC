package com.psdk.pitems;

import com.psdk.PSDK;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Item especial <b>Troque de Posição</b> (Ovo encantado).
 *
 * <p>Ao arremessar e acertar DIRETAMENTE outro jogador na arena de PvP, os dois
 * trocam de posição. A habilidade e as validações ficam no
 * {@link TrocaPosicaoListener}; esta classe é apenas a <i>factory</i>/identidade
 * do item.
 *
 * <p><b>Sistema único:</b> o item é criado e identificado pelo {@link PSDKItems}
 * (enum {@link PSDKItems.ItemType#TROCA_POSICAO}), exatamente como a Cadeia. Assim
 * as versões permanente e temporária compartilham o mesmo {@code item_type} no PDC,
 * a lore segue o padrão da Caixa Eye e a expiração é tratada pelo
 * {@link PSDKItemExpireTask}.
 *
 * <p><b>Identidade segura:</b> reconhecido pelo {@code item_type} do PDC + material,
 * nunca por nome/lore/brilho — então um Ovo comum jamais ativa a habilidade e a
 * antiga Bola de Neve também não.
 */
public final class TrocaPosicaoItem {

    /** Material do item especial. Ovos comuns NÃO ativam a habilidade (ver PDC). */
    public static final Material MATERIAL = Material.EGG;

    /** Cooldown individual por jogador, em milissegundos. */
    public static final long COOLDOWN_MS = 5_000L;

    private TrocaPosicaoItem() {}

    /** Cria a versão PERMANENTE do item (sem expiração, sem "ITEM EXCLUSIVO"). */
    public static ItemStack create(PSDK plugin) {
        return PSDKItems.create(PSDKItems.ItemType.TROCA_POSICAO);
    }

    /** Cria a versão TEMPORÁRIA do item (expira após {@code durationMillis}). */
    public static ItemStack create(PSDK plugin, long durationMillis) {
        return PSDKItems.create(PSDKItems.ItemType.TROCA_POSICAO, durationMillis);
    }

    /**
     * True somente se o item for o Ovo especial (identificado pelo {@code item_type}
     * do PDC + material). Não checa expiração — quem usa a habilidade valida isso
     * separadamente para evitar consumir/colocar um item expirado.
     */
    public static boolean isSwapItem(PSDK plugin, ItemStack item) {
        if (item == null || item.getType() != MATERIAL) return false;
        return PSDKItems.ItemType.TROCA_POSICAO.getId().equals(PSDKItems.getItemTypeId(item));
    }
}
