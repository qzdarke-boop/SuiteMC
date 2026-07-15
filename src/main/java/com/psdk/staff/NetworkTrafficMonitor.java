package com.psdk.staff;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mede o tráfego de rede (bytes recebidos/enviados) injetando um handler leve
 * no pipeline Netty de cada jogador.
 *
 * Tudo com fallback seguro: se não conseguir resolver o canal (mudança de
 * mapeamento/versão), as taxas ficam em 0 e o restante do HUD continua funcionando.
 */
public class NetworkTrafficMonitor implements Listener {

    private static final String HANDLER_NAME = "psdk_traffic";

    private final JavaPlugin plugin;
    private final AtomicLong rx = new AtomicLong();
    private final AtomicLong tx = new AtomicLong();
    private long lastRx, lastTx;
    private long lastTime = System.nanoTime();

    public NetworkTrafficMonitor(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        inject(event.getPlayer());
    }

    /** Injeta o contador nos jogadores já conectados (ex.: /reload). */
    public void injectOnline() {
        for (Player p : plugin.getServer().getOnlinePlayers()) inject(p);
    }

    /** Taxas {rxBytes/s, txBytes/s} desde a última chamada. */
    public long[] snapshotRates() {
        long now = System.nanoTime();
        double secs = Math.max(1e-9, (now - lastTime) / 1_000_000_000.0);
        long curRx = rx.get(), curTx = tx.get();
        long rxRate = (long) ((curRx - lastRx) / secs);
        long txRate = (long) ((curTx - lastTx) / secs);
        lastRx = curRx;
        lastTx = curTx;
        lastTime = now;
        return new long[]{ Math.max(0, rxRate), Math.max(0, txRate) };
    }

    private void inject(Player player) {
        final Channel channel;
        try {
            channel = resolveChannel(player);
        } catch (Throwable t) {
            return;
        }
        if (channel == null) return;
        channel.eventLoop().submit(() -> {
            try {
                if (channel.pipeline().get(HANDLER_NAME) != null) return;
                channel.pipeline().addFirst(HANDLER_NAME, new ChannelDuplexHandler() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        if (msg instanceof ByteBuf buf) rx.addAndGet(buf.readableBytes());
                        super.channelRead(ctx, msg);
                    }
                    @Override
                    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                        if (msg instanceof ByteBuf buf) tx.addAndGet(buf.readableBytes());
                        super.write(ctx, msg, promise);
                    }
                });
            } catch (Throwable ignored) {}
        });
    }

    private Channel resolveChannel(Player player) throws Exception {
        Object handle = player.getClass().getMethod("getHandle").invoke(player);
        // Caminho direto (mapeamento Mojang do Paper): connection -> connection -> channel.
        Object conn = fieldValue(handle, "connection");
        Object netConn = conn == null ? null : fieldValue(conn, "connection");
        Object ch = netConn == null ? null : fieldValue(netConn, "channel");
        if (ch instanceof Channel c) return c;
        // Fallback: varre o grafo de objetos procurando um io.netty.channel.Channel.
        return scanForChannel(handle);
    }

    private static Object fieldValue(Object obj, String name) {
        Class<?> cls = obj.getClass();
        while (cls != null) {
            try {
                Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(obj);
            } catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static Channel scanForChannel(Object root) {
        Deque<Object[]> queue = new ArrayDeque<>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        queue.add(new Object[]{root, 0});
        while (!queue.isEmpty()) {
            Object[] cur = queue.poll();
            Object obj = cur[0];
            int depth = (int) cur[1];
            if (obj == null || depth > 4 || !seen.add(obj)) continue;

            Class<?> cls = obj.getClass();
            while (cls != null && cls != Object.class) {
                for (Field f : cls.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    Object val;
                    try {
                        f.setAccessible(true);
                        val = f.get(obj);
                    } catch (Throwable t) {
                        continue;
                    }
                    if (val instanceof Channel ch) return ch;
                    if (val != null && depth < 4 && shouldRecurse(val.getClass())) {
                        queue.add(new Object[]{val, depth + 1});
                    }
                }
                cls = cls.getSuperclass();
            }
        }
        return null;
    }

    private static boolean shouldRecurse(Class<?> c) {
        String n = c.getName();
        return n.startsWith("net.minecraft.")
                || n.contains("Connection")
                || n.contains("Listener")
                || n.contains("network");
    }
}
