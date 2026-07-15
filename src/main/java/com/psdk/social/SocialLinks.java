package com.psdk.social;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class SocialLinks {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private SocialLinks() {}

    public static void sendDiscord(Audience sender) {
        sender.sendMessage(MM.deserialize(""));
        sender.sendMessage(MM.deserialize("   <#626CFF><bold>Discord da Suite!</bold>"));
        sender.sendMessage(MM.deserialize(""));
        sender.sendMessage(MM.deserialize("  <#464646> <white><glyph:discord> <#626CFF><hover:show_text:'<white>Clique para entrar no nosso <blue><bold>Discord</bold>!'><click:open_url:'https://discord.gg/suitemc'>discord.gg/suitemc</click></hover>"));
        sender.sendMessage(MM.deserialize(""));
    }

    public static void sendAllSocials(Audience sender) {
        sender.sendMessage(MM.deserialize(""));
        sender.sendMessage(MM.deserialize("  <#F42D2D><bold>Nossas Redes Sociais"));
        sender.sendMessage(MM.deserialize(""));
        sender.sendMessage(MM.deserialize("  Clique nos links abaixo para acessar:"));
        sender.sendMessage(MM.deserialize(""));
        sender.sendMessage(MM.deserialize("  <#464646><white><glyph:discord> <#626CFF><hover:show_text:'<white>Clique aqui para entrar no nosso <blue><bold>Discord</bold>!'><click:open_url:'https://discord.gg/suitemc'>discord.gg/suitemc</click></hover>"));
        sender.sendMessage(MM.deserialize(""));
        sender.sendMessage(MM.deserialize("  <#464646><white><glyph:youtube> <#FF4141><hover:show_text:'<white>Clique para abrir nosso <red><bold>YouTube</bold>!'><click:open_url:'https://www.youtube.com/@qzdarke'>youtube.com/@qzdarke</click></hover>"));
        sender.sendMessage(MM.deserialize(""));
        sender.sendMessage(MM.deserialize("  <#464646><white><glyph:tiktok> <#ff196e><hover:show_text:'<white>Clique para acessar nosso <white><bold>TikTok</bold>!'><click:open_url:'https://www.tiktok.com/@qzdarke'>tiktok.com/@qzdarke</click></hover>"));
        sender.sendMessage(MM.deserialize(""));
        sender.sendMessage(MM.deserialize("  <#F42D2D>Te espero lá! <glyph:heart>"));
        sender.sendMessage(MM.deserialize(""));
    }
}
