package dev.axsesclient.common.settings;

import com.google.gson.JsonElement;
import java.util.Objects;
import java.util.function.Consumer;

public abstract class Setting<T> {
    private final String id;
    private final String name;
    private final String description;
    private final T defaultValue;
    protected T value;
    private Consumer<T> changeListener;

    protected Setting(String id, String name, String description, T defaultValue) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Setting id must not be blank");
        }
        this.id = id.trim();
        this.name = name == null || name.trim().isEmpty() ? this.id : name.trim();
        this.description = description == null ? "" : description.trim();
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.value = defaultValue;
    }

    public final String getId() {
        return id;
    }

    public final String getName() {
        return name;
    }

    public final String getDescription() {
        return description;
    }

    public final T getDefaultValue() {
        return defaultValue;
    }

    public T getValue() {
        return value;
    }

    public final boolean setValue(T candidate) {
        T sanitized = sanitize(candidate);
        if (sanitized == null) {
            return false;
        }
        if (!Objects.equals(this.value, sanitized)) {
            this.value = sanitized;
            if (changeListener != null) {
                changeListener.accept(this.value);
            }
        }
        return true;
    }

    public void setChangeListener(Consumer<T> changeListener) {
        this.changeListener = changeListener;
    }

    public void reset() {
        setValue(defaultValue);
    }

    public abstract T sanitize(T candidate);
    public abstract JsonElement toJson();
    public abstract void fromJson(JsonElement element);
}
