package dev.axsesclient.common.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class StringSetting extends Setting<String> {
    private final int maxLength;

    public StringSetting(String id, String name, String description, String defaultValue, int maxLength) {
        super(id, name, description, defaultValue == null ? "" : defaultValue);
        this.maxLength = Math.max(1, maxLength);
        this.value = sanitize(defaultValue);
    }

    public int getMaxLength() {
        return maxLength;
    }

    @Override
    public String sanitize(String candidate) {
        if (candidate == null) {
            return getDefaultValue();
        }
        StringBuilder sb = new StringBuilder(Math.min(candidate.length(), maxLength));
        for (int i = 0; i < candidate.length() && sb.length() < maxLength; i++) {
            char c = candidate.charAt(i);
            if (c >= 32 || c == '\n' || c == '\t') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            setValue(element.getAsString());
        } else {
            reset();
        }
    }
}
