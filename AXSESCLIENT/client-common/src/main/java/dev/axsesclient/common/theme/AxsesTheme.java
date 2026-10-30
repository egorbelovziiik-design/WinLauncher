package dev.axsesclient.common.theme;

import com.google.gson.JsonObject;
import dev.axsesclient.common.settings.ColorSetting;
import java.util.Locale;

public final class AxsesTheme {
    private String id;
    private String name;
    private boolean builtIn;
    private int backgroundColor;
    private int surfaceColor;
    private int elevatedColor;
    private int primaryColor;
    private int secondaryColor;
    private int accentColor;
    private int textColor;
    private int mutedColor;
    private int successColor;
    private int warningColor;
    private int errorColor;
    private float opacity;
    private float animationSpeed;
    private int cornerRadius;
    private float shadowIntensity;

    public AxsesTheme(
            String id,
            String name,
            boolean builtIn,
            int backgroundColor,
            int surfaceColor,
            int elevatedColor,
            int primaryColor,
            int secondaryColor,
            int accentColor,
            int textColor,
            int mutedColor,
            int successColor,
            int warningColor,
            int errorColor,
            float opacity,
            float animationSpeed,
            int cornerRadius,
            float shadowIntensity
    ) {
        this.id = sanitizeId(id);
        this.name = name == null || name.trim().isEmpty() ? "Custom Theme" : name.trim();
        this.builtIn = builtIn;
        this.backgroundColor = ensureOpaque(backgroundColor);
        this.surfaceColor = ensureOpaque(surfaceColor);
        this.elevatedColor = ensureOpaque(elevatedColor);
        this.primaryColor = ensureOpaque(primaryColor);
        this.secondaryColor = ensureOpaque(secondaryColor);
        this.accentColor = ensureOpaque(accentColor);
        this.textColor = ensureOpaque(textColor);
        this.mutedColor = ensureOpaque(mutedColor);
        this.successColor = ensureOpaque(successColor);
        this.warningColor = ensureOpaque(warningColor);
        this.errorColor = ensureOpaque(errorColor);
        this.opacity = clamp(opacity, 0.35f, 1.0f);
        this.animationSpeed = clamp(animationSpeed, 0.25f, 3.0f);
        this.cornerRadius = Math.max(0, Math.min(28, cornerRadius));
        this.shadowIntensity = clamp(shadowIntensity, 0.0f, 1.0f);
    }

    public AxsesTheme copy(String newId, String newName, boolean asBuiltIn) {
        return new AxsesTheme(
                newId, newName, asBuiltIn,
                backgroundColor, surfaceColor, elevatedColor,
                primaryColor, secondaryColor, accentColor,
                textColor, mutedColor, successColor, warningColor, errorColor,
                opacity, animationSpeed, cornerRadius, shadowIntensity
        );
    }

    public static int ensureOpaque(int rgbOrArgb) {
        int a = (rgbOrArgb >>> 24) & 0xFF;
        if (a == 0) {
            return 0xFF000000 | (rgbOrArgb & 0x00FFFFFF);
        }
        return rgbOrArgb;
    }

    public static int withAlpha(int argb, float alphaMultiplier) {
        int baseA = (argb >>> 24) & 0xFF;
        if (baseA == 0) {
            baseA = 255;
        }
        int outA = Math.max(0, Math.min(255, Math.round(baseA * clamp(alphaMultiplier, 0.0f, 1.0f))));
        return (outA << 24) | (argb & 0x00FFFFFF);
    }

    private static float clamp(float v, float min, float max) {
        if (Float.isNaN(v) || Float.isInfinite(v)) {
            return min;
        }
        return Math.max(min, Math.min(max, v));
    }

    public static String sanitizeId(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "custom-theme";
        }
        String clean = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        if (clean.length() > 48) {
            clean = clean.substring(0, 48);
        }
        return clean.isEmpty() ? "custom-theme" : clean;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { if (name != null && !name.trim().isEmpty()) this.name = name.trim(); }
    public boolean isBuiltIn() { return builtIn; }

    public int getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(int c) { this.backgroundColor = ensureOpaque(c); }
    public int getSurfaceColor() { return surfaceColor; }
    public void setSurfaceColor(int c) { this.surfaceColor = ensureOpaque(c); }
    public int getElevatedColor() { return elevatedColor; }
    public void setElevatedColor(int c) { this.elevatedColor = ensureOpaque(c); }
    public int getPrimaryColor() { return primaryColor; }
    public void setPrimaryColor(int c) { this.primaryColor = ensureOpaque(c); }
    public int getSecondaryColor() { return secondaryColor; }
    public void setSecondaryColor(int c) { this.secondaryColor = ensureOpaque(c); }
    public int getAccentColor() { return accentColor; }
    public void setAccentColor(int c) { this.accentColor = ensureOpaque(c); }
    public int getTextColor() { return textColor; }
    public void setTextColor(int c) { this.textColor = ensureOpaque(c); }
    public int getMutedColor() { return mutedColor; }
    public void setMutedColor(int c) { this.mutedColor = ensureOpaque(c); }
    public int getSuccessColor() { return successColor; }
    public int getWarningColor() { return warningColor; }
    public int getErrorColor() { return errorColor; }

    public float getOpacity() { return opacity; }
    public void setOpacity(float opacity) { this.opacity = clamp(opacity, 0.35f, 1.0f); }
    public float getAnimationSpeed() { return animationSpeed; }
    public void setAnimationSpeed(float animationSpeed) { this.animationSpeed = clamp(animationSpeed, 0.25f, 3.0f); }
    public int getCornerRadius() { return cornerRadius; }
    public void setCornerRadius(int cornerRadius) { this.cornerRadius = Math.max(0, Math.min(28, cornerRadius)); }
    public float getShadowIntensity() { return shadowIntensity; }
    public void setShadowIntensity(float shadowIntensity) { this.shadowIntensity = clamp(shadowIntensity, 0.0f, 1.0f); }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("schemaVersion", Integer.valueOf(1));
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("builtIn", Boolean.valueOf(builtIn));
        o.addProperty("backgroundColor", toRgbHex(backgroundColor));
        o.addProperty("surfaceColor", toRgbHex(surfaceColor));
        o.addProperty("elevatedColor", toRgbHex(elevatedColor));
        o.addProperty("primaryColor", toRgbHex(primaryColor));
        o.addProperty("secondaryColor", toRgbHex(secondaryColor));
        o.addProperty("accentColor", toRgbHex(accentColor));
        o.addProperty("textColor", toRgbHex(textColor));
        o.addProperty("mutedColor", toRgbHex(mutedColor));
        o.addProperty("successColor", toRgbHex(successColor));
        o.addProperty("warningColor", toRgbHex(warningColor));
        o.addProperty("errorColor", toRgbHex(errorColor));
        o.addProperty("opacity", Float.valueOf(opacity));
        o.addProperty("animationSpeed", Float.valueOf(animationSpeed));
        o.addProperty("cornerRadius", Integer.valueOf(cornerRadius));
        o.addProperty("shadowIntensity", Float.valueOf(shadowIntensity));
        return o;
    }

    public static AxsesTheme fromJsonValidated(JsonObject o, boolean allowBuiltInFlag) {
        if (o == null || !o.has("id") || !o.has("name")) {
            throw new IllegalArgumentException("Missing required theme fields 'id' or 'name'");
        }
        if (o.has("schemaVersion") && o.get("schemaVersion").getAsInt() != 1) {
            throw new IllegalArgumentException("Unsupported theme schemaVersion");
        }
        String id = sanitizeId(o.get("id").getAsString());
        String name = o.get("name").getAsString();
        if (name == null || name.trim().isEmpty() || name.length() > 64) {
            throw new IllegalArgumentException("Invalid theme name");
        }
        int bg = requireColor(o, "backgroundColor", 0xFF070911);
        int surf = optColor(o, "surfaceColor", 0xFF10131D);
        int elev = optColor(o, "elevatedColor", 0xFF191D2B);
        int pri = requireColor(o, "primaryColor", 0xFF9060FF);
        int sec = requireColor(o, "secondaryColor", 0xFF536FFF);
        int acc = requireColor(o, "accentColor", 0xFF00D9FF);
        int txt = requireColor(o, "textColor", 0xFFF7F8FF);
        int mut = optColor(o, "mutedColor", 0xFF929BB1);
        int suc = optColor(o, "successColor", 0xFF39DFA2);
        int wrn = optColor(o, "warningColor", 0xFFFFC16A);
        int err = optColor(o, "errorColor", 0xFFFF5B79);
        float opacity = o.has("opacity") ? o.get("opacity").getAsFloat() : 0.94f;
        float anim = o.has("animationSpeed") ? o.get("animationSpeed").getAsFloat() : 1.0f;
        int radius = o.has("cornerRadius") ? o.get("cornerRadius").getAsInt() : 10;
        float shadow = o.has("shadowIntensity") ? o.get("shadowIntensity").getAsFloat() : 0.75f;
        boolean builtIn = allowBuiltInFlag && o.has("builtIn") && o.get("builtIn").getAsBoolean();
        return new AxsesTheme(id, name, builtIn, bg, surf, elev, pri, sec, acc, txt, mut, suc, wrn, err, opacity, anim, radius, shadow);
    }

    private static int requireColor(JsonObject o, String key, int fallback) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing color property: " + key);
        }
        Integer parsed = ColorSetting.parseHexColor(o.get(key).getAsString(), 255);
        if (parsed == null) {
            throw new IllegalArgumentException("Malformed hex color for " + key);
        }
        return parsed.intValue();
    }

    private static int optColor(JsonObject o, String key, int fallback) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive()) {
            return fallback;
        }
        Integer parsed = ColorSetting.parseHexColor(o.get(key).getAsString(), 255);
        return parsed == null ? fallback : parsed.intValue();
    }

    public static String toRgbHex(int argb) {
        return String.format(Locale.ROOT, "#%02X%02X%02X",
                Integer.valueOf((argb >>> 16) & 0xFF),
                Integer.valueOf((argb >>> 8) & 0xFF),
                Integer.valueOf(argb & 0xFF));
    }
}
