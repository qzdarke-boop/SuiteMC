package com.psdk.chat;

import com.psdk.PSDK;
import com.psdk.social.SuiteStore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Mensagens automáticas do Skill Pit. A cada {@link #INTERVAL_TICKS} envia UMA mensagem
 * da rotação (report → kits → loja/cupom → bounty → mineração → colina), avançando um
 * índice sequencial: nunca repete a mesma mensagem duas vezes seguidas e distribui os
 * anúncios ao longo do tempo, sem spam e sem criar várias tasks.
 *
 * <p>Só envia aos jogadores online que mantêm a preferência {@code announcements} ligada,
 * no padrão de cores/formatação do projeto (MiniMessage). Como é uma {@link BukkitRunnable}
 * agendada uma única vez no onEnable e o Bukkit cancela as tasks do plugin no onDisable,
 * não há duplicação de tarefas após reload nem tarefas ativas após o desligamento.
 *
 * <p>Cada anúncio pode reportar-se como indisponível (retornando {@code null} em seu
 * fornecedor de linhas): nesse caso a rotação o pula automaticamente e envia o próximo
 * (ex.: a mensagem da Colina só aparece quando existe uma Colina ativa — nunca anuncia
 * uma mecânica desligada).
 */
public class AnnouncementManager extends BukkitRunnable {

    // ============= AJUSTES DAS MENSAGENS AUTOMÁTICAS (EDITE AQUI) =============
    /** Liga/desliga TODAS as mensagens automáticas. */
    public static final boolean ENABLED = true;
    /** Intervalo entre cada mensagem (ticks). 7200 = 6 min. Com a rotação abaixo,
     *  cada anúncio reaparece a cada (nº de anúncios ativos × 6 min). */
    public static final long INTERVAL_TICKS = 6 * 60 * 20L;

    // Liga/desliga individual de cada anúncio da rotação.
    private static final boolean REPORT_ENABLED = true;
    private static final boolean KITS_ENABLED   = true;
    private static final boolean STORE_ENABLED  = true;
    private static final boolean BOUNTY_ENABLED = true;
    private static final boolean MINING_ENABLED = true;
    private static final boolean COLINA_ENABLED = true;

    // Site, cupom diário e desconto vêm da fonte única {@link SuiteStore} (mesma usada
    // pelas cabeças da Loja nos menus). Edite lá para alterar site/cupons/desconto/fuso.
    // =========================================================================

    private final PSDK plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    /**
     * Fornecedores de linhas dos anúncios, na ordem da rotação. Cada um devolve o bloco
     * de linhas MiniMessage a enviar, ou {@code null} quando o anúncio está desligado/
     * indisponível (a rotação pula e envia o próximo).
     */
    private final List<Supplier<List<String>>> rotation = new ArrayList<>();
    /** Índice do próximo anúncio a enviar (rotação sequencial). */
    private int index = 0;

    public AnnouncementManager(PSDK plugin) {
        this.plugin = plugin;
        rotation.add(this::reportLines);
        rotation.add(this::kitsLines);
        rotation.add(this::storeLines);
        rotation.add(this::bountyLines);
        rotation.add(this::miningLines);
        rotation.add(this::colinaLines);
    }

    @Override
    public void run() {
        if (!ENABLED || rotation.isEmpty()) return;
        if (Bukkit.getOnlinePlayers().isEmpty()) return;

        // Procura o próximo anúncio DISPONÍVEL a partir do índice atual, avançando sempre
        // (garante que a próxima chamada continue a rotação de onde parou → sem repetir a
        // mesma mensagem em sequência). Se nenhum estiver disponível, não envia nada.
        for (int tries = 0; tries < rotation.size(); tries++) {
            Supplier<List<String>> ann = rotation.get(index);
            index = (index + 1) % rotation.size();
            List<String> lines = ann.get();
            if (lines != null && !lines.isEmpty()) {
                broadcast(lines);
                return;
            }
        }
    }

    /** Envia o bloco de linhas a todos os jogadores online com a preferência ligada. */
    private void broadcast(List<String> lines) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.getSettingsManager().getSetting(player.getUniqueId(), "announcements")) continue;
            for (String line : lines) {
                player.sendMessage(mm.deserialize(line));
            }
        }
    }

    // ────────────────────────────── ANÚNCIOS ──────────────────────────────────

    /** Lembrete de /report (mensagem original, preservada). */
    private List<String> reportLines() {
        if (!REPORT_ENABLED) return null;
        return List.of(
                "",
                "   <#e22c27><bold>Regras do Skill Pit!",
                "<#cbd1d7>Use <#6817ff>/report <#cbd1d7>para denunciar quem estiver infringindo as regras.",
                ""
        );
    }

    /** Kits gratuitos por /kits (recarregam de tempos em tempos — ver {@code Kit}). */
    private List<String> kitsLines() {
        if (!KITS_ENABLED) return null;
        return List.of(
                "",
                "   <#e22c27><bold>Kits Gratuitos!",
                "<#cbd1d7>Não esqueça de usar <#fcc850>/kits <#cbd1d7>e resgatar seus kits gratuitos de tempos em tempos!",
                ""
        );
    }

    /** Loja + VIP + cupom do dia (detectado dinamicamente pelo fuso do servidor). */
    private List<String> storeLines() {
        if (!STORE_ENABLED) return null;
        String coupon = SuiteStore.getCurrentDailyCoupon();
        if (coupon == null) return null;
        return List.of(
                "",
                "   <#e22c27><bold>Seja VIP no Skill Pit!",
                "<#cbd1d7>Acesse <#F5F528><hover:show_text:'<white>Clique para abrir a <#F5F528><bold>loja</bold>!'>"
                        + "<click:open_url:'https://" + SuiteStore.STORE_URL + "'>" + SuiteStore.STORE_URL + "</click></hover> "
                        + "<#cbd1d7>e use o cupom <#F5F528><bold>" + coupon + "</bold> "
                        + "<#cbd1d7>para <#10fc46>" + SuiteStore.DISCOUNT_PERCENT + "% de desconto<#cbd1d7>!",
                ""
        );
    }

    /** /bounty: consultar/colocar recompensas e caçar os mais procurados por coins. */
    private List<String> bountyLines() {
        if (!BOUNTY_ENABLED) return null;
        return List.of(
                "",
                "   <#e22c27><bold>Caçada por Recompensas!",
                "<#cbd1d7>Use <#fcc850>/bounty <#cbd1d7>para colocar recompensas e caçar os jogadores mais procurados por <#fcc850>coins<#cbd1d7>!",
                ""
        );
    }

    /** Coins por mineração de minérios na arena (quanto mais raro, mais vale). */
    private List<String> miningLines() {
        if (!MINING_ENABLED) return null;
        return List.of(
                "",
                "   <#e22c27><bold>Minere por Coins!",
                "<#cbd1d7>Explore a arena e minere minérios para conquistar <#fcc850>coins <#cbd1d7>— quanto mais raro, mais vale!",
                ""
        );
    }

    /** Colina: dominar o topo sozinho gera coins. Só aparece com Colina ativa. */
    private List<String> colinaLines() {
        if (!COLINA_ENABLED) return null;
        if (plugin.getColinaManager() == null || !plugin.getColinaManager().isActive()) return null;
        return List.of(
                "",
                "   <#e22c27><bold>Domine a Colina!",
                "<#cbd1d7>Fique <#10fc46>sozinho <#cbd1d7>no topo da colina e ganhe <#fcc850>coins <#cbd1d7>enquanto mantiver o controle!",
                ""
        );
    }
}
