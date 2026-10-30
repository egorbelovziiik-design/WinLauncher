package dev.axsesclient.common.settings;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MultiSelectSetting extends Setting<Set<String>> {
    private final List<String> options;

    public MultiSelectSetting(String id, String name, String description, List<String> availableOptions, String... defaultSelected) {
        super(id, name, description, buildDefaultSet(availableOptions, defaultSelected));
        this.options = Collections.unmodifiableList(new ArrayList<String>(availableOptions));
        this.value = sanitize(getDefaultValue());
    }

    private static Set<String> buildDefaultSet(List<String> available, String[] defaults) {
        Set<String> set = new LinkedHashSet<String>();
        if (defaults != null && available != null) {
            for (String d : defaults) {
                for (String opt : available) {
                    if (opt.equalsIgnoreCase(d)) {
                        set.add(opt);
                    }
                }
            }
        }
        return Collections.unmodifiableSet(set);
    }

    public List<String> getOptions() {
        return options;
    }

    public boolean isSelected(String option) {
        return value.contains(option);
    }

    public void toggleOption(String option) {
        Set<String> next = new LinkedHashSet<String>(value);
        if (next.contains(option)) {
            next.remove(option);
        } else if (options.contains(option)) {
            next.add(option);
        }
        setValue(next);
    }

    @Override
    public Set<String> sanitize(Set<String> candidate) {
        if (candidate == null) {
            return getDefaultValue();
        }
        Set<String> valid = new LinkedHashSet<String>();
        for (String item : candidate) {
            if (item == null) {
                continue;
            }
            for (String opt : options) {
                if (opt.equalsIgnoreCase(item.trim())) {
                    valid.add(opt);
                }
            }
        }
        return Collections.unmodifiableSet(valid);
    }

    @Override
    public JsonElement toJson() {
        JsonArray arr = new JsonArray();
        for (String s : value) {
            arr.add(s);
        }
        return arr;
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonArray()) {
            Set<String> parsed = new LinkedHashSet<String>();
            for (JsonElement item : element.getAsJsonArray()) {
                if (item != null && item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                    parsed.add(item.getAsString());
                }
            }
            setValue(parsed);
        } else {
            reset();
        }
    }

    public static List<String> listOf(String... items) {
        return Arrays.asList(items);
    }
}
