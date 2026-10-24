package nexus.core.setting;

import nexus.util.json.Json;

/** Free text setting (chat messages, waypoint names, custom prefixes). */
public final class StringSetting extends Setting<String> {

    private final int maxLength;
    private final boolean secret;

    public StringSetting(String name, String description, String defaultValue) {
        this(name, description, defaultValue, 512, false);
    }

    public StringSetting(String name, String description, String defaultValue, int maxLength, boolean secret) {
        super(name, description, defaultValue == null ? "" : defaultValue);
        this.maxLength = maxLength;
        this.secret = secret;
    }

    public int maxLength() {
        return maxLength;
    }

    public boolean isSecret() {
        return secret;
    }

    @Override
    protected String validate(String value) {
        if (value.length() > maxLength) {
            return value.substring(0, maxLength);
        }
        return value;
    }

    @Override
    public String displayValue() {
        return secret ? "******" : get();
    }

    public String rawValue() {
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
        return "string";
    }

    @Override
    public boolean parse(String text) {
        set(text);
        return true;
    }
}
