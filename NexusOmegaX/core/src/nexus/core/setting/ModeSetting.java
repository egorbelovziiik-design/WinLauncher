package nexus.core.setting;

import nexus.util.json.Json;

/** Dropdown / cycling mode list. */
public final class ModeSetting extends Setting<String> {

    private final String[] modes;

    public ModeSetting(String name, String description, String defaultValue, String... modes) {
        super(name, description, defaultValue);
        this.modes = modes == null ? new String[]{defaultValue} : modes;
    }

    public String[] modes() {
        return modes;
    }

    public void cycle(int direction) {
        int index = index();
        int next = (index + direction) % modes.length;
        if (next < 0) {
            next += modes.length;
        }
        set(modes[next]);
    }

    public int index() {
        String current = get();
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equalsIgnoreCase(current)) {
                return i;
            }
        }
        return 0;
    }

    public boolean is(String mode) {
        return get().equalsIgnoreCase(mode);
    }

    @Override
    protected String validate(String value) {
        for (String mode : modes) {
            if (mode.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return defaultValue();
    }

    @Override
    public String displayValue() {
        return get();
    }

    @Override
    public void fromJson(Json value) {
        if (value instanceof Json.JsonString) {
            setSilently(((Json.JsonString) value).value());
        }
    }

    @Override
    public Json toJson() {
        return Json.of(get());
    }

    @Override
    public String typeName() {
        return "mode";
    }

    @Override
    public boolean parse(String text) {
        String wanted = text.trim();
        for (String mode : modes) {
            if (mode.equalsIgnoreCase(wanted)) {
                set(mode);
                return true;
            }
        }
        try {
            int index = Integer.parseInt(wanted);
            if (index >= 0 && index < modes.length) {
                set(modes[index]);
                return true;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return false;
    }
}
