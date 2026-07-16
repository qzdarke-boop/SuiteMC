package com.psdk.chat;

import com.psdk.PSDK;
import com.psdk.util.TextUtil;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.UUID;

/**
 * Ponte do /speak (proxy Velocity &lt;-&gt; backend).
 *
 * <p>O /speak vive na proxy (plugin VelocityCore) e funciona como uma versao
 * GLOBAL do /say: a mensagem aparece para todos os jogadores de todos os
 * servidores da rede. Como a aparencia (tag/cargo, prefixo, sufixo, nome,
 * cores, gradientes e glyphs/texturas do Nexo) depende de PlaceholderAPI,
 * LuckPerms e Nexo — que so existem no backend — quem MONTA a mensagem e' este
 * backend, exatamente como o {@link SayCommand}.
 *
 * <p>Fluxo:
 * <ol>
 *   <li>A proxy envia "SPEAK" + uuid + mensagem pelo canal {@code rede:eventos}.</li>
 *   <li>Aqui montamos o componente igual ao /say (PAPI + LuckPerms + Nexo +
 *       MiniMessage), com o mesmo espacamento (linhas vazias antes/depois).</li>
 *   <li>Serializamos em JSON e devolvemos "SPEAK_BROADCAST" + json.</li>
 *   <li>A proxy distribui o componente para todos os servidores e toca o som.</li>
 * </ol>
 *
 * <p>Totalmente aditivo: nao altera nada do comportamento existente (o /say
 * local continua igual).
 */
public class SpeakBridge implements PluginMessageListener {

    /** Mesmo canal usado pela proxy (VelocityCore.REDE_CHANNEL). */
    public static final String CHANNEL = "rede:eventos";

    private final PSDK plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public SpeakBridge(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player carrier, byte[] message) {
        if (!CHANNEL.equals(channel)) {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String action = in.readUTF();
        if (!"SPEAK".equals(action)) {
            return;
        }

        UUID uuid;
        String text;
        try {
            uuid = UUID.fromString(in.readUTF());
            text = in.readUTF();
        } catch (Exception ex) {
            return;
        }

        // Prefere o autor real; se nao achar, usa o jogador por onde a mensagem chegou.
        Player sender = Bukkit.getPlayer(uuid);
        if (sender == null) {
            sender = carrier;
        }
        if (sender == null) {
            return;
        }

        Component component = buildSpeakComponent(sender, text);

        String json = GsonComponentSerializer.gson().serialize(component);
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("SPEAK_BROADCAST");
        out.writeUTF(json);

        // Devolve para a proxy pela mesma conexao (jogador) que trouxe o pedido.
        carrier.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
    }

    /**
     * Reproduz fielmente a montagem do /say ({@link SayCommand}): prefixo + sufixo
     * do LuckPerms + a mensagem, resolvendo PlaceholderAPI (glyphs do Nexo
     * inclusos), convertendo codigos legados e desserializando com MiniMessage.
     * O espacamento e' o mesmo do /say: duas linhas vazias antes e depois.
     */
    private Component buildSpeakComponent(Player player, String fullMessage) {
        String content = "%luckperms_prefix%%luckperms_suffix%<white>: <reset>" + fullMessage;
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            content = PlaceholderAPI.setPlaceholders(player, content);
            content = content
                    .replace("%player_name%", player.getName())
                    .replace("%player%", player.getName());
        }

        Component line = mm.deserialize(TextUtil.legacyToMiniMessage(content));

        // Mesmo espacamento do /say (blank, blank, linha, blank, blank) em um unico componente.
        return Component.text("\n\n")
                .append(line)
                .append(Component.text("\n\n"));
    }
}
