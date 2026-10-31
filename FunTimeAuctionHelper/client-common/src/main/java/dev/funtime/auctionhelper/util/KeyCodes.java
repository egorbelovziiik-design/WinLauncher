package dev.funtime.auctionhelper.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GLFW key codes plus a small readable-name table.
 *
 * <p>The GUI is opened with the physical key that is labelled <b>Ё</b> (or {@code ~}/{@code `})
 * on a standard keyboard. That key is <b>GLFW_KEY_GRAVE_ACCENT (96)</b>. Minecraft key
 * handling is layout independent for physical key codes, so Russian and English layouts
 * both trigger the same code - we never compare typed characters.</p>
 */
public final class KeyCodes {
    public static final int KEY_GRAVE_ACCENT = 96;
    public static final int KEY_ESCAPE = 256;
    public static final int KEY_ENTER = 257;
    public static final int KEY_TAB = 258;
    public static final int KEY_BACKSPACE = 259;
    public static final int KEY_DELETE = 261;
    public static final int KEY_RIGHT_SHIFT = 344;
    public static final int KEY_LEFT_SHIFT = 340;
    public static final int KEY_LEFT_CONTROL = 341;
    public static final int KEY_RIGHT_CONTROL = 345;
    public static final int KEY_LEFT_ALT = 342;
    public static final int KEY_F1 = 290;
    public static final int KEY_F9 = 298;
    public static final int KEY_F10 = 299;
    public static final int KEY_UP = 265;
    public static final int KEY_DOWN = 264;
    public static final int KEY_LEFT = 263;
    public static final int KEY_RIGHT = 262;
    public static final int KEY_HOME = 268;
    public static final int KEY_END = 269;

    private static final Map<Integer, String> NAMES = new LinkedHashMap<Integer, String>();

    static {
        NAMES.put(KEY_GRAVE_ACCENT, "Ё / ` / ~");
        NAMES.put(KEY_ESCAPE, "ESC");
        NAMES.put(KEY_ENTER, "ENTER");
        NAMES.put(KEY_TAB, "TAB");
        NAMES.put(KEY_BACKSPACE, "BACKSPACE");
        NAMES.put(KEY_DELETE, "DELETE");
        NAMES.put(KEY_RIGHT_SHIFT, "RIGHT SHIFT");
        NAMES.put(KEY_LEFT_SHIFT, "LEFT SHIFT");
        NAMES.put(KEY_LEFT_CONTROL, "LEFT CTRL");
        NAMES.put(KEY_RIGHT_CONTROL, "RIGHT CTRL");
        NAMES.put(KEY_LEFT_ALT, "LEFT ALT");
        NAMES.put(KEY_F1, "F1");
        NAMES.put(KEY_F9, "F9");
        NAMES.put(KEY_F10, "F10");
        NAMES.put(KEY_UP, "UP");
        NAMES.put(KEY_DOWN, "DOWN");
        NAMES.put(KEY_LEFT, "LEFT");
        NAMES.put(KEY_RIGHT, "RIGHT");
        NAMES.put(KEY_HOME, "HOME");
        NAMES.put(KEY_END, "END");
        for (int i = 0; i < 26; i++) {
            NAMES.put(65 + i, String.valueOf((char) ('A' + i)));
        }
        for (int i = 0; i < 10; i++) {
            NAMES.put(48 + i, String.valueOf((char) ('0' + i)));
        }
        for (int i = 2; i <= 12; i++) {
            NAMES.put(289 + i, "F" + i);
        }
    }

    private KeyCodes() {
    }

    public static String name(int keyCode) {
        String name = NAMES.get(keyCode);
        if (name != null) {
            return name;
        }
        return "KEY " + keyCode;
    }

    public static boolean isKnown(int keyCode) {
        return NAMES.containsKey(keyCode);
    }
}
