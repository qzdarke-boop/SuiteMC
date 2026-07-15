package com.psdk.vip;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Toda a configuração do sistema VIP está aqui (hand-coded).
 * Edite este arquivo para alterar ranks, anúncios, mensagens, etc., e recompile.
 */
public final class VipConfig {

    private VipConfig() {}

    /** Substitui {@code %player%} e {@code %player_name%} pelo nick. */
    public static String fillName(String text, String name) {
        if (text == null) return null;
        return text.replace("%player_name%", name).replace("%player%", name);
    }

    // ================================================================
    //  RANKS
    // ================================================================

    public record Rank(String prefix, String suffix, String luckpermsGroup) {}

    public static final Map<String, Rank> VIPS;
    static {
        VIPS = new LinkedHashMap<>();
        VIPS.put("PREMIUM", new Rank("ꐓ", "<gradient:#B616CF:#F384FF:#B616CF>", "premium"));
        VIPS.put("ELITE",   new Rank("ꐢ", "<gradient:#01CF5A:#00FF6E:#01CF5A>", "elite"));
        VIPS.put("ETERNAL", new Rank("ꐡ", "<gradient:#007AFF:#0085FF:#007AFF>", "eternal"));
        VIPS.put("SUITE",   new Rank("♦", "<gradient:#AE0000:#FF5959:#AE0000>", "suite"));
    }

    public static final Map<String, Rank> STAFF;
    static {
        STAFF = new LinkedHashMap<>();
        STAFF.put("DEV",       new Rank("㈨", "<gradient:#AE0000:#FF5959:#AE0000>", "dev"));
        STAFF.put("MODERADOR", new Rank("ꐗ", "<gradient:#4E00EA:#6A06E3:#4E00EA>", "mod"));
        STAFF.put("SUPORTE",   new Rank("ꐠ", "<gradient:#FFB228:#FFD800:#FFB228>", "suporte"));
    }

    public static final Map<String, Rank> ADICIONAIS = new LinkedHashMap<>(Map.of(
        "PROMOTER", new Rank("ꐔ", "<gradient:#B616CF:#F384FF:#B616CF>", "promoter")
    ));

    public static final Map<String, Rank> PARCERIA;
    static {
        PARCERIA = new LinkedHashMap<>();
        PARCERIA.put("PARCEIRO", new Rank("㈤", "<gradient:#DE0000:#690000:#DE0000>", "parceiro"));
        PARCERIA.put("CRIADOR",  new Rank("✄", "<gradient:#007AFF:#0085FF:#007AFF>", "creator"));
    }

    public static Map<String, Rank> getCategory(String path) {
        return switch (path.toLowerCase()) {
            case "vips"       -> VIPS;
            case "staff"      -> STAFF;
            case "adicionais" -> ADICIONAIS;
            case "parceria"   -> PARCERIA;
            default           -> null;
        };
    }

    public static String pathFromCategory(String input) {
        return switch (input.toLowerCase()) {
            case "planos"    -> "vips";
            case "equipe"    -> "staff";
            case "outros"    -> "adicionais";
            case "criadores" -> "parceria";
            default          -> input.toLowerCase();
        };
    }

    // ================================================================
    //  ANÚNCIOS
    //  Estilos: "full" / "title-only" / "chat-only" / "compact"
    // ================================================================

    public record Announcement(
        String style,
        String title, String subtitle,
        int titleFadeIn, int titleStay, int titleFadeOut,
        String sound,
        List<String> text
    ) {}

    public static final Map<String, Announcement> ANNOUNCEMENTS = new LinkedHashMap<>();

    static {
        ANNOUNCEMENTS.put("VIP", new Announcement(
            "full",
            "%suffix%%player%",
            "<white>tornou-se <font:nexo:default>%prefix%</font> !",
            10, 70, 20,
            "entity.lightning_bolt.thunder;1.0;1.0",
            List.of(
                "",
                "  <#cbd1d7>O jogador %suffix%%player%</gradient>",
                " <#cbd1d7>adquiriu o produto <reset><font:nexo:default>%prefix%</font> !",
                "",
                "<gray>▸ <white>Mande <#F5F528>GG <#cbd1d7>para parabenizar o jogador!",
                "",
                "<#cbd1d7>Adquira também em: <#F5F528>loja.suitemc.club",
                "<#F5F528>Cupom <bold>SUITE</bold> com 70% DE DESCONTO!",
                ""
            )
        ));

        ANNOUNCEMENTS.put("STAFF", new Announcement(
            "full",
            "%suffix%%player%",
            "Foi promovido Para <font:nexo:default>%prefix%</font> !",
            10, 70, 20,
            "entity.lightning_bolt.thunder;1.0;1.0",
            List.of(
                "",
                "<#cbd1d7>O jogador <reset>%suffix%%player%</gradient> <#cbd1d7>foi promovido!",
                "",
                "<#cbd1d7>Desejem boa sorte ao novo <reset><font:nexo:default>%prefix%</font>!",
                "<gray>▸ <#cbd1d7>Mande <#F5F528>GG <#cbd1d7>para comemorar!",
                "",
                "<#cbd1d7>Quer ser da Staff? Aplique-se em:",
                " <#F5F528>aplicar.suitemc.club",
                ""
            )
        ));

        ANNOUNCEMENTS.put("ADICIONAIS", new Announcement(
            "full",
            "%suffix%%player%",
            "<white>tornou-se <font:nexo:default>%prefix%</font> !",
            10, 70, 20,
            "entity.lightning_bolt.thunder;1.0;1.0",
            List.of(
                "",
                "<white>O jogador %suffix%%player%</gradient> <white>se tornou <font:nexo:default>%prefix%</font> !",
                "",
                "<gray>▸ <white>Mande <#F5F528>GG <white>para comemorar!",
                "",
                "<white>Quer ser Promotor? Aplique-se em:",
                " <#F5F528>discord.gg/suitemc",
                ""
            )
        ));

        ANNOUNCEMENTS.put("PARCERIA", new Announcement(
            "full",
            "%suffix%%player%",
            "<white>Um novo <reset><font:nexo:default>%prefix%</font> se juntou à rede!",
            10, 70, 20,
            "entity.lightning_bolt.thunder;1.0;1.0",
            List.of(
                "",
                "<white>O jogador <aqua>%player% <white>se tornou <font:nexo:default>%prefix%</font> !",
                "",
                "<gray>▸ <white>Mande <#F5F528>GG <white>para comemorar!",
                "",
                "<white>Quer ser um deles também? Acesse:",
                "<#F5F528>discord.gg/suitemc"
            )
        ));
    }

    // ================================================================
    //  VIPTIME
    // ================================================================

    public static final String VIPTIME_HEADER   = "<gradient:#F8F8F8:#A7A7A7>Seus planos ou cargos ativos:</gradient>";
    public static final String VIPTIME_NO_PLANS = "<red>Nenhum plano ou cargos ativos encontrado.";
    public static final String VIPTIME_FORMAT   = " <gray>• <white><font:nexo:default>%prefix%</font><white> %color%%time%";

    public static final String VIPTIME_COLOR_PERM = "<green>";
    public static final String VIPTIME_COLOR_FAR  = "<yellow>";
    public static final String VIPTIME_COLOR_NEAR = "<red>";

    public static final Map<String, String> VIPTIME_SECTIONS = new LinkedHashMap<>();
    static {
        VIPTIME_SECTIONS.put("staff",
            "<gradient:#FF4B4B:#FF8B2D><shadow:#3B2323:1>C</shadow><shadow:#3E2A23:1>a</shadow></gradient>" +
            "<gradient:#FF8B2D:#FFD031><shadow:#403123:1>r</shadow><shadow:#403926:1>g</shadow></gradient>" +
            "<gradient:#FFD031:#DCFF39><shadow:#404028:1>o</shadow><shadow:#344028:1>s</shadow></gradient>" +
            "<gradient:#DCFF39:#6DFF45><shadow:#274027:1> </shadow></gradient>" +
            "<gradient:#6DFF45:#3BFFC7><shadow:#273A32:1>S</shadow><shadow:#26343D:1>t</shadow></gradient>" +
            "<gradient:#3BFFC7:#58D0FF><shadow:#282E3F:1>a</shadow><shadow:#292740:1>f</shadow></gradient>" +
            "<gradient:#58D0FF:#5489FF><shadow:#332840:1>f</shadow><shadow:#3C2840:1>:</shadow></gradient>");
        VIPTIME_SECTIONS.put("vips",      "<gradient:#00C3AB:#00FFC1:#83F5FF>Planos VIP Ativos:</gradient>");
        VIPTIME_SECTIONS.put("adicionais","<gradient:#38B379:#7FF5BF:#3CD67B:#9BFD6F:#37BA45>Cargos Adicionais:</gradient>");
        VIPTIME_SECTIONS.put("parceria",  "<gradient:#C2CEFF:#A5AFFF:#ABD3FF:#B8A5FF:#A1B3FF:#C2BCFF:#A1B3FF>Criadores e Parceiros</gradient>");
    }

    // ================================================================
    //  GG WAVE
    // ================================================================

    public static final boolean      GG_WAVE_ENABLED    = true;
    public static final int          GG_WAVE_DURATION   = 15;
    public static final String       GG_WAVE_ACTION_BAR = "<#cbd1d7>Aproveite a <b><#F5F528>ONDA</b> <#cbd1d7>de <#F5F528><bold>GG!</bold> <#848c94>(%time%)";
    public static final String       GG_WAVE_FORMAT     = "%luckperms_prefix% %luckperms_suffix% <white>: %gg%<b>GG!</b>";
    public static final String       GG_NO_WAVE         = "<red>Nenhuma comemoração ativa no momento.";
    public static final String       GG_ALREADY_SENT    = "<red>Você já mandou GG nesta comemoração!";
    public static final List<String> GG_COLORS          = List.of(
        "<#F0F094>","<#F0F098>","<#F0F09C>","<#F0F0A0>","<#F0F0A4>","<#F0F0A8>","<#F0F0AC>",
        "<#F0F0B0>","<#F0F0B4>","<#F0F0B8>","<#F0F0BC>","<#F0F0C0>","<#F0F0C4>","<#F0F0C8>",
        "<#F0F0CC>","<#F0F0D0>","<#F0F0DC>","<#F0F0E0>","<#F0F0E4>","<#F0F0E8>","<#F5F505>",
        "<#F5F50A>","<#F5F50F>","<#F5F514>","<#F5F519>","<#F5F51E>","<#F5F532>","<#F5F537>",
        "<#F5F53C>","<#F5F541>","<#F5F546>","<#F5F54B>","<#F5F550>","<#F5F555>","<#F5F55A>"
    );

    // ================================================================
    //  HEAD
    // ================================================================

    public static final String  HEAD_CHAR          = "█";
    public static final String  HEAD_SPACE_AFTER   = "  ";
    public static final boolean HEAD_TOP_MARGIN    = true;
    public static final boolean HEAD_BOTTOM_MARGIN = true;

    // ================================================================
    //  MENSAGENS
    // ================================================================

    public static final String MSG_NO_PERMISSION    = "<red>Você não tem permissão para isso.";
    public static final String MSG_PLAYER_NOT_FOUND = "<red>O jogador %player% não foi encontrado.";
    public static final String MSG_REMOVE_VIP       = "<white>O cargo <font:nexo:default>%prefix%</font> <white>foi removido de <#7B68EE>%player% !";

    // ================================================================
    //  PROXY
    // ================================================================

    public static final boolean PROXY_ENABLED = true;
}
