package dev.axsesclient.common.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String id, String name, String description, boolean defaultValue) {
        super(id, name, description, Boolean.valueOf(defaultValue));
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(value);
    }

    public void toggle() {
        setValue(Boolean.valueOf(!isEnabled()));
    }

    @Override
    public Boolean sanitize(Boolean candidate) {
        return candidate == null ? getDefaultValue() : candidate;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(isEnabled());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            setValue(Boolean.valueOf(element.getAsBoolean()));
        } else {
            reset();
        }
    }
}
