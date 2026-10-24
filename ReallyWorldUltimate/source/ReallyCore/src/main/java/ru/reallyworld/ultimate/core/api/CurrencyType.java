package ru.reallyworld.ultimate.core.api;

public enum CurrencyType {
    COINS("coins", "Монеты", "⛃"),
    RILLIKS("rilliks", "Риллики", "❖"),
    TOKENS("tokens", "Ромашки", "✿");

    private final String column;
    private final String displayName;
    private final String symbol;

    CurrencyType(String column, String displayName, String symbol) {
        this.column = column;
        this.displayName = displayName;
        this.symbol = symbol;
    }

    public String column() {
        return column;
    }

    public String displayName() {
        return displayName;
    }

    public String symbol() {
        return symbol;
    }

    public static CurrencyType fromString(String raw) {
        if (raw == null) return COINS;
        return switch (raw.trim().toUpperCase()) {
            case "RILLIKS", "RILLIK", "POINTS", "DONATE" -> RILLIKS;
            case "TOKENS", "TOKEN", "ROMASHKI" -> TOKENS;
            default -> COINS;
        };
    }
}
