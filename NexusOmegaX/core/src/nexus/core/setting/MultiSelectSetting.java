package nexus.core.setting;

import nexus.util.json.Json;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Multiple choice list (entity filters, block filters, HUD toggles). */
public final class MultiSelectSetting extends Setting<Set<String>> {

    private final String[] options;

    public MultiSelectSetting(String name, String description, String[] options, String... defaultSelected) {
        super(name, description, new LinkedHashSet<String>(Arrays.asList(defaultSelected)));
        this.options = options == null ? new String[0] : options;
    }

    public String[] options() {
        return options;
    }

    public Set<String> selected() {
        return get();
    }

    public boolean isSelected(String option) {
        return get().contains(option);
    }

    public void toggle(String option) {
        Set<String> next = new LinkedHashSet<String>(get());
        if (!next.remove(option)) {
            next.add(option);
        }
        set(next);
    }

    public List<String> selectedList() {
        return new ArrayList<String>(get());
    }

    @Override
    protected Set<String> validate(Set<String> value) {
        Set<String> filtered = new LinkedHashSet<String>();
        for (String option : options) {
            if (value.contains(option)) {
                filtered.add(option);
            }
        }
        return filtered;
    }

    @Override
    public String displayValue() {
        int size = get().size();
        return size == 0 ? "NONE" : (size + "/" + options.length);
    }

    @Override
    public void fromJson(Json value) {
        if (value instanceof Json.JsonArray) {
            Set<String> next = new LinkedHashSet<String>();
            Json.JsonArray array = (Json.JsonArray) value;
            for (int i = 0; i < array.size(); i++) {
                Json item = array.get(i);
                if (item instanceof Json.JsonString) {
                    next.add(((Json.JsonString) item).value());
                }
            }
            setSilently(next);
        }
    }

    @Override
    public Json toJson() {
        Json.JsonArray array = Json.array();
        for (String item : get()) {
            array.add(item);
        }
        return array;
    }

    @Override
    public String typeName() {
        return "multiselect";
    }

    @Override
    public boolean parse(String text) {
        if (text == null) {
            return false;
        }
        String value = text.trim();
        if (value.equalsIgnoreCase("all")) {
            set(new LinkedHashSet<String>(Arrays.asList(options)));
            return true;
        }
        if (value.equalsIgnoreCase("none") || value.equalsIgnoreCase("clear")) {
            set(new LinkedHashSet<String>());
            return true;
        }
        for (String option : options) {
            if (option.equalsIgnoreCase(value)) {
                toggle(option);
                return true;
            }
        }
        return false;
    }
}
