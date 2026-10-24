package nexus.core.setting;

import nexus.util.ColorUtil;
import nexus.util.json.Json;

/** ARGB color with optional rainbow mode. */
public final class ColorSetting extends Setting<Integer> {

    private boolean rainbow;
    private double rainbowSpeed = 0.4;
    private float rainbowSaturation = 0.75f;
    private float rainbowBrightness = 1.0f;

    public ColorSetting(String name, String description, int defaultColor) {
        super(name, description, Integer.valueOf(defaultColor));
    }

    public ColorSetting(String name, String description, int defaultColor, boolean rainbow) {
        this(name, description, defaultColor);
        this.rainbow = rainbow;
    }

    public boolean isRainbow() {
        return rainbow;
    }

    public void setRainbow(boolean value) {
        this.rainbow = value;
    }

    public double rainbowSpeed() {
        return rainbowSpeed;
    }

    public void setRainbowSpeed(double value) {
        this.rainbowSpeed = value;
    }

    public float rainbowSaturation() {
        return rainbowSaturation;
    }

    public float rainbowBrightness() {
        return rainbowBrightness;
    }

    /** Resolved color for the current frame (applies rainbow when enabled). */
    public int effectiveColor(long timeMs) {
        if (rainbow) {
            return ColorUtil.rainbow(timeMs, rainbowSpeed, rainbowSaturation, rainbowBrightness, color() >>> 24);
        }
        return color();
    }

    /** Resolved color that also shifts with screen position (array list style). */
    public int effectiveColor(long timeMs, double positionOffset) {
        if (rainbow) {
            return ColorUtil.rainbow(timeMs + (long) (positionOffset * 900.0), rainbowSpeed, rainbowSaturation, rainbowBrightness, color() >>> 24);
        }
        return color();
    }

    public int color() {
        return get().intValue();
    }

    public void setColor(int color) {
        set(Integer.valueOf(color));
    }

    public int alpha() {
        return ColorUtil.alpha(color());
    }

    public void setAlpha(int alpha) {
        setColor(ColorUtil.withAlpha(color(), alpha));
    }

    public int red() {
        return ColorUtil.red(color());
    }

    public int green() {
        return ColorUtil.green(color());
    }

    public int blue() {
        return ColorUtil.blue(color());
    }

    @Override
    public String displayValue() {
        return ColorUtil.toHex(color()) + (rainbow ? " (RAINBOW)" : "");
    }

    @Override
    public void fromJson(Json value) {
        if (value instanceof Json.JsonNumber) {
            setSilently(Integer.valueOf(((Json.JsonNumber) value).asInt(color())));
        } else if (value instanceof Json.JsonString) {
            String text = ((Json.JsonString) value).value();
            if (text.equalsIgnoreCase("rainbow")) {
                rainbow = true;
            } else {
                setSilently(Integer.valueOf(ColorUtil.parseHex(text, color())));
            }
        } else if (value instanceof Json.JsonObject) {
            Json.JsonObject object = (Json.JsonObject) value;
            setSilently(Integer.valueOf(ColorUtil.parseHex(object.string("hex", ColorUtil.toHex(color())), color())));
            rainbow = object.bool("rainbow", rainbow);
            rainbowSpeed = object.double_("speed", rainbowSpeed);
            rainbowSaturation = (float) object.double_("saturation", rainbowSaturation);
            rainbowBrightness = (float) object.double_("brightness", rainbowBrightness);
        }
    }

    @Override
    public Json toJson() {
        Json.JsonObject object = Json.object();
        object.set("hex", ColorUtil.toHex(color()));
        object.set("rainbow", rainbow);
        object.set("speed", rainbowSpeed);
        object.set("saturation", rainbowSaturation);
        object.set("brightness", rainbowBrightness);
        return object;
    }

    @Override
    public String typeName() {
        return "color";
    }

    @Override
    public boolean parse(String text) {
        String value = text.trim();
        if (value.equalsIgnoreCase("rainbow")) {
            rainbow = !rainbow;
            return true;
        }
        if (value.startsWith("#") || value.matches("[0-9A-Fa-f]{6,8}")) {
            setColor(ColorUtil.parseHex(value, color()));
            return true;
        }
        String[] parts = value.split("[ ,]+");
        if (parts.length >= 3) {
            try {
                int r = Integer.parseInt(parts[0]);
                int g = Integer.parseInt(parts[1]);
                int b = Integer.parseInt(parts[2]);
                int a = parts.length > 3 ? Integer.parseInt(parts[3]) : alpha();
                setColor(ColorUtil.argb(a, r, g, b));
                return true;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }
}
