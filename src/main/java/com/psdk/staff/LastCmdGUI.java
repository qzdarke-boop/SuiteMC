package com.psdk.staff;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GUI paginada dos últimos comandos de um jogador ({@code /staff lastcmd}).
 * Mais recentes primeiro, um papel por comando, com livro de info no rodapé.
 */
public final class LastCmdGUI implements InventoryHolder {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Comandos por página (5 linhas de cima). */
    public static final int PER_PAGE = 45;
    public static final int SLOT_PREV = 45;
    public static final int SLOT_INFO = 49;
    public static final int SLOT_NEXT = 53;

    private final Inventory inventory;
    private final UUID targetId;
    private final String targetName;
    private final int page;
    private final int totalPages;

    private LastCmdGUI(UUID targetId, String targetName, int page, int totalPages, Component title) {
        this.targetId = targetId;
        this.targetName = targetName;
        this.page = page;
        this.totalPages = totalPages;
        this.inventory = Bukkit.createInventory(this, 54, title);
    }

    public UUID getTargetId()  { return targetId; }
    public String getTargetName() { return targetName; }
    public int getPage()       { return page; }
    public int getTotalPages() { return totalPages; }

    @Override
    public Inventory getInventory() { return inventory; }

    /**
     * Constrói a GUI para uma página.
     *
     * @param entries lista completa, já ordenada (mais recentes primeiro)
     * @param page    página (0-based)
     */
    public static LastCmdGUI build(UUID targetId, String targetName,
                                   List<StaffManager.CmdEntry> entries, int page) {
        int total = entries.size();
        int totalPages = Math.max(1, (total + PER_PAGE - 1) / PER_PAGE);
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        Component title = MM.deserialize(
                "<#3fd0c9><bold>✦ Comandos: " + sanitize(targetName) + " ✦");
        LastCmdGUI gui = new LastCmdGUI(targetId, targetName, page, totalPages, title);

        int start = page * PER_PAGE;
        int end = Math.min(start + PER_PAGE, total);
        for (int i = start; i < end; i++) {
            StaffManager.CmdEntry entry = entries.get(i);
            gui.inventory.setItem(i - start, paper(i + 1, entry));
        }

        // Navegação + info
        if (page > 0) {
            gui.inventory.setItem(SLOT_PREV, arrow("<#fcc850>Página anterior",
                    "<gray>Ir para a página " + page + "."));
        }
        if (page < totalPages - 1) {
            gui.inventory.setItem(SLOT_NEXT, arrow("<#fcc850>Próxima página",
                    "<gray>Ir para a página " + (page + 2) + "."));
        }
        gui.inventory.setItem(SLOT_INFO, infoBook(targetName, total, page, totalPages));
        return gui;
    }

    private static ItemStack paper(int numero, StaffManager.CmdEntry entry) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MM.deserialize("<!italic><white>" + sanitize(entry.command())));
            List<Component> lore = new ArrayList<>();
            lore.add(MM.deserialize("<!italic><dark_gray>#" + numero));
            lore.add(Component.empty());
            lore.add(MM.deserialize("<!italic><gray>" + relativeTime(entry.time())));
            lore.add(MM.deserialize("<!italic><dark_gray>" + absoluteTime(entry.time())));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack arrow(String name, String lore) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MM.deserialize("<!italic>" + name));
            meta.lore(List.of(MM.deserialize("<!italic>" + lore)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack infoBook(String name, int total, int page, int totalPages) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MM.deserialize("<!italic><#3fd0c9><bold>" + sanitize(name)));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(MM.deserialize("<!italic><gray>Total de comandos: <white>" + total));
            lore.add(MM.deserialize("<!italic><gray>Página: <white>" + (page + 1) + "<gray>/<white>" + totalPages));
            lore.add(Component.empty());
            lore.add(MM.deserialize("<!italic><dark_gray>Mais recentes primeiro"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String sanitize(String raw) {
        if (raw == null) return "";
        return raw.replace("<", "‹").replace(">", "›");
    }

    private static String relativeTime(long time) {
        long diff = Math.max(0, System.currentTimeMillis() - time);
        long sec = diff / 1000L;
        if (sec < 60)  return "há " + sec + (sec == 1 ? " segundo" : " segundos");
        long min = sec / 60L;
        if (min < 60)  return "há " + min + (min == 1 ? " minuto" : " minutos");
        long hr = min / 60L;
        if (hr < 24)   return "há " + hr + (hr == 1 ? " hora" : " horas");
        long days = hr / 24L;
        return "há " + days + (days == 1 ? " dia" : " dias");
    }

    private static String absoluteTime(long time) {
        return new java.text.SimpleDateFormat("dd/MM HH:mm:ss").format(new java.util.Date(time));
    }
}
