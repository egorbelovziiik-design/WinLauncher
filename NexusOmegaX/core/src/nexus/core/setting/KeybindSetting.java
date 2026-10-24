package nexus.core.setting;

import nexus.api.KeyCodes;
import nexus.util.json.Json;

/** Key binding owned by a module or by a client feature. */
public final class KeybindSetting extends Setting<Integer> {

    public KeybindSetting(String name, String description, int defaultKey) {
        super(name, description, Integer.valueOf(defaultKey));
    }

    public int key() {
        return get().intValue();
    }

    public boolean isBound() {
        return key() != KeyCodes.NONE;
    }

    public String keyName() {
        return KeyCodes.name(key());
    }

    @Override
    public String displayValue() {
        return keyName();
    }

    @Override
    public void fromJson(Json value) {
        if (value instanceof Json.JsonNumber) {
            setSilently(Integer.valueOf(((Json.JsonNumber) value).asInt(key())));
        } else if (value instanceof Json.JsonString) {
            setSilently(Integer.valueOf(KeyCodes.parse(((Json.JsonString) value).value())));
        }
    }

    @Override
    public Json toJson() {
        return Json.of(keyName());
    }

    @Override
    public String typeName() {
        return "keybind";
    }

    @Override
    public boolean parse(String text) {
        String value = text.trim();
        if (value.equalsIgnoreCase("none") || value.equals("-1")) {
            set(Integer.valueOf(KeyCodes.NONE));
            return true;
        }
        int parsed = KeyCodes.parse(value);
        if (parsed == KeyCodes.NONE && !value.equalsIgnoreCase("NONE")) {
            return false;
        }
        set(Integer.valueOf(parsed));
        return true;
    }
}
