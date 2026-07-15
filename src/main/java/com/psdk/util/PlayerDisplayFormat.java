package com.psdk.util;

import com.psdk.PSDK;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nick de exibição unificado (prefix + nome + suffix do LuckPerms) com cores e
 * gradientes MiniMessage preservados — usado em tops, hologramas e GUIs de clan.
 *
 * Thread-safety: nunca bloqueia a main thread. Para jogador offline sem user no
 * cache do LuckPerms, retorna o nome puro e agenda o carregamento async; a
 * próxima leitura (TTL 5 min) já vem formatada.
 */
public final class PlayerDisplayFormat {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private static final long TTL_MS = 5 * 60_000L;
    private static final int MAX_CACHE = 512;

    private record Cached(Component component, long at) {}

    private static final Map<UUID, Cached> CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> WARMING = ConcurrentHashMap.newKeySet();

    private PlayerDisplayFormat() {}

    /** Nick completo formatado. {@code uuid} pode ser null (usa só o nome, sem cor). */
    public static Component displayName(UUID uuid, String fallbackName) {
        String name = (fallbackName == null || fallbackName.isBlank()) ? "Desconhecido" : fallbackName;
        if (uuid == null) return plain(name);

        Cached cached = CACHE.get(uuid);
        if (cached != null && System.currentTimeMillis() - cached.at() < TTL_MS) {
            return cached.component();
        }

        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            Component c = buildFor(online, online.getName());
            put(uuid, c);
            return c;
        }

        // Offline: só o cache do LP na main thread; loadUser (I/O) apenas async.
        User user = getLuckPermsUser(uuid, !Bukkit.isPrimaryThread());
        if (user == null) {
            warmAsync(uuid, name);
            return cached != null ? cached.component() : plain(name);
        }
        Component c = buildOffline(uuid, name, user);
        put(uuid, c);
        return c;
    }

    public static void invalidate(UUID uuid) {
        if (uuid != null) CACHE.remove(uuid);
    }

    // ── Construção ──────────────────────────────────────────────────────────

    private static Component buildFor(Player player, String name) {
        String prefix = "";
        String suffix = "";
        if (MessageFormatter.hasPAPI()) {
            prefix = PlaceholderAPI.setPlaceholders(player, "%luckperms_prefix%");
            suffix = PlaceholderAPI.setPlaceholders(player, "%luckperms_suffix%");
        } else {
            User user = getLuckPermsUser(player.getUniqueId(), false);
            if (user != null) {
                prefix = orEmpty(user.getCachedData().getMetaData().getPrefix());
                suffix = orEmpty(user.getCachedData().getMetaData().getSuffix());
            }
        }
        return assemble(prefix, suffix, name, player);
    }

    private static Component buildOffline(UUID uuid, String name, User user) {
        String prefix = orEmpty(user.getCachedData().getMetaData().getPrefix());
        String suffix = orEmpty(user.getCachedData().getMetaData().getSuffix());
        OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
        if (MessageFormatter.hasPAPI()) {
            try {
                prefix = PlaceholderAPI.setPlaceholders(target, prefix);
                suffix = PlaceholderAPI.setPlaceholders(target, suffix);
            } catch (Exception ignored) {
                // Algumas expansões não suportam OfflinePlayer — usa o valor cru do LP.
            }
        }
        return assemble(prefix, suffix, name, null);
    }

    private static Component assemble(String prefix, String suffix, String name, Player papiTarget) {
        prefix = sanitize(prefix);
        suffix = sanitize(suffix);
        prefix = replaceNameTokens(prefix, name);
        suffix = replaceNameTokens(suffix, name);

        String raw = prefix + suffix;
        if (!raw.contains(name)) {
            raw = prefix + name + suffix;
        }
        return deserialize(raw);
    }

    /**
     * Pipeline igual ao do chat: legacy (&/§ + &x hex) → MiniMessage. Preserva
     * <gradient>/<#hex> dos prefixos VIP e remove sombra (fica ruim em TextDisplay).
     */
    private static Component deserialize(String raw) {
        String converted = TextUtil.legacyToMiniMessage(raw.replace('§', '&'));
        try {
            return stripShadow(MM.deserialize(converted).decoration(TextDecoration.ITALIC, false));
        } catch (Exception ignored) {
            try {
                return stripShadow(LEGACY.deserialize(raw.replace('§', '&')).decoration(TextDecoration.ITALIC, false));
            } catch (Exception e2) {
                return plain(raw);
            }
        }
    }

    /** Remove sombra/offset do prefixo LP e caracteres soltos que viram "quadrado". */
    private static String sanitize(String raw) {
        if (raw == null) return "";
        String s = raw;
        s = s.replaceAll("(?i)</?shadow(?:[:][^>]*)?>", "");
        s = s.replaceAll("(?i)</?offset(?:[:][^>]*)?>", "");
        s = s.replace("{reset}", "<reset>").replace("&r", "<reset>").replace("§r", "<reset>");
        return s;
    }

    private static String replaceNameTokens(String text, String name) {
        return text.replace("%player_name%", name).replace("%player%", name);
    }

    private static Component stripShadow(Component component) {
        List<Component> kids = component.children().stream()
                .map(PlayerDisplayFormat::stripShadow)
                .toList();
        return component.style(component.style().shadowColor(ShadowColor.none())).children(kids);
    }

    private static Component plain(String name) {
        return Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false);
    }

    private static String orEmpty(String s) {
        return s != null ? s : "";
    }

    // ── LuckPerms / cache ───────────────────────────────────────────────────

    private static User getLuckPermsUser(UUID uuid, boolean allowLoad) {
        try {
            RegisteredServiceProvider<LuckPerms> provider =
                    Bukkit.getServicesManager().getRegistration(LuckPerms.class);
            if (provider == null) return null;
            LuckPerms lp = provider.getProvider();
            User user = lp.getUserManager().getUser(uuid);
            if (user == null && allowLoad) {
                user = lp.getUserManager().loadUser(uuid).join();
            }
            return user;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void warmAsync(UUID uuid, String name) {
        if (!WARMING.add(uuid)) return;
        PSDK plugin = PSDK.getInstance();
        if (plugin == null || !plugin.isEnabled()) {
            WARMING.remove(uuid);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                User user = getLuckPermsUser(uuid, true);
                if (user != null) {
                    put(uuid, buildOffline(uuid, name, user));
                }
            } finally {
                WARMING.remove(uuid);
            }
        });
    }

    private static void put(UUID uuid, Component component) {
        if (CACHE.size() >= MAX_CACHE) {
            long now = System.currentTimeMillis();
            CACHE.entrySet().removeIf(e -> now - e.getValue().at() >= TTL_MS);
        }
        CACHE.put(uuid, new Cached(component, System.currentTimeMillis()));
    }
}
