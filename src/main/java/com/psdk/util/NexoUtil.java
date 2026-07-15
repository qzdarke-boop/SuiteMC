package com.psdk.util;

import com.psdk.PSDK;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Logger;

/**
 * Integração com o Nexo usando a API OFICIAL diretamente (compile-time), conforme
 * a documentação do Nexo (docs.nexomc.com): {@code NexoItems.itemFromId(id).build()}.
 *
 * Centraliza a criação de itens personalizados do Nexo para todos os sistemas
 * (itens especiais, pacotinhos de cor, chaves de cor, crates...). Se o Nexo não
 * estiver presente, o id não existir, ou a versão do Nexo for incompatível, volta
 * null (o chamador usa um fallback vanilla) e o motivo é logado no console.
 */
public final class NexoUtil {

    private NexoUtil() {}

    /**
     * Cria um {@link ItemStack} a partir de um id do Nexo, com o modelo/textura já
     * aplicados. Retorna {@code null} se não der (com o motivo no console).
     */
    public static ItemStack buildItem(String nexoId) {
        if (nexoId == null || nexoId.isBlank()) return null;
        Logger log = PSDK.getInstance().getLogger();

        if (!Bukkit.getPluginManager().isPluginEnabled("Nexo")) {
            log.warning("[Nexo] o plugin 'Nexo' não está ativo no servidor — item '"
                    + nexoId + "' criado como vanilla.");
            return null;
        }

        try {
            // API oficial do Nexo: itemFromId(id) -> ItemBuilder -> build() -> ItemStack.
            com.nexomc.nexo.items.ItemBuilder builder = com.nexomc.nexo.api.NexoItems.itemFromId(nexoId);
            if (builder == null) {
                log.warning("[Nexo] itemFromId('" + nexoId + "') = null — o id não existe ou os itens "
                        + "do Nexo ainda não carregaram. Confira com /nexo iteminfo. Item criado como vanilla.");
                return null;
            }
            ItemStack stack = builder.build();
            if (stack == null) {
                log.warning("[Nexo] build() retornou null para '" + nexoId + "'. Item criado como vanilla.");
                return null;
            }
            log.info("[Nexo] Item '" + nexoId + "' criado com modelo (" + stack.getType() + ").");
            return stack;
        } catch (Throwable t) {
            // NoSuchMethodError/NoClassDefFoundError aqui = versão do Nexo no servidor
            // diferente da API compilada (com.nexomc:nexo:0.9.0). Avisa claramente.
            log.warning("[Nexo] Falha ao criar '" + nexoId + "': " + t.getClass().getSimpleName()
                    + " - " + t.getMessage() + " — provável versão do Nexo incompatível com a API 0.9.0. "
                    + "Item criado como vanilla.");
            return null;
        }
    }
}
