package nexus.core.theme;

import nexus.core.Nexus;
import nexus.util.ColorUtil;
import nexus.util.Log;
import nexus.util.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Theme registry + runtime palette resolution (theme engine). */
public final class ThemeManager {

    public static final String[] BUILT_IN_FONTS = new String[]{"Nexus Sans", "Minecraft", "Arial", "Consolas", "Verdana"};

    private final Nexus nexus;
    private final Map<String, Theme> themes = new LinkedHashMap<String, Theme>();
    private Theme active;

    public ThemeManager(Nexus nexus) {
        this.nexus = nexus;
    }

    public void registerDefaults() {
        register(new Theme("omega_purple", "OMEGA PURPLE",
                0xFF7C3AED, 0xFF9333EA, 0xFFA855F7, 0xFFC084FC,
                0xE608080D, 0xF2101018, 0xF2181824, 0xFFF5F3FF, 0x99C4B5FD));
        register(new Theme("dark_matter", "DARK MATTER",
                0xFF2A2A35, 0xFF343442, 0xFF4B4B5C, 0xFF6E6E85,
                0xEE0A0A0F, 0xF4121219, 0xF41A1A24, 0xFFECECF1, 0x99A0A6B8));
        register(new Theme("cyber_blue", "CYBER BLUE",
                0xFF0EA5E9, 0xFF0284C7, 0xFF38BDF8, 0xFF7DD3FC,
                0xE6060F1A, 0xF40B1522, 0xF4112130, 0xFFE0F2FE, 0x99BAE6FD));
        register(new Theme("blood_red", "BLOOD RED",
                0xFFDC2626, 0xFFEF4444, 0xFFF87171, 0xFFFCA5A5,
                0xEE120608, 0xF41A0B0E, 0xF4261114, 0xFFFEE2E2, 0x99FCA5A5));
        register(new Theme("neon_green", "NEON GREEN",
                0xFF22C55E, 0xFF16A34A, 0xFF4ADE80, 0xFF86EFAC,
                0xE605140B, 0xF40A1E11, 0xF4102A18, 0xFFDCFCE7, 0x99BBF7D0));
        Theme white = new Theme("pure_white", "PURE WHITE",
                0xFF6D28D9, 0xFF8B5CF6, 0xFFA78BFA, 0xFFDDD6FE,
                0xF0FFFFFF, 0xF8F8F8FA, 0xFFFFFFFF, 0xFF1A1A22, 0x99666677);
        register(white);
        Theme custom = new Theme("custom_rgb", "CUSTOM RGB");
        custom.setRainbowAccent(true);
        register(custom);
        active = themes.get("omega_purple");
    }

    public void register(Theme theme) {
        themes.put(theme.id(), theme);
    }

    public List<Theme> themes() {
        return new ArrayList<Theme>(themes.values());
    }

    public List<String> names() {
        List<String> names = new ArrayList<String>();
        for (Theme theme : themes.values()) {
            names.add(theme.name());
        }
        return names;
    }

    public Theme byId(String id) {
        return themes.get(id);
    }

    public Theme byName(String name) {
        if (name == null) {
            return null;
        }
        for (Theme theme : themes.values()) {
            if (theme.name().equalsIgnoreCase(name) || theme.id().equalsIgnoreCase(name)) {
                return theme;
            }
        }
        return null;
    }

    public Theme activeTheme() {
        return active;
    }

    public String activeName() {
        return active == null ? "NONE" : active.name();
    }

    public boolean setActive(String name) {
        Theme theme = byName(name);
        if (theme == null) {
            return false;
        }
        active = theme;
        applyActiveTheme();
        nexus.configManager().markDirty();
        return true;
    }

    public void cycle(int direction) {
        List<Theme> all = themes();
        if (all.isEmpty()) {
            return;
        }
        int index = all.indexOf(active);
        int next = (index + direction) % all.size();
        if (next < 0) {
            next += all.size();
        }
        active = all.get(next);
        applyActiveTheme();
        nexus.notifications().info("Theme", active.name());
        nexus.configManager().markDirty();
    }

    /** Pushes the theme onto the render pipeline (blur, glow, font, radius). */
    public void applyActiveTheme() {
        if (active == null) {
            return;
        }
        nexus.render().setBlurStrength(active.blurStrength());
        nexus.render().setGlowStrength(active.glowStrength());
        nexus.render().setCornerRadius(active.cornerRadius());
        nexus.render().setFont(active.font());
    }

    /** Creates (or reuses) a user theme derived from the active one. */
    public Theme createCustom(String name) {
        String id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        Theme existing = byId(id);
        if (existing != null) {
            return existing;
        }
        Theme created = active.copy(id, name);
        register(created);
        return created;
    }

    public boolean deleteCustom(String id) {
        if (id == null || id.startsWith("omega") || id.startsWith("dark") || id.startsWith("cyber")
                || id.startsWith("blood") || id.startsWith("neon") || id.startsWith("pure") || id.equals("custom_rgb")) {
            return false;
        }
        Theme removed = themes.remove(id);
        if (removed != null && removed == active) {
            active = themes.get("omega_purple");
        }
        return removed != null;
    }

    // ------------------------------------------------------------- live colors

    public int primary(long timeMs) {
        return active == null ? 0xFF7C3AED : active.primary(timeMs);
    }

    public int accent(long timeMs) {
        return active == null ? 0xFFA855F7 : active.accent(timeMs);
    }

    public int panel() {
        return active == null ? 0xF2101018 : active.panel();
    }

    public int panelAlt() {
        return active == null ? 0xF2181824 : active.panelAlt();
    }

    public int text() {
        return active == null ? 0xFFF5F3FF : active.text();
    }

    public int textDim() {
        return active == null ? 0x99C4B5FD : active.textDim();
    }

    public int outline() {
        return active == null ? 0x40C084FC : active.outline();
    }

    public int success() {
        return active == null ? 0xFF4ADE80 : active.success();
    }

    public int warning() {
        return active == null ? 0xFFFBBF24 : active.warning();
    }

    public int error() {
        return active == null ? 0xFFF87171 : active.error();
    }

    public int background() {
        return active == null ? 0xE608080D : active.background();
    }

    public double cornerRadius() {
        return active == null ? 8.0 : active.cornerRadius();
    }

    public double animationSpeed() {
        return active == null ? 1.0 : active.animationSpeed();
    }

    public String font() {
        return active == null ? "Nexus Sans" : active.font();
    }

    public boolean textShadow() {
        return active == null || active.textShadow();
    }

    public boolean neonGlow() {
        return active == null || active.neonGlow();
    }

    public double glowStrength() {
        return active == null ? 1.0 : active.glowStrength();
    }

    public double blurStrength() {
        return active == null ? 1.0 : active.blurStrength();
    }

    public boolean gradient() {
        return active == null || active.gradient();
    }

    // ------------------------------------------------------------------ config

    public Json colorsToJson() {
        return active == null ? Json.object() : active.toJson();
    }

    public void colorsFromJson(Json value) {
        if (active != null) {
            active.fromJson(value);
            applyActiveTheme();
        }
    }

    public Json toJson() {
        Json.JsonObject object = Json.object();
        object.set("active", active == null ? "omega_purple" : active.id());
        Json.JsonObject all = Json.object();
        for (Map.Entry<String, Theme> entry : themes.entrySet()) {
            if (!entry.getKey().equals("omega_purple") && !entry.getKey().equals("custom_rgb")) {
                // only store user modified themes to keep the file small
                if (isBuiltIn(entry.getKey()) && !isModified(entry.getValue())) {
                    continue;
                }
            }
            all.set(entry.getKey(), entry.getValue().toJson());
        }
        object.set("themes", all);
        return object;
    }

    private boolean isBuiltIn(String id) {
        return id.equals("omega_purple") || id.equals("dark_matter") || id.equals("cyber_blue")
                || id.equals("blood_red") || id.equals("neon_green") || id.equals("pure_white")
                || id.equals("custom_rgb");
    }

    private boolean isModified(Theme theme) {
        Theme defaults = new Theme(theme.id(), theme.name());
        return !Json.write(theme.toJson()).equals(Json.write(defaults.toJson()));
    }

    public void fromJson(Json value) {
        if (!(value instanceof Json.JsonObject)) {
            return;
        }
        Json.JsonObject object = (Json.JsonObject) value;
        Json.JsonObject all = object.getObject("themes", Json.object());
        for (Map.Entry<String, Json> entry : all.members().entrySet()) {
            String id = entry.getKey();
            Json.JsonObject data = entry.getValue() instanceof Json.JsonObject
                    ? (Json.JsonObject) entry.getValue() : Json.object();
            Theme theme = themes.get(id);
            if (theme == null) {
                Theme created = new Theme(id, data.string("name", id));
                created.fromJson(data);
                register(created);
            } else {
                theme.fromJson(data);
            }
        }
        String activeId = object.string("active", "omega_purple");
        Theme theme = byId(activeId);
        if (theme != null) {
            active = theme;
        }
        Log.info("Themes loaded: " + themes.size());
    }

    /** Applies a hex color to the active theme (used by {@code .color}). */
    public boolean setColor(String target, String hex) {
        if (active == null) {
            return false;
        }
        int color = ColorUtil.parseHex(hex, Integer.MIN_VALUE);
        if (color == Integer.MIN_VALUE) {
            return false;
        }
        String key = target.toLowerCase(Locale.ROOT);
        Json.JsonObject object = (Json.JsonObject) active.toJson();
        Json.JsonObject colors = object.object("colors");
        if (!colors.has(key)) {
            return false;
        }
        colors.set(key, ColorUtil.toHex(color));
        active.fromJson(object);
        applyActiveTheme();
        nexus.configManager().markDirty();
        return true;
    }

    public String[] availableFonts() {
        return BUILT_IN_FONTS;
    }

    public boolean setFont(String font) {
        if (active == null) {
            return false;
        }
        for (String candidate : BUILT_IN_FONTS) {
            if (candidate.equalsIgnoreCase(font)) {
                active.setFont(candidate);
                applyActiveTheme();
                nexus.configManager().markDirty();
                return true;
            }
        }
        return false;
    }
}
