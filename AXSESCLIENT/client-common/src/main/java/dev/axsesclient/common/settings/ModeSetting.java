package dev.axsesclient.common.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ModeSetting extends Setting<String> {
    private final List<String> modes;

    public ModeSetting(String id, String name, String description, String defaultMode, String... availableModes) {
        super(id, name, description, defaultMode);
        List<String> list = new ArrayList<String>();
        if (availableModes != null) {
            list.addAll(Arrays.asList(availableModes));
        }
        if (!list.contains(defaultMode)) {
            list.add(0, defaultMode);
        }
        this.modes = Collections.unmodifiableList(list);
        this.value = sanitize(defaultMode);
    }

    public List<String> getModes() {
        return modes;
    }

    public void cycleNext() {
        int idx = modes.indexOf(value);
        int next = (idx + 1) % modes.size();
        setValue(modes.get(next));
    }

    public void cyclePrevious() {
        int idx = modes.indexOf(value);
        int prev = (idx - 1 + modes.size()) % modes.size();
        setValue(modes.get(prev));
    }

    @Override
    public String sanitize(String candidate) {
        if (candidate == null) {
            return getDefaultValue();
        }
        for (String mode : modes) {
            if (mode.equalsIgnoreCase(candidate.trim())) {
                return mode;
            }
        }
        return getDefaultValue();
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
