package nexus.core.module;

import nexus.api.IClient;
import nexus.core.Nexus;
import nexus.core.event.*;
import nexus.core.setting.*;
import nexus.util.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class of every Nexus module.
 * <p>
 * Lifecycle contract enforced here (quality control section of the spec):
 * <ol>
 *   <li>enable() -> onEnable() + handlers subscribed + ModuleToggleEvent</li>
 *   <li>disable() -> handlers unsubscribed + onDisable() + state cleanup</li>
 *   <li>every module saves and loads all of its settings</li>
 *   <li>every module owns a keybind setting</li>
 *   <li>version support is checked before enabling</li>
 * </ol>
 */
public abstract class Module {

    private final String name;
    private final String displayName;
    private final String description;
    private final ModuleCategory category;

    private final List<Setting<?>> settings = new ArrayList<Setting<?>>();
    private final Map<String, Setting<?>> settingsByName = new LinkedHashMap<String, Setting<?>>();

    private final KeybindSetting keybind;
    private final ModeSetting bindMode;
    private final BooleanSetting hiddenFromArray;

    private boolean enabled;
    private boolean favorite;
    private long enabledAt;
    private long lastActivity;

    protected Module(String name, String displayName, String description, ModuleCategory category) {
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.category = category;

        this.keybind = new KeybindSetting("Keybind", "Key used to toggle this module", -1);
        this.bindMode = new ModeSetting("Bind Mode", "Toggle = press once, Hold = while held, Double = double tap",
                "Toggle", "Toggle", "Hold", "DoubleClick");
        this.hiddenFromArray = new BooleanSetting("Hide In HUD", "Do not show this module in the array list", false);

        addSetting(keybind);
        addSetting(bindMode);
        addSetting(hiddenFromArray);
    }

    // ---------------------------------------------------------------- identity

    public String name() {
        return name;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public ModuleCategory category() {
        return category;
    }

    @Override
    public String toString() {
        return displayName;
    }

    // ---------------------------------------------------------------- settings

    protected final <T extends Setting<?>> T addSetting(T setting) {
        settings.add(setting);
        settingsByName.put(setting.name().toLowerCase(), setting);
        return setting;
    }

    public List<Setting<?>> settings() {
        return settings;
    }

    public List<Setting<?>> visibleSettings() {
        List<Setting<?>> visible = new ArrayList<Setting<?>>();
        for (Setting<?> setting : settings) {
            if (setting.isVisible()) {
                visible.add(setting);
            }
        }
        return visible;
    }

    @SuppressWarnings("unchecked")
    public <T extends Setting<?>> T setting(String settingName) {
        return (T) settingsByName.get(settingName.toLowerCase());
    }

    public boolean hasSetting(String settingName) {
        return settingsByName.containsKey(settingName.toLowerCase());
    }

    protected BooleanSetting bool(String name, String description, boolean defaultValue) {
        return addSetting(new BooleanSetting(name, description, defaultValue));
    }

    protected NumberSetting number(String name, String description, double value, double min, double max, double step) {
        return addSetting(new NumberSetting(name, description, value, min, max, step));
    }

    protected NumberSetting integer(String name, String description, int value, int min, int max) {
        return addSetting(new NumberSetting(name, description, value, min, max, 1, true, ""));
    }

    protected ModeSetting mode(String name, String description, String value, String... modes) {
        return addSetting(new ModeSetting(name, description, value, modes));
    }

    protected ColorSetting color(String name, String description, int color) {
        return addSetting(new ColorSetting(name, description, color));
    }

    protected StringSetting text(String name, String description, String value) {
        return addSetting(new StringSetting(name, description, value));
    }

    protected MultiSelectSetting multi(String name, String description, String[] options, String... selected) {
        return addSetting(new MultiSelectSetting(name, description, options, selected));
    }

    /** Convenience accessors used by module logic. */
    protected boolean boolValue(String name) {
        Setting<?> setting = settingsByName.get(name.toLowerCase());
        return setting instanceof BooleanSetting && ((BooleanSetting) setting).isEnabled();
    }

    protected double numberValue(String name) {
        Setting<?> setting = settingsByName.get(name.toLowerCase());
        return setting instanceof NumberSetting ? ((NumberSetting) setting).value() : 0.0;
    }

    protected int intValue(String name) {
        return (int) Math.round(numberValue(name));
    }

    protected String modeValue(String name) {
        Setting<?> setting = settingsByName.get(name.toLowerCase());
        return setting instanceof ModeSetting ? ((ModeSetting) setting).get() : "";
    }

    protected int colorValue(String name) {
        Setting<?> setting = settingsByName.get(name.toLowerCase());
        return setting instanceof ColorSetting ? ((ColorSetting) setting).color() : 0xFFFFFFFF;
    }

    // ------------------------------------------------------------------ keybind

    public KeybindSetting keybindSetting() {
        return keybind;
    }

    public int keybind() {
        return keybind.key();
    }

    public void setKeybind(int key) {
        keybind.set(Integer.valueOf(key));
    }

    public String bindMode() {
        return bindMode.get();
    }

    public boolean isHiddenFromArray() {
        return hiddenFromArray.isEnabled();
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean value) {
        favorite = value;
    }

    // ------------------------------------------------------------------ state

    public boolean isEnabled() {
        return enabled;
    }

    public long enabledAt() {
        return enabledAt;
    }

    public long lastActivity() {
        return lastActivity;
    }

    protected void markActivity() {
        lastActivity = System.currentTimeMillis();
    }

    public void setEnabled(boolean value) {
        setEnabled(value, true);
    }

    public void setEnabled(boolean value, boolean byUser) {
        if (value == enabled) {
            return;
        }
        if (value && !isSupported()) {
            Nexus.instance().notifications().error(name, "Not supported on " + Nexus.instance().platform().minecraftVersion());
            return;
        }
        enabled = value;
        if (enabled) {
            enabledAt = System.currentTimeMillis();
            try {
                onEnable();
                subscribeHandlers();
            } catch (Throwable throwable) {
                enabled = false;
                Nexus.instance().notifications().error(name, "Failed to enable (see logs)");
                nexus.util.Log.error("Module " + name + " failed to enable", throwable);
                return;
            }
        } else {
            unsubscribeHandlers();
            try {
                onDisable();
            } catch (Throwable throwable) {
                nexus.util.Log.error("Module " + name + " failed to disable cleanly", throwable);
            }
            cleanup();
        }
        markActivity();
        Nexus.eventBus().post(new ModuleToggleEvent(this, enabled, byUser));
        Nexus.instance().configManager().markDirty();
    }

    public void toggle() {
        setEnabled(!enabled, true);
    }

    /** Extra info rendered behind the module name in the HUD array list. */
    public String hudInfo() {
        return "";
    }

    // -------------------------------------------------------------- versioning

    /** Versions this module is compatible with; {@code *} means all of them. */
    public String[] supportedVersions() {
        return new String[]{"*"};
    }

    public boolean isSupported() {
        String[] supported = supportedVersions();
        if (supported == null || supported.length == 0) {
            return true;
        }
        for (String version : supported) {
            if ("*".equals(version)) {
                return true;
            }
        }
        String current = Nexus.isInitialized() ? Nexus.instance().platform().minecraftVersion() : null;
        if (current == null) {
            return true;
        }
        for (String version : supported) {
            if (version.equals(current)) {
                return true;
            }
        }
        return false;
    }

    // ----------------------------------------------------------------- events

    /** Subscribes the standard handlers; called on every enable. */
    private void subscribeHandlers() {
        EventBus bus = Nexus.eventBus();
        bus.subscribe(TickEvent.class, new Listener<TickEvent>() {
            @Override
            public void on(TickEvent event) {
                onTick(event);
            }
        }, 0, this);
        bus.subscribe(Render2DEvent.class, new Listener<Render2DEvent>() {
            @Override
            public void on(Render2DEvent event) {
                onRender2D(event);
            }
        }, 0, this);
        bus.subscribe(Render3DEvent.class, new Listener<Render3DEvent>() {
            @Override
            public void on(Render3DEvent event) {
                onRender3D(event);
            }
        }, 0, this);
        bus.subscribe(KeyEvent.class, new Listener<KeyEvent>() {
            @Override
            public void on(KeyEvent event) {
                onKey(event);
            }
        }, 0, this);
        bus.subscribe(MouseEvent.class, new Listener<MouseEvent>() {
            @Override
            public void on(MouseEvent event) {
                onMouse(event);
            }
        }, 0, this);
        bus.subscribe(ChatSendEvent.class, new Listener<ChatSendEvent>() {
            @Override
            public void on(ChatSendEvent event) {
                onChatSend(event);
            }
        }, 0, this);
        bus.subscribe(ChatReceiveEvent.class, new Listener<ChatReceiveEvent>() {
            @Override
            public void on(ChatReceiveEvent event) {
                onChatReceive(event);
            }
        }, 0, this);
        bus.subscribe(AttackEvent.class, new Listener<AttackEvent>() {
            @Override
            public void on(AttackEvent event) {
                onAttack(event);
            }
        }, 0, this);
        bus.subscribe(PacketEvent.class, new Listener<PacketEvent>() {
            @Override
            public void on(PacketEvent event) {
                onPacket(event);
            }
        }, 0, this);
        bus.subscribe(WorldJoinEvent.class, new Listener<WorldJoinEvent>() {
            @Override
            public void on(WorldJoinEvent event) {
                onWorldJoin(event);
            }
        }, 0, this);
        bus.subscribe(WorldLeaveEvent.class, new Listener<WorldLeaveEvent>() {
            @Override
            public void on(WorldLeaveEvent event) {
                onWorldLeave(event);
            }
        }, 0, this);
        bus.subscribe(DeathEvent.class, new Listener<DeathEvent>() {
            @Override
            public void on(DeathEvent event) {
                onDeath(event);
            }
        }, 0, this);
    }

    private void unsubscribeHandlers() {
        Nexus.eventBus().unsubscribe(this);
    }

    // -------------------------------------------------------------- overridables

    public void onEnable() {
    }

    public void onDisable() {
    }

    /** Restore any game state the module changed (gamma, fov, ...). */
    public void cleanup() {
    }

    public void onTick(TickEvent event) {
    }

    public void onRender2D(Render2DEvent event) {
    }

    public void onRender3D(Render3DEvent event) {
    }

    public void onKey(KeyEvent event) {
    }

    public void onMouse(MouseEvent event) {
    }

    public void onChatSend(ChatSendEvent event) {
    }

    public void onChatReceive(ChatReceiveEvent event) {
    }

    public void onAttack(AttackEvent event) {
    }

    public void onPacket(PacketEvent event) {
    }

    public void onWorldJoin(WorldJoinEvent event) {
    }

    public void onWorldLeave(WorldLeaveEvent event) {
    }

    public void onDeath(DeathEvent event) {
    }

    // ------------------------------------------------------------------ config

    public Json toJson() {
        Json.JsonObject object = Json.object();
        object.set("enabled", enabled);
        object.set("favorite", favorite);
        Json.JsonObject values = Json.object();
        for (Setting<?> setting : settings) {
            values.set(setting.name(), setting.toJson());
        }
        object.set("settings", values);
        return object;
    }

    public void fromJson(Json value) {
        if (!(value instanceof Json.JsonObject)) {
            return;
        }
        Json.JsonObject object = (Json.JsonObject) value;
        favorite = object.bool("favorite", favorite);
        Json.JsonObject values = object.getObject("settings", Json.object());
        for (Setting<?> setting : settings) {
            Json stored = values.get(setting.name());
            if (!stored.isNull()) {
                try {
                    setting.fromJson(stored);
                } catch (Throwable throwable) {
                    nexus.util.Log.warn("Invalid stored value for " + name + "." + setting.name() + ", using default");
                }
            }
        }
        boolean shouldEnable = object.bool("enabled", false);
        if (shouldEnable && !enabled) {
            setEnabled(true, false);
        } else if (!shouldEnable && enabled) {
            setEnabled(false, false);
        }
    }

    /** Shortcut to the client API. */
    protected IClient mc() {
        return Nexus.instance().client();
    }

    protected nexus.api.IPlayer player() {
        return Nexus.instance().client().player();
    }

    protected nexus.api.IWorld world() {
        return Nexus.instance().client().world();
    }
}
