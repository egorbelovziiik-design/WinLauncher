package nexus.core.setting;

import nexus.util.json.Json;

/** On / off switch. */
public final class BooleanSetting extends Setting<Boolean> {

    public BooleanSetting(String name, String description, boolean defaultValue) {
        super(name, description, Boolean.valueOf(defaultValue));
    }

    public boolean isEnabled() {
        return get().booleanValue();
    }

    public void toggle() {
        set(Boolean.valueOf(!isEnabled()));
    }

    @Override
    public String displayValue() {
        return isEnabled() ? "ON" : "OFF";
    }

    @Override
    public void fromJson(Json value) {
        if (value instanceof Json.JsonBool) {
            setSilently(Boolean.valueOf(((Json.JsonBool) value).value()));
        } else if (value instanceof Json.JsonNumber) {
            setSilently(Boolean.valueOf(((Json.JsonNumber) value).asInt(0) != 0));
        } else if (value instanceof Json.JsonString) {
            setSilently(Boolean.valueOf(Boolean.parseBoolean(((Json.JsonString) value).value())));
        }
    }

    @Override
    public Json toJson() {
        return Json.of(isEnabled());
    }

    @Override
    public String typeName() {
        return "boolean";
    }

    @Override
    public boolean parse(String text) {
        String value = text.trim().toLowerCase();
        if (value.equals("true") || value.equals("on") || value.equals("1") || value.equals("enable")) {
            set(Boolean.TRUE);
            return true;
        }
        if (value.equals("false") || value.equals("off") || value.equals("0") || value.equals("disable")) {
            set(Boolean.FALSE);
            return true;
        }
        if (value.isEmpty() || value.equals("toggle")) {
            toggle();
            return true;
        }
        return false;
    }
}
