package com.psdk.staff;

import com.psdk.PSDK;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StaffManager {

    private final PSDK plugin;
    private final Map<UUID, Deque<CmdEntry>> commandHistory = new ConcurrentHashMap<>();
    /** Quantos comandos manter por jogador (sessão atual, em memória). */
    private static final int MAX_COMMAND_LOG = 200;

    /** Um comando registrado: instante (epoch millis) + linha digitada. */
    public record CmdEntry(long time, String command) {}

    public StaffManager(PSDK plugin) {
        this.plugin = plugin;
    }

    public void logCommand(Player player, String commandLine) {
        Deque<CmdEntry> deque = commandHistory.computeIfAbsent(player.getUniqueId(), u -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(new CmdEntry(System.currentTimeMillis(), commandLine));
            while (deque.size() > MAX_COMMAND_LOG) deque.removeFirst();
        }
    }

    /** Histórico do jogador na ordem cronológica (mais antigo primeiro). */
    public List<CmdEntry> getCommandLog(UUID uuid) {
        Deque<CmdEntry> deque = commandHistory.get(uuid);
        if (deque == null) return List.of();
        synchronized (deque) {
            return new ArrayList<>(deque);
        }
    }

    /** Histórico com os mais recentes primeiro (para exibição no GUI). */
    public List<CmdEntry> getCommandLogNewestFirst(UUID uuid) {
        List<CmdEntry> list = new ArrayList<>(getCommandLog(uuid));
        Collections.reverse(list);
        return list;
    }
}
