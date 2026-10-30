package dev.axsesclient.common.i18n;

import java.util.HashMap;
import java.util.Map;

public final class LocalizationManager {
    private String activeLocale = "en_US";
    private final Map<String, Map<String, String>> dictionaries = new HashMap<String, Map<String, String>>();

    public LocalizationManager() {
        Map<String, String> en = new HashMap<String, String>();
        en.put("gui.title", "AXSESCLIENT GENESIS X");
        en.put("gui.search_placeholder", "Search modules or descriptions...");
        en.put("gui.favorites", "Favorites");
        en.put("gui.hud_editor", "HUD Editor");
        en.put("gui.themes", "Themes");
        en.put("gui.configs", "Configs");
        en.put("gui.reset_all", "Reset Defaults");
        en.put("cat.combat_training", "Combat Training");
        en.put("cat.movement", "Movement");
        en.put("cat.render", "Render");
        en.put("cat.player", "Player");
        en.put("cat.world", "World");
        en.put("cat.utility", "Utility");
        en.put("cat.hud", "HUD");
        en.put("cat.settings", "Settings");
        en.put("menu.singleplayer", "SINGLEPLAYER");
        en.put("menu.multiplayer", "MULTIPLAYER");
        en.put("menu.settings", "SETTINGS");
        en.put("menu.axses_gui", "AXSES GUI");
        en.put("menu.accounts", "ACCOUNTS");
        en.put("menu.quit", "QUIT");
        dictionaries.put("en_US", en);

        Map<String, String> ru = new HashMap<String, String>();
        ru.put("gui.title", "AXSESCLIENT GENESIS X");
        ru.put("gui.search_placeholder", "Поиск по названиям и описаниям...");
        ru.put("gui.favorites", "Избранное");
        ru.put("gui.hud_editor", "Редактор HUD");
        ru.put("gui.themes", "Темы");
        ru.put("gui.configs", "Конфиги");
        ru.put("gui.reset_all", "Сбросить всё");
        ru.put("cat.combat_training", "Тренировка боя");
        ru.put("cat.movement", "Движение");
        ru.put("cat.render", "Визуал");
        ru.put("cat.player", "Игрок");
        ru.put("cat.world", "Мир");
        ru.put("cat.utility", "Утилиты");
        ru.put("cat.hud", "HUD");
        ru.put("cat.settings", "Настройки");
        ru.put("menu.singleplayer", "SINGLEPLAYER");
        ru.put("menu.multiplayer", "MULTIPLAYER");
        ru.put("menu.settings", "SETTINGS");
        ru.put("menu.axses_gui", "AXSES GUI");
        ru.put("menu.accounts", "ACCOUNTS");
        ru.put("menu.quit", "QUIT");
        dictionaries.put("ru_RU", ru);
    }

    public String getActiveLocale() {
        return activeLocale;
    }

    public void setActiveLocale(String locale) {
        if (locale != null && dictionaries.containsKey(locale)) {
            this.activeLocale = locale;
        }
    }

    public void toggleLocale() {
        setActiveLocale("en_US".equals(activeLocale) ? "ru_RU" : "en_US");
    }

    public String tr(String key) {
        Map<String, String> dict = dictionaries.get(activeLocale);
        if (dict != null && dict.containsKey(key)) {
            return dict.get(key);
        }
        Map<String, String> fallback = dictionaries.get("en_US");
        if (fallback != null && fallback.containsKey(key)) {
            return fallback.get(key);
        }
        return key;
    }
}
