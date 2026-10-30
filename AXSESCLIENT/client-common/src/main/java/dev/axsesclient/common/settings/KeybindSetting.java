package dev.axsesclient.common.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.axsesclient.common.platform.KeyCodes;

public final class KeybindSetting extends Setting<Integer> {
    public KeybindSetting(String id, String name, String description, int defaultKeyCode) {
        super(id, name, description, Integer.valueOf(defaultKeyCode <= 0 ? KeyCodes.KEY_NONE : defaultKeyCode));
    }

    public int getKeyCode() {
        return value.intValue();
    }

    public boolean isBound() {
        return getKeyCode() > 0;
    }

    public void assign(int keyCode) {
        if (keyCode <= 0 || keyCode == KeyCodes.KEY_DELETE || keyCode == KeyCodes.KEY_BACKSPACE) {
            remove();
        } else {
            setValue(Integer.valueOf(keyCode));
        }
    }

    public void remove() {
        setValue(Integer.valueOf(KeyCodes.KEY_NONE));
    }

    public String getFormattedKey() {
        return KeyCodes.formatKey(getKeyCode());
    }

    @Override
    public Integer sanitize(Integer candidate) {
        if (candidate == null || candidate.intValue() <= 0 || candidate.intValue() > 512) {
            return Integer.valueOf(KeyCodes.KEY_NONE);
        }
        return candidate;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            try {
                setValue(Integer.valueOf(element.getAsInt()));
                return;
            } catch (Exception ignored) {
            }
        }
        reset();
    }
}
