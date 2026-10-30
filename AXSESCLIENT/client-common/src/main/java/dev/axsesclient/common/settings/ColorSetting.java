package dev.axsesclient.common.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;

public final class ColorSetting extends Setting<Integer> {
    public ColorSetting(String id, String name, String description, int defaultArgb) {
        super(id, name, description, Integer.valueOf(defaultArgb));
    }

    public int getArgb() {
        return value.intValue();
    }

    public int getAlpha() {
        return (getArgb() >>> 24) & 0xFF;
    }

    public int getRed() {
        return (getArgb() >>> 16) & 0xFF;
    }

    public int getGreen() {
        return (getArgb() >>> 8) & 0xFF;
    }

    public int getBlue() {
        return getArgb() & 0xFF;
    }

    public void setRgba(int r, int g, int b, int a) {
        int cr = clampByte(r);
        int cg = clampByte(g);
        int cb = clampByte(b);
        int ca = clampByte(a);
        setValue(Integer.valueOf((ca << 24) | (cr << 16) | (cg << 8) | cb));
    }

    public void setAlpha(int alpha) {
        setRgba(getRed(), getGreen(), getBlue(), alpha);
    }

    public String toHex() {
        return String.format(Locale.ROOT, "#%02X%02X%02X%02X",
                Integer.valueOf(getAlpha()),
                Integer.valueOf(getRed()),
                Integer.valueOf(getGreen()),
                Integer.valueOf(getBlue()));
    }

    public String toRgbHex() {
        return String.format(Locale.ROOT, "#%02X%02X%02X",
                Integer.valueOf(getRed()),
                Integer.valueOf(getGreen()),
                Integer.valueOf(getBlue()));
    }

    public boolean setFromHex(String hex) {
        Integer parsed = parseHexColor(hex, getAlpha());
        if (parsed == null) {
            return false;
        }
        return setValue(parsed);
    }

    public static Integer parseHexColor(String hex, int fallbackAlpha) {
        if (hex == null) {
            return null;
        }
        String clean = hex.trim();
        if (clean.startsWith("#")) {
            clean = clean.substring(1);
        }
        try {
            if (clean.length() == 6) {
                int rgb = Integer.parseInt(clean, 16);
                return Integer.valueOf(((clampByte(fallbackAlpha)) << 24) | (rgb & 0x00FFFFFF));
            } else if (clean.length() == 8) {
                long argb = Long.parseLong(clean, 16);
                return Integer.valueOf((int) (argb & 0xFFFFFFFFL));
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private static int clampByte(int v) {
        return Math.max(0, Math.min(255, v));
    }

    @Override
    public Integer sanitize(Integer candidate) {
        return candidate == null ? getDefaultValue() : candidate;
    }

    @Override
    public JsonElement toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("hex", toHex());
        obj.addProperty("r", Integer.valueOf(getRed()));
        obj.addProperty("g", Integer.valueOf(getGreen()));
        obj.addProperty("b", Integer.valueOf(getBlue()));
        obj.addProperty("a", Integer.valueOf(getAlpha()));
        return obj;
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element == null) {
            reset();
            return;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            if (!setFromHex(element.getAsString())) {
                reset();
            }
            return;
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("hex") && obj.get("hex").isJsonPrimitive()) {
                if (setFromHex(obj.get("hex").getAsString())) {
                    return;
                }
            }
            if (obj.has("r") && obj.has("g") && obj.has("b")) {
                try {
                    int r = obj.get("r").getAsInt();
                    int g = obj.get("g").getAsInt();
                    int b = obj.get("b").getAsInt();
                    int a = obj.has("a") ? obj.get("a").getAsInt() : 255;
                    setRgba(r, g, b, a);
                    return;
                } catch (Exception ignored) {
                }
            }
        }
        reset();
    }
}
