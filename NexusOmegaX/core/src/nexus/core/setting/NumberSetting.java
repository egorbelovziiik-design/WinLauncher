package nexus.core.setting;

import nexus.util.MathUtil;
import nexus.util.json.Json;

/** Numeric slider (integer or decimal). */
public final class NumberSetting extends Setting<Double> {

    private final double min;
    private final double max;
    private final double step;
    private final boolean integer;
    private final String unit;

    public NumberSetting(String name, String description, double defaultValue, double min, double max, double step) {
        this(name, description, defaultValue, min, max, step, false, "");
    }

    public NumberSetting(String name, String description, double defaultValue, double min, double max, double step, boolean integer, String unit) {
        super(name, description, Double.valueOf(defaultValue));
        this.min = min;
        this.max = max;
        this.step = step;
        this.integer = integer;
        this.unit = unit == null ? "" : unit;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double step() {
        return step;
    }

    public boolean isInteger() {
        return integer;
    }

    public String unit() {
        return unit;
    }

    public double value() {
        return get().doubleValue();
    }

    public int intValue() {
        return (int) Math.round(value());
    }

    public float floatValue() {
        return (float) value();
    }

    @Override
    protected Double validate(Double value) {
        double result = MathUtil.clamp(value.doubleValue(), min, max);
        if (step > 0.0) {
            result = MathUtil.roundToStep(result, step);
            result = MathUtil.clamp(result, min, max);
        }
        if (integer) {
            result = Math.round(result);
        }
        return Double.valueOf(MathUtil.round(result, 4));
    }

    /** 0..1 progress used by sliders. */
    public double progress() {
        if (max - min == 0.0) {
            return 0.0;
        }
        return MathUtil.normalize(value(), min, max);
    }

    public void setProgress(double progress) {
        set(Double.valueOf(MathUtil.lerp(min, max, progress)));
    }

    @Override
    public String displayValue() {
        if (integer) {
            return intValue() + unit;
        }
        String text = String.valueOf(MathUtil.round(value(), step < 0.1 ? 2 : 1));
        if (text.endsWith(".0")) {
            text = text.substring(0, text.length() - 2);
        }
        return text + unit;
    }

    @Override
    public void fromJson(Json value) {
        if (value instanceof Json.JsonNumber) {
            setSilently(Double.valueOf(((Json.JsonNumber) value).asDouble(value())));
        } else if (value instanceof Json.JsonString) {
            try {
                setSilently(Double.valueOf(Double.parseDouble(((Json.JsonString) value).value())));
            } catch (NumberFormatException ignored) {
                // keep default on malformed config entries
            }
        }
    }

    @Override
    public Json toJson() {
        return Json.of(value());
    }

    @Override
    public String typeName() {
        return integer ? "int" : "double";
    }

    @Override
    public boolean parse(String text) {
        try {
            set(Double.valueOf(Double.parseDouble(text.trim())));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
