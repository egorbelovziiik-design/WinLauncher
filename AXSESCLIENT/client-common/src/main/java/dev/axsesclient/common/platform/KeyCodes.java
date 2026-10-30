package dev.axsesclient.common.platform;

public final class KeyCodes {
    public static final int KEY_NONE = -1;
    public static final int KEY_ESCAPE = 256;
    public static final int KEY_ENTER = 257;
    public static final int KEY_TAB = 258;
    public static final int KEY_BACKSPACE = 259;
    public static final int KEY_DELETE = 261;
    public static final int KEY_RIGHT = 262;
    public static final int KEY_LEFT = 263;
    public static final int KEY_DOWN = 264;
    public static final int KEY_UP = 265;
    public static final int KEY_F2 = 291;
    public static final int KEY_F3 = 292;
    public static final int KEY_F5 = 294;
    public static final int KEY_LEFT_SHIFT = 340;
    public static final int KEY_LEFT_CONTROL = 341;
    public static final int KEY_LEFT_ALT = 342;
    public static final int KEY_RIGHT_SHIFT = 344;
    public static final int KEY_RIGHT_CONTROL = 345;
    public static final int KEY_SPACE = 32;

    public static final int KEY_A = 65;
    public static final int KEY_B = 66;
    public static final int KEY_C = 67;
    public static final int KEY_D = 68;
    public static final int KEY_E = 69;
    public static final int KEY_F = 70;
    public static final int KEY_G = 71;
    public static final int KEY_H = 72;
    public static final int KEY_K = 75;
    public static final int KEY_M = 77;
    public static final int KEY_N = 78;
    public static final int KEY_P = 80;
    public static final int KEY_R = 82;
    public static final int KEY_S = 83;
    public static final int KEY_V = 86;
    public static final int KEY_W = 87;
    public static final int KEY_Z = 90;

    private KeyCodes() {
    }

    public static String formatKey(int keyCode) {
        if (keyCode <= 0) {
            return "NONE";
        }
        switch (keyCode) {
            case KEY_RIGHT_SHIFT: return "RSHIFT";
            case KEY_LEFT_SHIFT: return "LSHIFT";
            case KEY_LEFT_CONTROL: return "LCTRL";
            case KEY_RIGHT_CONTROL: return "RCTRL";
            case KEY_LEFT_ALT: return "LALT";
            case KEY_SPACE: return "SPACE";
            case KEY_TAB: return "TAB";
            case KEY_ENTER: return "ENTER";
            case KEY_ESCAPE: return "ESC";
            case KEY_BACKSPACE: return "BKSP";
            case KEY_DELETE: return "DEL";
            case KEY_UP: return "UP";
            case KEY_DOWN: return "DOWN";
            case KEY_LEFT: return "LEFT";
            case KEY_RIGHT: return "RIGHT";
            default:
                if (keyCode >= 33 && keyCode <= 96) {
                    return String.valueOf((char) keyCode);
                }
                if (keyCode >= 290 && keyCode <= 301) {
                    return "F" + (keyCode - 289);
                }
                return "KEY_" + keyCode;
        }
    }
}
