package com.psdk.lootchest;

import org.bukkit.Material;

/**
 * Raridades dos baús aleatórios espalhados pelo mapa.
 *
 * Cada raridade define seu visual (cor + nome do holograma), o ritmo de spawn
 * (quantos baús por ciclo e o intervalo em ticks) e as coordenadas fixas onde
 * pode aparecer (no mundo {@link LootChestManager#WORLD}).
 */
public enum LootRarity {

    NORMAL("BAÚ NORMAL", "<#a4a4a4>", Material.CHEST,
            3, 6000L,   // 3 por vez a cada 5 min
            new int[][]{
                    {716, -56, -569},
                    {801, -58, -550},
                    {696, -58, -436},
                    {865, -58, -488},
                    {912, -58, -468}
            }),

    RARO("BAÚ RARO", "<#71b0ec>", Material.CHEST,
            2, 12000L,  // 2 por vez a cada 10 min
            new int[][]{
                    {684, -56, -505},
                    {755, -59, -510},
                    {890, -58, -458},
                    {700, -47, -57}
            }),

    EPICO("BAÚ ÉPICO", "<#b85afc>", Material.CHEST,
            2, 24000L,  // 2 por vez a cada 20 min
            new int[][]{
                    {825, -39, -541},
                    {675, -40, -502}
            }),

    LENDARIO("BAÚ LENDÁRIO", "<#fcc850>", Material.CHEST,
            1, 48000L,  // 1 por vez a cada 40 min
            new int[][]{
                    {694, -58, -424},
                    {816, -40, -396},
                    {916, -41, -500}
            });

    private final String displayName;
    private final String color;
    private final Material block;
    private final int spawnCount;
    private final long intervalTicks;
    private final int[][] coords;

    LootRarity(String displayName, String color, Material block,
               int spawnCount, long intervalTicks, int[][] coords) {
        this.displayName = displayName;
        this.color = color;
        this.block = block;
        this.spawnCount = spawnCount;
        this.intervalTicks = intervalTicks;
        this.coords = coords;
    }

    /** Nome puro (sem cor) exibido no holograma, ex.: "BAÚ LENDÁRIO". */
    public String getDisplayName() { return displayName; }

    /** Cor em MiniMessage, ex.: "<#fcc850>". */
    public String getColor() { return color; }

    /** Texto MiniMessage do holograma — gradiente da paleta + negrito. */
    public String getHologramText() {
        String grad = switch (this) {
            case NORMAL   -> "<gradient:#cbd1d7:#a4a4a4:#848c94>";
            case RARO     -> "<gradient:#71b0ec:#cbd1d7:#71b0ec>";
            case EPICO    -> "<gradient:#b85afc:#af83ff:#b85afc>";
            case LENDARIO -> "<gradient:#fed44f:#fcc850:#fc9d1a>";
        };
        return grad + "<bold>" + displayName + "</bold></gradient>";
    }

    public Material getBlock() { return block; }
    public int getSpawnCount() { return spawnCount; }
    public long getIntervalTicks() { return intervalTicks; }
    public int[][] getCoords() { return coords; }

    public static LootRarity fromString(String s) {
        if (s == null) return null;
        return switch (s.toLowerCase()) {
            case "normal" -> NORMAL;
            case "raro", "rare" -> RARO;
            case "epico", "épico", "epic" -> EPICO;
            case "lendario", "lendário", "legendary" -> LENDARIO;
            default -> null;
        };
    }
}
