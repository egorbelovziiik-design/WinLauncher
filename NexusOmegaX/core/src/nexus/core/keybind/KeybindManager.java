package nexus.core.keybind;

import nexus.api.KeyCodes;
import nexus.core.event.KeyEvent;
import nexus.core.module.Module;
import nexus.util.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Central keybind registry.
 * <p>
 * Supported bind modes: {@code Toggle}, {@code Hold} and {@code DoubleClick}.
 * Conflict detection, keybind search, reset, import and export are implemented
 * on top of the registry, as required by the spec.
 */
public final class KeybindManager {

    public enum Mode {
        TOGGLE("Toggle"),
        HOLD("Hold"),
        DOUBLE_CLICK("DoubleClick");

        private final String display;

        Mode(String display) {
            this.display = display;
        }

        public String display() {
            return display;
        }

        public static Mode parse(String text, Mode fallback) {
            if (text == null) {
                return fallback;
            }
            for (Mode mode : values()) {
                if (mode.display.equalsIgnoreCase(text.trim()) || mode.name().equalsIgnoreCase(text.trim())) {
                    return mode;
                }
            }
            return fallback;
        }
    }

    /** A single registered bind. */
    public static class Bind {

        private final String id;
        private final String displayName;
        private final String group;
        private int key;
        private Mode mode;
        private boolean enabled = true;
        private boolean held;
        private long lastPress;

        public Bind(String id, String displayName, String group, int key, Mode mode) {
            this.id = id;
            this.displayName = displayName;
            this.group = group;
            this.key = key;
            this.mode = mode;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        public String group() {
            return group;
        }

        public int key() {
            return key;
        }

        public void setKey(int key) {
            this.key = key;
        }

        public Mode mode() {
            return mode;
        }

        public void setMode(Mode mode) {
            this.mode = mode;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean value) {
            enabled = value;
        }

        public boolean isHeld() {
            return held;
        }

        public String keyName() {
            return KeyCodes.name(key);
        }

        /** Toggle behaviour (also used on the second tap of a double click). */
        public void activate() {
        }

        public void onHoldStart() {
        }

        public void onHoldEnd() {
        }
    }

    /** Maximum delay between two taps of a double click bind. */
    private static final long DOUBLE_CLICK_WINDOW_MS = 280L;

    private final Map<String, Bind> binds = new LinkedHashMap<String, Bind>();

    public Bind registerModule(final Module module) {
        final Bind bind = new Bind("module:" + module.name(), module.displayName(), "Modules", module.keybind(),
                Mode.parse(module.bindMode(), Mode.TOGGLE)) {
            @Override
            public void activate() {
                module.toggle();
            }

            @Override
            public void onHoldStart() {
                module.setEnabled(true);
            }

            @Override
            public void onHoldEnd() {
                module.setEnabled(false);
            }
        };
        binds.put(bind.id(), bind);
        return bind;
    }

    public Bind register(String id, String displayName, String group, int key, Mode mode) {
        Bind bind = new Bind(id, displayName, group, key, mode);
        binds.put(id, bind);
        return bind;
    }

    public Bind register(String id, String displayName, String group, int key, Mode mode, final Runnable action) {
        Bind bind = new Bind(id, displayName, group, key, mode) {
            @Override
            public void activate() {
                action.run();
            }
        };
        binds.put(id, bind);
        return bind;
    }

    public Bind byId(String id) {
        return binds.get(id);
    }

    public Bind forModule(Module module) {
        return binds.get("module:" + module.name());
    }

    public List<Bind> all() {
        return new ArrayList<Bind>(binds.values());
    }

    public List<Bind> bound() {
        List<Bind> result = new ArrayList<Bind>();
        for (Bind bind : binds.values()) {
            if (bind.key() != KeyCodes.NONE) {
                result.add(bind);
            }
        }
        return result;
    }

    public List<Bind> search(String query) {
        List<Bind> result = new ArrayList<Bind>();
        if (query == null || query.trim().isEmpty()) {
            return result;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        for (Bind bind : binds.values()) {
            if (bind.displayName().toLowerCase(Locale.ROOT).contains(needle)
                    || bind.id().toLowerCase(Locale.ROOT).contains(needle)
                    || bind.keyName().toLowerCase(Locale.ROOT).contains(needle)) {
                result.add(bind);
            }
        }
        return result;
    }

    /** All binds sharing {@code key} (conflict detection), excluding {@code ignore}. */
    public List<Bind> conflicts(int key, Bind ignore) {
        List<Bind> result = new ArrayList<Bind>();
        if (key == KeyCodes.NONE) {
            return result;
        }
        for (Bind bind : binds.values()) {
            if (bind != ignore && bind.key() == key) {
                result.add(bind);
            }
        }
        return result;
    }

    public boolean hasConflict(int key, Bind ignore) {
        return !conflicts(key, ignore).isEmpty();
    }

    // ------------------------------------------------------------- dispatch

    public boolean onKey(KeyEvent event) {
        if (event.action == KeyEvent.Action.RELEASE) {
            for (Bind bind : binds.values()) {
                if (bind.key() == event.key && bind.isHeld()) {
                    bind.held = false;
                    bind.onHoldEnd();
                }
            }
            return false;
        }
        boolean consumed = false;
        for (Bind bind : binds.values()) {
            if (!bind.isEnabled() || bind.key() != event.key) {
                continue;
            }
            if (bind.mode() == Mode.HOLD) {
                if (!bind.isHeld()) {
                    bind.held = true;
                    bind.onHoldStart();
                }
                consumed = true;
            } else if (bind.mode() == Mode.DOUBLE_CLICK) {
                long now = System.currentTimeMillis();
                if (now - bind.lastPress <= DOUBLE_CLICK_WINDOW_MS) {
                    bind.lastPress = 0L;
                    bind.activate();
                } else {
                    bind.lastPress = now;
                }
                consumed = true;
            } else if (event.action == KeyEvent.Action.PRESS) {
                bind.activate();
                consumed = true;
            }
        }
        return consumed;
    }

    /** Releases every held bind (used by PANIC and when the GUI takes focus). */
    public void releaseAll() {
        for (Bind bind : binds.values()) {
            if (bind.isHeld()) {
                bind.held = false;
                bind.onHoldEnd();
            }
        }
    }

    public void reset(Bind bind) {
        bind.setKey(KeyCodes.NONE);
        bind.setMode(Mode.TOGGLE);
    }

    // ------------------------------------------------------------------ config

    public Json toJson() {
        Json.JsonObject object = Json.object();
        for (Bind bind : binds.values()) {
            Json.JsonObject entry = Json.object();
            entry.set("key", bind.keyName());
            entry.set("mode", bind.mode().display());
            entry.set("enabled", bind.isEnabled());
            object.set(bind.id(), entry);
        }
        return object;
    }

    public void fromJson(Json value) {
        if (!(value instanceof Json.JsonObject)) {
            return;
        }
        Json.JsonObject object = (Json.JsonObject) value;
        for (Map.Entry<String, Json> entry : object.members().entrySet()) {
            Bind bind = binds.get(entry.getKey());
            if (bind == null) {
                continue;
            }
            Json.JsonObject data = entry.getValue() instanceof Json.JsonObject
                    ? (Json.JsonObject) entry.getValue()
                    : Json.object();
            bind.setKey(KeyCodes.parse(data.string("key", bind.keyName())));
            bind.setMode(Mode.parse(data.string("mode", bind.mode().display()), Mode.TOGGLE));
            bind.setEnabled(data.bool("enabled", true));
        }
    }

    /** Applies bind keys back onto module settings (keeps both systems in sync). */
    public void syncToModules(java.util.Collection<Module> modules) {
        for (Module module : modules) {
            Bind bind = forModule(module);
            if (bind != null) {
                module.setKeybind(bind.key());
            }
        }
    }

    /** Applies module settings back onto binds (after loading a config). */
    public void syncFromModules(java.util.Collection<Module> modules) {
        for (Module module : modules) {
            Bind bind = forModule(module);
            if (bind != null) {
                bind.setKey(module.keybind());
                bind.setMode(Mode.parse(module.bindMode(), Mode.TOGGLE));
            }
        }
    }
}
