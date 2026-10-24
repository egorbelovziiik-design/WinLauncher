package nexus.api;

/**
 * GLFW key codes (identical for Minecraft 1.16.5 -> 1.21.11).
 */
public final class KeyCodes {

    public static final int NONE = -1;
    public static final int ESCAPE = 256;
    public static final int ENTER = 257;
    public static final int TAB = 258;
    public static final int BACKSPACE = 259;
    public static final int DELETE = 261;
    public static final int RIGHT = 262;
    public static final int LEFT = 263;
    public static final int DOWN = 264;
    public static final int UP = 265;
    public static final int PAGE_UP = 266;
    public static final int PAGE_DOWN = 267;
    public static final int HOME = 268;
    public static final int END = 269;
    public static final int CAPS_LOCK = 280;
    public static final int F1 = 290;
    public static final int F11 = 300;
    public static final int F12 = 301;
    public static final int LEFT_SHIFT = 340;
    public static final int LEFT_CONTROL = 341;
    public static final int LEFT_ALT = 342;
    public static final int RIGHT_SHIFT = 344;
    public static final int RIGHT_CONTROL = 345;
    public static final int RIGHT_ALT = 346;
    public static final int SPACE = 32;
    public static final int APOSTROPHE = 39;
    public static final int COMMA = 44;
    public static final int MINUS = 45;
    public static final int PERIOD = 46;
    public static final int SLASH = 47;
    public static final int KEY_A = 65;
    public static final int KEY_B = 66;
    public static final int KEY_V = 86;
    public static final int KEY_0 = 48;

    /** The ClickGUI bind used by Nexus: RIGHT SHIFT toggles the interface. */
    public static final int GUI_KEY = RIGHT_SHIFT;

    private KeyCodes() {
    }

    public static String name(int key) {
        if (key == NONE) {
            return "NONE";
        }
        if (key >= KEY_0 && key <= KEY_0 + 9) {
            return String.valueOf((char) key);
        }
        if (key >= 65 /* A */ && key <= 90 /* Z */) {
            return String.valueOf((char) key);
        }
        switch (key) {
            case ESCAPE:
                return "ESC";
            case ENTER:
                return "ENTER";
            case TAB:
                return "TAB";
            case BACKSPACE:
                return "BACKSPACE";
            case DELETE:
                return "DELETE";
            case RIGHT:
                return "RIGHT";
            case LEFT:
                return "LEFT";
            case DOWN:
                return "DOWN";
            case UP:
                return "UP";
            case PAGE_UP:
                return "PAGE UP";
            case PAGE_DOWN:
                return "PAGE DOWN";
            case HOME:
                return "HOME";
            case END:
                return "END";
            case LEFT_SHIFT:
                return "L.SHIFT";
            case LEFT_CONTROL:
                return "L.CTRL";
            case LEFT_ALT:
                return "L.ALT";
            case RIGHT_SHIFT:
                return "R.SHIFT";
            case RIGHT_CONTROL:
                return "R.CTRL";
            case RIGHT_ALT:
                return "R.ALT";
            case SPACE:
                return "SPACE";
            case MINUS:
                return "MINUS";
            case PERIOD:
                return "PERIOD";
            case COMMA:
                return "COMMA";
            case SLASH:
                return "SLASH";
            case APOSTROPHE:
                return "APOSTROPHE";
            default:
                if (key >= F1 && key <= F12) {
                    return "F" + (key - F1 + 1);
                }
                return "KEY" + key;
        }
    }

    /** Parses a key name coming from configs / {@code .bind} command. */
    public static int parse(String text) {
        if (text == null || text.isEmpty()) {
            return NONE;
        }
        String value = text.trim().toUpperCase();
        if (value.length() == 1) {
            char c = value.charAt(0);
            if (c >= 'A' && c <= 'Z') {
                return c;
            }
            if (c >= '0' && c <= '9') {
                return c;
            }
        }
        if (value.equals("R.SHIFT") || value.equals("RSHIFT") || value.equals("RIGHT SHIFT")) {
            return RIGHT_SHIFT;
        }
        if (value.equals("L.SHIFT") || value.equals("LSHIFT") || value.equals("SHIFT")) {
            return LEFT_SHIFT;
        }
        if (value.equals("R.CTRL") || value.equals("RCONTROL")) {
            return RIGHT_CONTROL;
        }
        if (value.equals("L.CTRL") || value.equals("CONTROL") || value.equals("CTRL")) {
            return LEFT_CONTROL;
        }
        if (value.equals("L.ALT") || value.equals("ALT")) {
            return LEFT_ALT;
        }
        if (value.equals("R.ALT")) {
            return RIGHT_ALT;
        }
        if (value.equals("SPACE")) {
            return SPACE;
        }
        if (value.equals("ESC") || value.equals("ESCAPE")) {
            return ESCAPE;
        }
        if (value.startsWith("F") && value.length() <= 3) {
            try {
                int number = Integer.parseInt(value.substring(1));
                if (number >= 1 && number <= 12) {
                    return F1 + number - 1;
                }
            } catch (NumberFormatException ignored) {
                return NONE;
            }
        }
        return NONE;
    }
}
