package ru.reallyworld.ultimate.core.api;

public interface ReallyModule {
    String id();

    String displayName();

    boolean isEnabled();

    void enable() throws Exception;

    void disable();

    default void reload() throws Exception {
        // Safe hot-reload of YAML configurations without full server reload
    }

    default String healthStatus() {
        return isEnabled() ? "OK" : "DISABLED";
    }
}
