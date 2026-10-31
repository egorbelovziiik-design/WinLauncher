package dev.funtime.auctionhelper.util;

/** ARGB colour helpers used by the theme engine and the GUI renderer. */
public final class Colors {
    public static final int TRANSPARENT = 0x00000000;
    public static final int WHITE = 0xFFF7F8FF;
    public static final int SUCCESS = 0xFF39DD9C;
    public static final int WARNING = 0xFFFFC266;
    public static final int ERROR = 0xFFFF5B78;
    public static final int ACCENT = 0xFF00DBFF;

    private Colors() {
    }

    public static int argb(int alpha, int rgb) {
        return ((alpha & 0xFF) << 24) | (rgb & 0x00FFFFFF);
    }

    public static int rgb(int r, int g, int b) {
        return 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
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

    public static int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    public static int withAlphaFactor(int color, float factor) {
        int a = Math.round(alpha(color) * clamp01(factor));
        return withAlpha(color, a);
    }

    public static int multiply(int color, float factor) {
        int r = Math.min(255, Math.round(red(color) * factor));
        int g = Math.min(255, Math.round(green(color) * factor));
        int b = Math.min(255, Math.round(blue(color) * factor));
        return ((color >>> 24) << 24) | (r << 16) | (g << 8) | b;
    }

    public static int blend(int from, int to, float t) {
        float k = clamp01(t);
        int a = Math.round(alpha(from) + (alpha(to) - alpha(from)) * k);
        int r = Math.round(red(from) + (red(to) - red(from)) * k);
        int g = Math.round(green(from) + (green(to) - green(from)) * k);
        int b = Math.round(blue(from) + (blue(to) - blue(from)) * k);
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    /** Parses {@code #RRGGBB} or {@code #AARRGGBB}; returns {@code fallback} on bad input. */
    public static int parse(String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        String value = text.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        } else if (value.startsWith("0x") || value.startsWith("0X")) {
            value = value.substring(2);
        }
        try {
            if (value.length() == 6) {
                return 0xFF000000 | Integer.parseInt(value, 16);
            }
            if (value.length() == 8) {
                return (int) Long.parseLong(value, 16);
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return fallback;
    }

    public static String toHex(int color) {
        String value = Integer.toHexString(color & 0x00FFFFFF).toUpperCase(java.util.Locale.ROOT);
        while (value.length() < 6) {
            value = "0" + value;
        }
        return "#" + value;
    }

    public static float clamp01(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }
}
