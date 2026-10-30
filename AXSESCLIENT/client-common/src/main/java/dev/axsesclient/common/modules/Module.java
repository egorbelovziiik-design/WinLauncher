package dev.axsesclient.common.modules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.axsesclient.common.platform.KeyCodes;
import dev.axsesclient.common.platform.MinecraftAdapter;
import dev.axsesclient.common.platform.RenderAdapter;
import dev.axsesclient.common.settings.KeybindSetting;
import dev.axsesclient.common.settings.Setting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public abstract class Module {
    private final String id;
    private final String name;
    private final String description;
    private final ModuleCategory category;
    private final Set<String> supportedVersions;
    private final boolean requiresSingleplayer;

    private boolean enabled;
    private boolean favorite;
    private final KeybindSetting keybind;
    private final List<Setting<?>> settings = new ArrayList<Setting<?>>();
    private int consecutiveErrorCount = 0;
    private String lastErrorMessage = null;

    protected Module(String id, String name, String description, ModuleCategory category) {
        this(id, name, description, category, false, KeyCodes.KEY_NONE, false, "1.16.5", "1.21.4", "1.21.11");
    }

    protected Module(
            String id,
            String name,
            String description,
            ModuleCategory category,
            boolean defaultEnabled,
            int defaultKeybind,
            boolean requiresSingleplayer,
            String... supportedMcVersions
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.enabled = defaultEnabled;
        this.requiresSingleplayer = requiresSingleplayer;
        this.keybind = new KeybindSetting("keybind", "Keybind", "Toggle keybind for " + name, defaultKeybind);
        Set<String> vers = new LinkedHashSet<String>();
        if (supportedMcVersions != null && supportedMcVersions.length > 0) {
            vers.addAll(Arrays.asList(supportedMcVersions));
        } else {
            vers.add("1.16.5");
            vers.add("1.21.4");
            vers.add("1.21.11");
        }
        this.supportedVersions = Collections.unmodifiableSet(vers);
    }

    protected final <S extends Setting<?>> S addSetting(S setting) {
        settings.add(setting);
        return setting;
    }

    public final String getId() { return id; }
    public final String getName() { return name; }
    public final String getDescription() { return description; }
    public final ModuleCategory getCategory() { return category; }
    public final Set<String> getSupportedVersions() { return supportedVersions; }
    public final boolean isRequiresSingleplayer() { return requiresSingleplayer; }

    public final boolean isEnabled() { return enabled; }
    public final boolean isFavorite() { return favorite; }
    public final void setFavorite(boolean favorite) { this.favorite = favorite; }
    public final void toggleFavorite() { this.favorite = !this.favorite; }

    public final KeybindSetting getKeybind() { return keybind; }
    public final List<Setting<?>> getSettings() { return Collections.unmodifiableList(settings); }

    public String getIncompatibilityReason(MinecraftAdapter mc) {
        if (mc != null) {
            String ver = mc.getMinecraftVersion();
            if (ver != null && !ver.isEmpty() && !supportedVersions.contains(ver)) {
                return "Unsupported on Minecraft " + ver + " (Supported: " + supportedVersions + ")";
            }
            if (requiresSingleplayer && mc.isInWorld() && !mc.isSingleplayer()) {
                return "Restricted to Singleplayer / Local Practice Worlds for fair-play compliance";
            }
        }
        return null;
    }

    public boolean isCompatibleNow(MinecraftAdapter mc) {
        return getIncompatibilityReason(mc) == null;
    }

    public final boolean setEnabled(boolean target, MinecraftAdapter mc) {
        if (target && !isCompatibleNow(mc)) {
            this.enabled = false;
            return false;
        }
        if (this.enabled == target) {
            return true;
        }
        this.enabled = target;
        if (target) {
            consecutiveErrorCount = 0;
            onEnable(mc);
        } else {
            onDisable(mc);
        }
        return true;
    }

    public final boolean toggle(MinecraftAdapter mc) {
        return setEnabled(!this.enabled, mc);
    }

    public void onEnable(MinecraftAdapter mc) {
    }

    public void onDisable(MinecraftAdapter mc) {
    }

    public void onTick(MinecraftAdapter mc) {
    }

    public void onRender(RenderAdapter renderer, MinecraftAdapter mc, float deltaSeconds) {
    }

    /**
     * Optional concise text line rendered when this module is pinned as a HUD widget.
     */
    public String getHudBadgeText(MinecraftAdapter mc) {
        return null;
    }

    public void resetSettings(MinecraftAdapter mc) {
        keybind.reset();
        for (Setting<?> s : settings) {
            s.reset();
        }
    }

    public int recordError(Throwable t) {
        consecutiveErrorCount++;
        lastErrorMessage = t == null ? "Unknown error" : (t.getClass().getSimpleName() + ": " + t.getMessage());
        return consecutiveErrorCount;
    }

    public void clearErrors() {
        consecutiveErrorCount = 0;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", Boolean.valueOf(enabled));
        o.addProperty("favorite", Boolean.valueOf(favorite));
        o.add("keybind", keybind.toJson());
        JsonObject sObj = new JsonObject();
        for (Setting<?> s : settings) {
            sObj.add(s.getId(), s.toJson());
        }
        o.add("settings", sObj);
        return o;
    }

    public void fromJson(JsonObject o, MinecraftAdapter mc) {
        if (o == null) {
            return;
        }
        if (o.has("favorite") && o.get("favorite").isJsonPrimitive()) {
            setFavorite(o.get("favorite").getAsBoolean());
        }
        if (o.has("keybind")) {
            keybind.fromJson(o.get("keybind"));
        }
        if (o.has("settings") && o.get("settings").isJsonObject()) {
            JsonObject sObj = o.getAsJsonObject("settings");
            for (Setting<?> s : settings) {
                if (sObj.has(s.getId())) {
                    JsonElement val = sObj.get(s.getId());
                    try {
                        s.fromJson(val);
                    } catch (Exception ignored) {
                        s.reset();
                    }
                }
            }
        }
        if (o.has("enabled") && o.get("enabled").isJsonPrimitive()) {
            boolean wantEnabled = o.get("enabled").getAsBoolean();
            setEnabled(wantEnabled, mc);
        }
    }
}
