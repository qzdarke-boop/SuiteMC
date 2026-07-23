package com.psdk.social;

import com.psdk.util.SkullUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Componente central e reutilizável da <b>Loja da Suite</b>.
 *
 * <p>Fonte ÚNICA de: endereço do site, cupom diário (detectado pelo dia da semana no fuso
 * do servidor), porcentagem de desconto, textura da cabeça, som de clique e formatação.
 * Usado por todos os menus (menu principal de {@code /kits}, Kits VIP e caixas) e pelas
 * mensagens automáticas ({@code AnnouncementManager}) — evitando lógica duplicada.
 *
 * <p>O cupom é calculado dinamicamente em cada chamada ({@link #getCurrentDailyCoupon()}),
 * então muda sozinho à meia-noite, sem reiniciar o servidor.
 */
public final class SuiteStore {

    private SuiteStore() {}

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // ============= CONFIG DA LOJA (EDITE AQUI) =============
    /** Endereço do site/loja (sem protocolo; usado no texto e no clique). */
    public static final String STORE_URL = "loja.suitemc.club";
    /** Desconto do cupom diário, em %. */
    public static final int DISCOUNT_PERCENT = 20;
    /** Fuso horário do servidor — define quando o cupom "vira" à meia-noite. */
    public static final ZoneId TIMEZONE = ZoneId.of("America/Sao_Paulo");
    /** Som (só para quem clica) ao usar a cabeça. Discreto e coerente com os menus. */
    private static final Sound CLICK_SOUND = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
    private static final float CLICK_VOLUME = 0.7f;
    private static final float CLICK_PITCH  = 1.4f;

    /** Textura Base64 da cabeça da Loja da Suite (mesma em todos os menus). */
    private static final String HEAD_TEXTURE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDgwODM5MDY5OWJiMDY5NTNjODZjMWEzNTU3OTJlZTYzNjc4OTgxNjE4ZjgwNDdiNzVjOTNiYTA3YTk4MWEwOCJ9fX0=";

    /** Cupom de cada dia da semana (nomes SEM acento, como cadastrados na loja). */
    private static final Map<DayOfWeek, String> DAILY_COUPONS = new EnumMap<>(DayOfWeek.class);
    static {
        DAILY_COUPONS.put(DayOfWeek.MONDAY,    "SEGUNDA");
        DAILY_COUPONS.put(DayOfWeek.TUESDAY,   "TERCA");
        DAILY_COUPONS.put(DayOfWeek.WEDNESDAY, "QUARTA");
        DAILY_COUPONS.put(DayOfWeek.THURSDAY,  "QUINTA");
        DAILY_COUPONS.put(DayOfWeek.FRIDAY,    "SEXTA");
        DAILY_COUPONS.put(DayOfWeek.SATURDAY,  "SABADO");
        DAILY_COUPONS.put(DayOfWeek.SUNDAY,    "DOMINGO");
    }
    // ======================================================

    /** Onde a cabeça está sendo exibida — permite pequenas diferenças de lore. */
    public enum Context { KITS_MAIN, KITS_VIP, CRATES }

    /** Cupom do dia atual (fuso do servidor). Recalculado a cada chamada. */
    public static String getCurrentDailyCoupon() {
        return DAILY_COUPONS.get(LocalDate.now(TIMEZONE).getDayOfWeek());
    }

    /**
     * Cria a cabeça personalizada da Loja da Suite para o contexto informado. Todas as
     * variações compartilham textura, cupom, site, desconto, cores e som — mudando apenas
     * a ênfase da lore (VIPs no menu VIP, chaves nas caixas, geral no /kits).
     */
    public static ItemStack createHead(Context context) {
        ItemStack head = SkullUtils.createTextured(HEAD_TEXTURE);
        ItemMeta meta = head.getItemMeta();
        if (meta == null) return head;

        meta.displayName(MM.deserialize("<!italic><#fcc850><bold>Loja da Suite"));
        meta.lore(buildLore(context));
        head.setItemMeta(meta);
        return head;
    }

    private static List<Component> buildLore(Context context) {
        String coupon = getCurrentDailyCoupon();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        switch (context) {
            case KITS_VIP -> {
                lore.add(MM.deserialize("<!italic><#848c94>Torne-se <#fcc850>VIP <#848c94>e desbloqueie"));
                lore.add(MM.deserialize("<!italic><#848c94>vantagens na <#fcc850>Loja da Suite<#848c94>!"));
            }
            case CRATES -> {
                lore.add(MM.deserialize("<!italic><#848c94>Adquira <#fcc850>chaves <#848c94>e muito mais"));
                lore.add(MM.deserialize("<!italic><#848c94>na <#fcc850>Loja da Suite<#848c94>!"));
            }
            default -> {
                lore.add(MM.deserialize("<!italic><#848c94>Adquira <#fcc850>VIPs<#848c94>, <#fcc850>chaves <#848c94>e mais"));
                lore.add(MM.deserialize("<!italic><#848c94>na <#fcc850>Loja da Suite<#848c94>!"));
            }
        }
        lore.add(Component.empty());
        lore.add(MM.deserialize("<!italic><#cbd1d7>Cupom de hoje: <#F5F528><bold>" + coupon));
        lore.add(MM.deserialize("<!italic><#cbd1d7>Ganhe <#10fc46>" + DISCOUNT_PERCENT + "% de desconto<#cbd1d7>!"));
        lore.add(Component.empty());
        lore.add(MM.deserialize("<!italic><#10fc46>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ʀᴇᴄᴇʙᴇʀ ᴏ sɪᴛᴇ"));
        return lore;
    }

    /**
     * Envia a mensagem da Loja no chat (site clicável + cupom do dia + desconto) e toca o
     * som SÓ para quem clicou. Segue o padrão visual das outras mensagens do Skill Pit.
     */
    public static void sendStoreMessage(Player player) {
        if (player == null) return;
        String coupon = getCurrentDailyCoupon();
        player.sendMessage(MM.deserialize(""));
        player.sendMessage(MM.deserialize("   <#e22c27><bold>Loja da Suite!"));
        player.sendMessage(MM.deserialize(
                "<#cbd1d7>Acesse <#F5F528><hover:show_text:'<white>Clique para abrir a <#F5F528><bold>loja</bold>!'>"
                        + "<click:open_url:'https://" + STORE_URL + "'>" + STORE_URL + "</click></hover> "
                        + "<#cbd1d7>e use o cupom <#F5F528><bold>" + coupon + "</bold> "
                        + "<#cbd1d7>para <#10fc46>" + DISCOUNT_PERCENT + "% de desconto<#cbd1d7>!"));
        player.sendMessage(MM.deserialize(""));
        player.playSound(player.getLocation(), CLICK_SOUND, CLICK_VOLUME, CLICK_PITCH);
    }
}
