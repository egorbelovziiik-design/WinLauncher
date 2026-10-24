package nexus.core.module.client;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** Interface language with a built in dictionary. */
public final class LanguageSettingsModule extends Module {

    private final ModeSetting language;
    private final Map<String, String> dictionary = new LinkedHashMap<String, String>();

    public LanguageSettingsModule() {
        super("languagesettings", "Language", "Interface language of the client.",
                ModuleCategory.CLIENT);
        this.language = mode("Language", "Interface language", "English", "English", "Ukrainian",
                "Russian", "German", "Spanish");
        fill();
    }

    private void fill() {
        dictionary.put("enabled", "Enabled");
        dictionary.put("disabled", "Disabled");
        dictionary.put("settings", "Settings");
        dictionary.put("search", "Search");
    }

    /** Translates a key, falling back to the key itself. */
    public String translate(String key) {
        if (language.is("English")) {
            return key;
        }
        String value = dictionary.get(key);
        if (value == null) {
            return key;
        }
        if (language.is("Ukrainian")) {
            return ukrainian(key, value);
        }
        if (language.is("Russian")) {
            return russian(key, value);
        }
        if (language.is("German")) {
            return german(key, value);
        }
        if (language.is("Spanish")) {
            return spanish(key, value);
        }
        return value;
    }

    private String ukrainian(String key, String fallback) {
        if (key.equals("enabled")) {
            return "Увімкнено";
        }
        if (key.equals("disabled")) {
            return "Вимкнено";
        }
        if (key.equals("settings")) {
            return "Налаштування";
        }
        if (key.equals("search")) {
            return "Пошук";
        }
        return fallback;
    }

    private String russian(String key, String fallback) {
        if (key.equals("enabled")) {
            return "Включено";
        }
        if (key.equals("disabled")) {
            return "Выключено";
        }
        if (key.equals("settings")) {
            return "Настройки";
        }
        if (key.equals("search")) {
            return "Поиск";
        }
        return fallback;
    }

    private String german(String key, String fallback) {
        if (key.equals("enabled")) {
            return "Aktiv";
        }
        if (key.equals("disabled")) {
            return "Inaktiv";
        }
        if (key.equals("settings")) {
            return "Einstellungen";
        }
        if (key.equals("search")) {
            return "Suche";
        }
        return fallback;
    }

    private String spanish(String key, String fallback) {
        if (key.equals("enabled")) {
            return "Activado";
        }
        if (key.equals("disabled")) {
            return "Desactivado";
        }
        if (key.equals("settings")) {
            return "Ajustes";
        }
        if (key.equals("search")) {
            return "Buscar";
        }
        return fallback;
    }

    @Override
    public String hudInfo() {
        return language.get();
    }
}
