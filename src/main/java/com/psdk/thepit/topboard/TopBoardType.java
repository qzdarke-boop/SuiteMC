package com.psdk.thepit.topboard;

/**
 * Categorias de ranking. KILLS/HORAS/COINS têm rastreio semanal/mensal
 * ({@code player_period_stats}); as demais só possuem o acumulado global.
 */
public enum TopBoardType {
    KILLS("<#ff5555>", "TOP KILLS", "abates", true),
    HOURS("<#55ffff>", "TOP HORAS", "horas", true),
    COINS("<#fcc850>", "TOP COINS", "coins", true),
    DEATHS("<#a4a4a4>", "TOP DEATHS", "mortes", false),
    LEVEL("<#10fc46>", "TOP LEVEL", "nivel", false),
    TOKENS("<#efa600>", "TOP TOKENS", "tokens", false),
    BLOCKS_BROKEN("<#55ffff>", "TOP BLOCOS QUEBRADOS", "blocos quebrados", false),
    BLOCKS_PLACED("<#efa600>", "TOP BLOCOS COLOCADOS", "blocos colocados", false);

    private final String color;
    private final String title;
    private final String unit;
    private final boolean supportsPeriods;

    TopBoardType(String color, String title, String unit, boolean supportsPeriods) {
        this.color = color;
        this.title = title;
        this.unit = unit;
        this.supportsPeriods = supportsPeriods;
    }

    public String getColor() { return color; }
    public String getTitle() { return title; }
    public String getUnit() { return unit; }

    /** true se há colunas semanal/mensal em player_period_stats para esta categoria. */
    public boolean supportsPeriods() { return supportsPeriods; }

    public static TopBoardType fromId(String id) {
        if (id == null) return null;
        return switch (id.toLowerCase()) {
            case "kills", "kill", "abates" -> KILLS;
            case "hours", "horas", "hora" -> HOURS;
            case "coins", "coin", "dinheiro" -> COINS;
            case "deaths", "death", "mortes" -> DEATHS;
            case "level", "nivel" -> LEVEL;
            case "tokens", "token" -> TOKENS;
            case "blocks_broken", "blocosquebrados" -> BLOCKS_BROKEN;
            case "blocks_placed", "blocoscolocados" -> BLOCKS_PLACED;
            default -> null;
        };
    }

    public String getId() {
        return name().toLowerCase();
    }
}
