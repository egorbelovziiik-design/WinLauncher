package nexus.core.setting;

import nexus.util.json.Json;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class of every module setting.
 * <p>
 * Settings are self-describing (name, description, value, visibility) so the
 * ClickGUI, the HUD editor, the config system and the command system can all
 * work with them generically.
 *
 * @param <T> value type
 */
public abstract class Setting<T> {

    /** Called whenever the value changes. */
    public interface ChangeListener<T> {
        void onChanged(Setting<T> setting, T oldValue, T newValue);
    }

    /** Dynamic visibility: hidden settings are skipped by the GUI and the config. */
    public interface Visibility {
        boolean isVisible();
    }

    private final String name;
    private final String description;
    private final T defaultValue;
    private T value;
    private Visibility visibility;
    private final List<ChangeListener<T>> listeners = new ArrayList<ChangeListener<T>>(1);

    protected Setting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description == null ? "" : description;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public T get() {
        return value;
    }

    public boolean set(T newValue) {
        if (newValue == null || newValue.equals(value)) {
            return false;
        }
        T oldValue = value;
        value = validate(newValue);
        fireChanged(oldValue, value);
        return true;
    }

    /** Subclasses clamp / sanitize incoming values here. */
    protected T validate(T value) {
        return value;
    }

    public void setSilently(T newValue) {
        if (newValue == null) {
            return;
        }
        value = validate(newValue);
    }

    public void reset() {
        setSilently(defaultValue);
    }

    public boolean isDefault() {
        return value.equals(defaultValue);
    }

    public Setting<T> visibleWhen(Visibility visibility) {
        this.visibility = visibility;
        return this;
    }

    public boolean isVisible() {
        return visibility == null || visibility.isVisible();
    }

    public Setting<T> onChange(ChangeListener<T> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
        return this;
    }

    protected void fireChanged(T oldValue, T newValue) {
        for (int i = 0; i < listeners.size(); i++) {
            try {
                listeners.get(i).onChanged(this, oldValue, newValue);
            } catch (Throwable throwable) {
                nexus.util.Log.error("Setting listener failed for " + name, throwable);
            }
        }
    }

    /** Value shown next to the setting name in the ClickGUI. */
    public abstract String displayValue();

    public abstract void fromJson(Json value);

    public abstract Json toJson();

    public abstract String typeName();

    /** Parses a value coming from the {@code .settings} command. */
    public abstract boolean parse(String text);

    @Override
    public String toString() {
        return name + "=" + displayValue();
    }
}
