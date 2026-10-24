package ru.reallyworld.ultimate.core.api;

public enum RarityType {
    COMMON("Обычный", "<gray>"),
    RARE("Редкий", "<#55FFFF>"),
    EPIC("Эпический", "<#AA00AA>"),
    LEGENDARY("Легендарный", "<#FBBC00>"),
    MYTHIC("Мифический", "<#FF4500>");

    private final String displayName;
    private final String colorTag;

    RarityType(String displayName, String colorTag) {
        this.displayName = displayName;
        this.colorTag = colorTag;
    }

    public String displayName() {
        return displayName;
    }

    public String colorTag() {
        return colorTag;
    }

    public static RarityType fromString(String raw) {
        if (raw == null) return COMMON;
        for (RarityType r : values()) {
            if (r.name().equalsIgnoreCase(raw.trim())) {
                return r;
            }
        }
        return COMMON;
    }
}
