package com.psdk.chat;

import com.psdk.PSDK;
import com.psdk.clan.Clan;
import com.psdk.util.TextUtil;
import com.psdk.vip.util.GGWaveManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatListener implements Listener {

    private final PSDK plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private static final Pattern GG_PATTERN = Pattern.compile("^g+[!.]*$", Pattern.CASE_INSENSITIVE);
    private static final long CHAT_DELAY_MS = 3500;   // delay de 3.5s entre mensagens (OP/bypass pula)
    private static final String REPLY_PERMISSION = "psdk.chat.responder";
    private static final String REPLY_BUTTON_TEXT = "<font:nexo:default>ꐮ</font> ";
    private static final String REPLY_BUTTON_HOVER = "<#cbd1d7>Clique para responder <white>%player%<#cbd1d7>.";
    private final java.util.Map<java.util.UUID, Long> chatCooldown = new java.util.concurrent.ConcurrentHashMap<>();

    public ChatListener(PSDK plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String originalMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

        // Onda de GG (digitar "gg" durante a comemoração de VIP).
        if (plugin.getVipManager() != null) {
            GGWaveManager wave = plugin.getVipManager().getGgWaveManager();
            if (wave != null && wave.isActive() && GG_PATTERN.matcher(originalMessage.trim()).matches()) {
                event.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> player.performCommand("gg"));
                return;
            }
        }

        // FWave (digitar "gg" durante a comemoração de casamento).
        if (plugin.getMarriageManager() != null) {
            GGWaveManager fWave = plugin.getMarriageManager().getFWaveManager();
            if (fWave != null && fWave.isActive() && GG_PATTERN.matcher(originalMessage.trim()).matches()) {
                event.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> plugin.getMarriageManager().handleFWaveGG(player));
                return;
            }
        }

        // Onda de GG de aniversário.
        if (plugin.getBirthdayManager() != null) {
            GGWaveManager birthdayWave = plugin.getBirthdayManager().getGgWaveManager();
            if (birthdayWave != null && birthdayWave.isActive() && GG_PATTERN.matcher(originalMessage.trim()).matches()) {
                event.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> plugin.getBirthdayManager().handleBirthdayGG(player));
                return;
            }
        }

        ChatManager cm = plugin.getChatManager();
        if (cm == null) return; // segurança: sem manager, deixa o chat vanilla

        // Modo /say automático.
        if (cm.getSayTogglePlayers().contains(player.getUniqueId())) {
            event.setCancelled(true);
            if (!player.hasPermission("psdk.say")) {
                player.sendMessage(mm.deserialize("<#FF0000>Você não tem permissão para usar o modo /say!"));
                return;
            }
            String suffix = cm.getSaySilentPlayers().contains(player.getUniqueId()) ? " -s" : "";
            Bukkit.getScheduler().runTask(plugin, () -> player.performCommand("say " + originalMessage + suffix));
            return;
        }

        // Chat travado.
        if (!cm.isChatEnabled() && !player.hasPermission("psdk.chatbypass")) {
            event.setCancelled(true);
            player.sendMessage(mm.deserialize("<#FF0000>O chat está desativado no momento."));
            return;
        }

        // Delay de chat de 3.5s (OP e quem tem psdk.chatdelay.bypass pulam).
        if (!player.hasPermission("psdk.chatdelay.bypass")) {
            long now = System.currentTimeMillis();
            Long last = chatCooldown.get(player.getUniqueId());
            if (last != null && now - last < CHAT_DELAY_MS) {
                event.setCancelled(true);
                double leftS = (CHAT_DELAY_MS - (now - last)) / 1000.0;
                player.sendMessage(mm.deserialize("<#FF0000>Aguarde <bold>"
                        + String.format(java.util.Locale.US, "%.1f", leftS) + "</bold>s para falar de novo!"));
                return;
            }
            chatCooldown.put(player.getUniqueId(), now);
        }

        // Anti-link.
        if (!player.hasPermission("psdk.antilink.bypass") && containsLink(originalMessage)) {
            event.setCancelled(true);
            player.sendMessage(mm.deserialize("<#FF0000>Você não pode enviar links no chat!"));
            return;
        }

        cm.addHistory(player.getUniqueId(), originalMessage);

        // Menções: coleta nomes citados e notifica os alvos (sem deixar uma falha derrubar o chat).
        List<String> mentioned = new ArrayList<>();
        try {
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target.equals(player)) continue;
                if (originalMessage.toLowerCase().contains(target.getName().toLowerCase())) {
                    mentioned.add(target.getName());
                    notifyMention(target);
                }
            }
        } catch (Exception ignored) {}

        // Usa o RENDERER do Paper em vez de cancelar+broadcast: a própria plataforma
        // entrega a mensagem formatada a todos os viewers (jogadores + console),
        // evitando o problema clássico de o Bukkit.broadcast não chegar a ninguém.
        final Component formatted = buildMessage(player, originalMessage, mentioned);
        event.renderer((source, sourceDisplayName, message, viewer) -> addReplyAction(player, formatted, viewer));
    }

    private Component addReplyAction(Player sender, Component formatted, Audience viewer) {
        if (!(viewer instanceof Player player) || player.equals(sender) || !player.hasPermission(REPLY_PERMISSION)) {
            return formatted;
        }

        ClickEvent replyClick = ClickEvent.suggestCommand(sender.getName() + ", ");
        Component hover = mm.deserialize(REPLY_BUTTON_HOVER.replace("%player%", sender.getName()));
        Component button = mm.deserialize(REPLY_BUTTON_TEXT).clickEvent(replyClick).hoverEvent(hover);
        return button.append(formatted).clickEvent(replyClick).hoverEvent(hover);
    }

    /** Monta a linha final: prefixo confiável (LuckPerms) + corpo da mensagem (não-confiável), cada um com fallback. */
    private Component buildMessage(Player player, String rawMessage, List<String> mentioned) {
        String clanTag = "";
        try {
            Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
            if (clan != null) {
                clanTag = com.psdk.util.ClanText.formatClanTag(clan.getColorHex(), clan.getTag()) + "<reset> ";
            }
        } catch (Exception ignored) {}

        String prefixFmt = clanTag + "%luckperms_prefix%%luckperms_suffix%<white>: <reset>";
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try { prefixFmt = PlaceholderAPI.setPlaceholders(player, prefixFmt); } catch (Exception ignored) {}
        }
        prefixFmt = prefixFmt.replace("%player_name%", player.getName()).replace("%player%", player.getName());

        String converted = TextUtil.legacyToMiniMessage(prefixFmt);
        converted = sanitizeMiniMessage(converted);

        Component prefix;
        try {
            prefix = mm.deserialize(converted);
        } catch (Exception e) {
            prefix = Component.text(player.getName() + ": ");
        }

        return prefix.append(buildBody(player, rawMessage, mentioned));
    }

    /**
     * Escapa qualquer {@code <} que claramente NÃO é início de tag MiniMessage.
     * Tags válidas começam com: letra, /, !, ou # (hex color).
     */
    private static String sanitizeMiniMessage(String input) {
        if (input == null || !input.contains("<")) return input;
        StringBuilder sb = new StringBuilder(input.length());
        int len = input.length();
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            if (c == '<') {
                if (i + 1 < len) {
                    char next = input.charAt(i + 1);
                    if (Character.isLetter(next) || next == '/' || next == '!' || next == '#') {
                        int close = input.indexOf('>', i);
                        if (close != -1) {
                            sb.append(input, i, close + 1);
                            i = close;
                        } else {
                            sb.append("\\<");
                        }
                    } else {
                        sb.append("\\<");
                    }
                } else {
                    sb.append("\\<");
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Component buildBody(Player player, String rawMessage, List<String> mentioned) {
        String text = rawMessage;

        // Hex/gradient/rainbow bloqueados para todos sem psdk.chat.hexbypass (inclui OP).
        if (!player.hasPermission("psdk.chat.hexbypass")) {
            text = TextUtil.stripAdvancedColors(text);
        }

        // Jogadores sem permissão de cores não podem injetar outras tags MiniMessage.
        if (!player.hasPermission("psdk.colors")) {
            text = text.replace("\\", "\\\\").replace("<", "\\<");
        }

        // Destaca as menções (tags reais inseridas após o escape).
        for (String name : mentioned) {
            text = text.replaceAll("(?i)" + Pattern.quote(name),
                    Matcher.quoteReplacement("<yellow>" + name + "<reset>"));
        }

        // Quem PODE mandar link (OP / psdk.antilink.bypass): o link vira CLICÁVEL.
        if (player.hasPermission("psdk.antilink.bypass")) {
            text = makeLinksClickable(text);
        }

        try {
            return mm.deserialize(text);
        } catch (Exception e) {
            // Mensagem malformada nunca pode derrubar o chat: cai no texto puro.
            return Component.text(rawMessage);
        }
    }

    private static final Pattern URL_PATTERN = Pattern.compile(
            "((?:https?://)?(?:[\\w-]+\\.)+[a-zA-Z]{2,}(?:/\\S*)?)",
            Pattern.CASE_INSENSITIVE);

    /** Envolve URLs em tags clicáveis (open_url) — só pra quem tem permissão de link. */
    private String makeLinksClickable(String text) {
        Matcher m = URL_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String url = m.group(1);
            String href = url.regionMatches(true, 0, "http", 0, 4) ? url : "https://" + url;
            String repl = "<click:open_url:'" + href + "'><hover:show_text:'<gray>Clique para abrir'>"
                    + "<gradient:#ff9008:#e22c27><underlined>" + url + "</underlined></gradient></hover></click>";
            m.appendReplacement(sb, Matcher.quoteReplacement(repl));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private void notifyMention(Player target) {
        try {
            if (!plugin.getSettingsManager().getSetting(target.getUniqueId(), "mentions")) return;
            target.sendMessage(mm.deserialize("<#F0C039>Você foi mencionado no chat!"));
            if (plugin.getSettingsManager().getSetting(target.getUniqueId(), "mention_sound")) {
                target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            }
        } catch (Exception ignored) {}
    }

    private static boolean containsLink(String message) {
        if (message == null) return false;
        return URL_PATTERN.matcher(message).find();
    }
}
