package com.psdk.thepit.topboard;

public enum TopPeriod {
    WEEKLY("Semanal"),
    MONTHLY("Mensal"),
    GLOBAL("Global");

    private final String display;

    TopPeriod(String display) {
        this.display = display;
    }

    public String getDisplay() { return display; }

    public TopPeriod next() {
        return switch (this) {
            case WEEKLY -> MONTHLY;
            case MONTHLY -> GLOBAL;
            case GLOBAL -> WEEKLY;
        };
    }

    public static TopPeriod fromId(String id) {
        TopPeriod parsed = parseId(id);
        return parsed != null ? parsed : WEEKLY;
    }

    public static TopPeriod parseId(String id) {
        if (id == null) return null;
        return switch (id.toLowerCase()) {
            case "weekly", "semanal" -> WEEKLY;
            case "monthly", "mensal" -> MONTHLY;
            case "global" -> GLOBAL;
            default -> null;
        };
    }

    public String getId() {
        return name().toLowerCase();
    }
}
