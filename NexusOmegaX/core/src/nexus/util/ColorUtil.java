package nexus.util;

import nexus.util.json.Json;

/**
 * Color helpers for the theme engine, ESP, ClickGUI and HUD.
 * Colors are packed 0xAARRGGBB integers.
 */
public final class ColorUtil {

    private ColorUtil() {
    }

    public static int argb(int a, int r, int g, int b) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int rgb(int r, int g, int b) {
        return argb(255, r, g, b);
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public static int alpha(int color) {
        return (color >>> 24) & 0xFF;
    }

    public static int red(int color) {
        return (color >> 16) & 0xFF;
    }

    public static int green(int color) {
        return (color >> 8) & 0xFF;
    }

    public static int blue(int color) {
        return color & 0xFF;
    }

    public static int[] toArray(int color) {
        return new int[]{alpha(color), red(color), green(color), blue(color)};
    }

    public static int fromArray(int[] rgba) {
        if (rgba == null || rgba.length < 3) {
            return 0xFFFFFFFF;
        }
        int a = rgba.length > 3 ? rgba[3] : 255;
        return argb(a, rgba[0], rgba[1], rgba[2]);
    }

    public static int hsbToColor(float hue, float saturation, float brightness, int alpha) {
        int rgb = java.awt.Color.HSBtoRGB(hue, saturation, brightness);
        return withAlpha(rgb, alpha);
    }

    /** Rainbow color cycling over time. */
    public static int rainbow(long timeMs, double speed, float saturation, float brightness, int alpha) {
        double hue = ((timeMs / 1000.0) * speed) % 1.0;
        return hsbToColor((float) hue, saturation, brightness, alpha);
    }

    /** Rainbow that shifts with vertical screen position (array list style). */
    public static int rainbowVertical(double y, double offsetStep, float saturation, float brightness, int alpha) {
        double hue = ((y / 240.0) + offsetStep) % 1.0;
        return hsbToColor((float) hue, saturation, brightness, alpha);
    }

    public static int blend(int first, int second, double t) {
        double f = MathUtil.clamp(t, 0.0, 1.0);
        int a = (int) MathUtil.lerp(alpha(first), alpha(second), f);
        int r = (int) MathUtil.lerp(red(first), red(second), f);
        int g = (int) MathUtil.lerp(green(first), green(second), f);
        int b = (int) MathUtil.lerp(blue(first), blue(second), f);
        return argb(a, r, g, b);
    }

    public static int darken(int color, double amount) {
        double f = 1.0 - MathUtil.clamp(amount, 0.0, 1.0);
        return argb(alpha(color), (int) (red(color) * f), (int) (green(color) * f), (int) (blue(color) * f));
    }

    public static int brighten(int color, double amount) {
        double f = MathUtil.clamp(amount, 0.0, 1.0);
        int r = (int) MathUtil.lerp(red(color), 255, f);
        int g = (int) MathUtil.lerp(green(color), 255, f);
        int b = (int) MathUtil.lerp(blue(color), 255, f);
        return argb(alpha(color), r, g, b);
    }

    /** Invert keeping alpha, used by the "PURE WHITE" theme accents. */
    public static int invert(int color) {
        return argb(alpha(color), 255 - red(color), 255 - green(color), 255 - blue(color));
    }

    public static int fade(int color, double alphaScale) {
        return withAlpha(color, (int) (alpha(color) * MathUtil.clamp(alphaScale, 0.0, 1.0)));
    }

    /** Health based green -> red gradient. */
    public static int healthColor(float health, float maxHealth) {
        double ratio = maxHealth <= 0 ? 0 : MathUtil.clamp(health / maxHealth, 0.0, 1.0);
        int r = (int) (255 * (1.0 - ratio) * 2.0);
        int g = (int) (255 * ratio * 2.0);
        return argb(255, MathUtil.clamp(r, 0, 255), MathUtil.clamp(g, 0, 255), 40);
    }

    public static int parseHex(String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        String value = text.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        try {
            long parsed = Long.parseLong(value, 16);
            if (value.length() <= 6) {
                return withAlpha((int) parsed, 255);
            }
            return (int) parsed;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static String toHex(int color) {
        return String.format("#%08X", color);
    }

    public static String toHexRgb(int color) {
        return String.format("#%06X", color & 0x00FFFFFF);
    }

    public static Json toJson(int color) {
        return Json.of(toHex(color));
    }

    public static int fromJson(Json value, int fallback) {
        if (value instanceof Json.JsonNumber) {
            return ((Json.JsonNumber) value).asInt(fallback);
        }
        if (value instanceof Json.JsonString) {
            return parseHex(((Json.JsonString) value).value(), fallback);
        }
        return fallback;
    }
}
