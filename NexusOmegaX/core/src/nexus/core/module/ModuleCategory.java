package nexus.core.module;

/** ClickGUI tabs. Each category carries a glyph id drawn by the GUI renderer. */
public enum ModuleCategory {

    COMBAT("Combat", "sword", 0xFF7C3AED),
    MOVEMENT("Movement", "arrow", 0xFFA855F7),
    PLAYER("Player", "player", 0xFFC084FC),
    RENDER("Render", "eye", 0xFF9333EA),
    WORLD("World", "globe", 0xFF8B5CF6),
    AUTOMATION("Automation", "gear", 0xFF6D28D9),
    EXPLORATION("Exploration", "compass", 0xFFA78BFA),
    MISC("Misc", "sparkles", 0xFFD8B4FE),
    CLIENT("Client", "nexus", 0xFFE9D5FF),
    CONFIGS("Configs", "disk", 0xFFF5F3FF);

    private final String displayName;
    private final String icon;
    private final int accent;

    ModuleCategory(String displayName, String icon, int accent) {
        this.displayName = displayName;
        this.icon = icon;
        this.accent = accent;
    }

    public String displayName() {
        return displayName;
    }

    public String icon() {
        return icon;
    }

    public int accent() {
        return accent;
    }

    public static ModuleCategory byName(String name) {
        for (ModuleCategory category : values()) {
            if (category.name().equalsIgnoreCase(name) || category.displayName.equalsIgnoreCase(name)) {
                return category;
            }
        }
        return MISC;
    }
}
