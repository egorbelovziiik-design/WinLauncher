package dev.funtime.auctionhelper.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Theme engine: six built-in premium themes plus unlimited user themes.
 * A theme is pure data, so themes can be exported together with the configuration.
 */
public final class ThemeEngine {
    private final Map<String, HelperConfig.ThemeConfig> themes =
            new LinkedHashMap<String, HelperConfig.ThemeConfig>();
    private String activeThemeId = "funtime-dark";
    private HelperConfig customConfigHolder;

    public ThemeEngine() {
        registerBuiltIns();
    }

    private void registerBuiltIns() {
        HelperConfig.ThemeConfig dark = new HelperConfig.ThemeConfig("funtime-dark", "FUNTIME DARK");
        dark.background = 0xFF070910;
        dark.surface = 0xFF11141E;
        dark.elevated = 0xFF1A1E2C;
        dark.primary = 0xFF8B5CFF;
        dark.secondary = 0xFF5274FF;
        dark.accent = 0xFF00DBFF;
        dark.text = 0xFFF7F8FF;
        dark.textMuted = 0xFF8A90A6;
        add(dark);

        HelperConfig.ThemeConfig purple = new HelperConfig.ThemeConfig("funtime-purple", "FUNTIME PURPLE");
        purple.background = 0xFF0B0713;
        purple.surface = 0xFF151022;
        purple.elevated = 0xFF241A3B;
        purple.primary = 0xFFB073FF;
        purple.secondary = 0xFF7C4DFF;
        purple.accent = 0xFFE1B4FF;
        add(purple);

        HelperConfig.ThemeConfig blue = new HelperConfig.ThemeConfig("funtime-blue", "FUNTIME BLUE");
        blue.background = 0xFF050B16;
        blue.surface = 0xFF0C1626;
        blue.elevated = 0xFF14233A;
        blue.primary = 0xFF3E8BFF;
        blue.secondary = 0xFF4FC3F7;
        blue.accent = 0xFF9AE6FF;
        add(blue);

        HelperConfig.ThemeConfig cyber = new HelperConfig.ThemeConfig("funtime-cyber", "FUNTIME CYBER");
        cyber.background = 0xFF050A0E;
        cyber.surface = 0xFF0B1418;
        cyber.elevated = 0xFF122229;
        cyber.primary = 0xFF00DBFF;
        cyber.secondary = 0xFF00FFC2;
        cyber.accent = 0xFFFF4FD8;
        add(cyber);

        HelperConfig.ThemeConfig midnight = new HelperConfig.ThemeConfig("funtime-midnight", "FUNTIME MIDNIGHT");
        midnight.background = 0xFF04060C;
        midnight.surface = 0xFF0B0F18;
        midnight.elevated = 0xFF141A28;
        midnight.primary = 0xFF6C7CFF;
        midnight.secondary = 0xFF3B4B8F;
        midnight.accent = 0xFF8FD3FF;
        add(midnight);

        HelperConfig.ThemeConfig minimal = new HelperConfig.ThemeConfig("funtime-minimal", "FUNTIME MINIMAL");
        minimal.background = 0xFF101216;
        minimal.surface = 0xFF171A20;
        minimal.elevated = 0xFF20242C;
        minimal.primary = 0xFFE8EAF0;
        minimal.secondary = 0xFFBFC4D0;
        minimal.accent = 0xFF8B5CFF;
        minimal.cornerRadius = 4;
        minimal.opacity = 0.98f;
        add(minimal);
    }

    private void add(HelperConfig.ThemeConfig theme) {
        themes.put(theme.id, theme);
    }

    /** Adds user themes from the configuration (they win over built-ins with the same id). */
    public void loadCustomThemes(HelperConfig config) {
        this.customConfigHolder = config;
        if (config == null || config.customThemes == null) {
            return;
        }
        for (int i = 0; i < config.customThemes.size(); i++) {
            HelperConfig.ThemeConfig theme = config.customThemes.get(i);
            if (theme != null && theme.id != null) {
                themes.put(theme.id, theme);
            }
        }
        if (config.activeTheme != null) {
            activeThemeId = config.activeTheme;
        }
    }

    public HelperConfig.ThemeConfig active() {
        HelperConfig.ThemeConfig theme = themes.get(activeThemeId);
        if (theme == null) {
            theme = themes.get("funtime-dark");
        }
        return theme;
    }

    public String getActiveThemeId() {
        return activeThemeId;
    }

    public boolean setActive(String id) {
        if (!themes.containsKey(id)) {
            return false;
        }
        activeThemeId = id;
        if (customConfigHolder != null) {
            customConfigHolder.activeTheme = id;
        }
        return true;
    }

    public List<HelperConfig.ThemeConfig> all() {
        return new ArrayList<HelperConfig.ThemeConfig>(themes.values());
    }

    public List<String> ids() {
        return new ArrayList<String>(themes.keySet());
    }

    /** Creates (or replaces) a user theme. */
    public void saveCustomTheme(HelperConfig.ThemeConfig theme) {
        if (theme == null || theme.id == null || theme.id.isEmpty()) {
            return;
        }
        themes.put(theme.id, theme);
        if (customConfigHolder != null) {
            List<HelperConfig.ThemeConfig> list = customConfigHolder.customThemes;
            if (list == null) {
                list = new ArrayList<HelperConfig.ThemeConfig>();
                customConfigHolder.customThemes = list;
            }
            for (int i = 0; i < list.size(); i++) {
                if (theme.id.equals(list.get(i).id)) {
                    list.set(i, theme);
                    return;
                }
            }
            list.add(theme);
        }
    }
}
