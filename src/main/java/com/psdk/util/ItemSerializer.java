package com.psdk.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serializa ItemStack para Base64 e vice-versa.
 *
 * Estratégia: tenta primeiro o método Paper 1.21.4+
 * ({@code ItemStack.serializeAsBytes} / {@code ItemStack.deserializeBytes}), que
 * preserva 100% dos data-components modernos como {@code item_model} e
 * {@code custom_model_data}. Se não estiver disponível (versão mais antiga), cai
 * no {@code BukkitObjectOutputStream} legado, que perde esses componentes mas
 * mantém compatibilidade retroativa.
 *
 * Os dados salvos pelo método legado continuam legíveis pelo fallback abaixo; os
 * novos registros passam a usar o formato Paper e preservam texturas Nexo.
 */
public final class ItemSerializer {

    private static final Logger LOG = Logger.getLogger("PSDK");
    private static final boolean PAPER_API;

    static {
        boolean ok = false;
        try {
            ItemStack.class.getMethod("serializeAsBytes");
            ok = true;
        } catch (NoSuchMethodException ignored) {}
        PAPER_API = ok;
    }

    private ItemSerializer() {}

    public static String toBase64(ItemStack item) {
        if (item == null) return null;
        if (PAPER_API) {
            try {
                byte[] bytes = item.serializeAsBytes();
                return Base64.getEncoder().encodeToString(bytes);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Erro ao serializar item (Paper API)!", e);
                return null;
            }
        }
        // Fallback legado (preserva compatibilidade com versões antigas).
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream boos = new BukkitObjectOutputStream(baos)) {
            boos.writeObject(item);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro ao serializar item (legado)!", e);
            return null;
        }
    }

    public static ItemStack fromBase64(String data) {
        if (data == null || data.isEmpty()) return null;
        byte[] bytes = Base64.getDecoder().decode(data);
        if (PAPER_API) {
            try {
                return ItemStack.deserializeBytes(bytes);
            } catch (Exception paperEx) {
                // Dado pode ter sido salvo no formato legado; tenta o fallback.
                try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                     BukkitObjectInputStream bois = new BukkitObjectInputStream(bais)) {
                    return (ItemStack) bois.readObject();
                } catch (Exception legacyEx) {
                    LOG.log(Level.SEVERE, "Erro ao desserializar item (ambos os formatos falharam)!", legacyEx);
                    return null;
                }
            }
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream bois = new BukkitObjectInputStream(bais)) {
            return (ItemStack) bois.readObject();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro ao desserializar item (legado)!", e);
            return null;
        }
    }
}
