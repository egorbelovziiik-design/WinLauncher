package dev.axsesclient.common.modules;

public enum ModuleCategory {
    COMBAT_TRAINING("cat.combat_training", "Combat Training"),
    MOVEMENT("cat.movement", "Movement"),
    RENDER("cat.render", "Render"),
    PLAYER("cat.player", "Player"),
    WORLD("cat.world", "World"),
    UTILITY("cat.utility", "Utility"),
    HUD("cat.hud", "HUD"),
    SETTINGS("cat.settings", "Settings");

    private final String translationKey;
    private final String defaultTitle;

    ModuleCategory(String translationKey, String defaultTitle) {
        this.translationKey = translationKey;
        this.defaultTitle = defaultTitle;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public String getDefaultTitle() {
        return defaultTitle;
    }
}
