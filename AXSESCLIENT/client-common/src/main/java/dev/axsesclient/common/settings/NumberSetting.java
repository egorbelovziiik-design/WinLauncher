package dev.axsesclient.common.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class NumberSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;

    public NumberSetting(String id, String name, String description, double defaultValue, double min, double max, double step) {
        super(id, name, description, Double.valueOf(clampAndSnap(defaultValue, min, max, step)));
        if (max < min) {
            throw new IllegalArgumentException("max must be >= min");
        }
        this.min = min;
        this.max = max;
        this.step = step <= 0.0 ? 0.01 : step;
        this.value = sanitize(Double.valueOf(defaultValue));
    }

    private static double clampAndSnap(double raw, double min, double max, double step) {
        if (Double.isNaN(raw) || Double.isInfinite(raw)) {
            return min;
        }
        double clamped = Math.max(min, Math.min(max, raw));
        if (step > 0.0) {
            double steps = Math.round((clamped - min) / step);
            clamped = min + steps * step;
            clamped = Math.max(min, Math.min(max, clamped));
            // Clean floating-point drift
            clamped = Math.round(clamped * 100000.0) / 100000.0;
        }
        return clamped;
    }

    public double getDouble() {
        return value.doubleValue();
    }

    public float getFloat() {
        return value.floatValue();
    }

    public int getInt() {
        return (int) Math.round(value.doubleValue());
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getStep() {
        return step;
    }

    public float getNormalized() {
        if (max <= min) {
            return 0.0f;
        }
        return (float) ((getDouble() - min) / (max - min));
    }

    public void setFromNormalized(float normalized) {
        float t = Math.max(0.0f, Math.min(1.0f, normalized));
        setValue(Double.valueOf(min + (max - min) * t));
    }

    public String formatValue() {
        if (Math.abs(step - Math.rint(step)) < 1e-6) {
            return String.valueOf(getInt());
        }
        if (step >= 0.1) {
            return String.format(java.util.Locale.ROOT, "%.1f", Double.valueOf(getDouble()));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", Double.valueOf(getDouble()));
    }

    @Override
    public Double sanitize(Double candidate) {
        if (candidate == null || Double.isNaN(candidate.doubleValue()) || Double.isInfinite(candidate.doubleValue())) {
            return getDefaultValue();
        }
        return Double.valueOf(clampAndSnap(candidate.doubleValue(), min, max, step));
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            try {
                setValue(Double.valueOf(element.getAsDouble()));
                return;
            } catch (Exception ignored) {
            }
        }
        reset();
    }
}
