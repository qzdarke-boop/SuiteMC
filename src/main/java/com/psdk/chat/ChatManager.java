package com.psdk.chat;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatManager {

    private boolean chatEnabled = true;
    private final Set<UUID> sayTogglePlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> saySilentPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, LinkedList<String>> history = new ConcurrentHashMap<>();

    private static final int MAX_HISTORY = 10;

    public boolean isChatEnabled() { return chatEnabled; }
    public void setChatEnabled(boolean enabled) { this.chatEnabled = enabled; }

    public Set<UUID> getSayTogglePlayers() { return sayTogglePlayers; }
    public Set<UUID> getSaySilentPlayers() { return saySilentPlayers; }

    public void addHistory(UUID player, String message) {
        history.computeIfAbsent(player, k -> new LinkedList<>());
        LinkedList<String> list = history.get(player);
        list.addFirst(message);
        while (list.size() > MAX_HISTORY) list.removeLast();
    }

    public LinkedList<String> getPlayerHistory(UUID player) {
        return history.getOrDefault(player, new LinkedList<>());
    }
}
