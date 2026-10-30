package dev.axsesclient.common.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SettingManager {
    private final Map<String, Setting<?>> settingsByQualifiedId = new LinkedHashMap<String, Setting<?>>();

    public synchronized void register(String ownerId, Setting<?> setting) {
        String key = ownerId + "." + setting.getId();
        settingsByQualifiedId.put(key, setting);
    }

    public synchronized Setting<?> get(String qualifiedId) {
        return settingsByQualifiedId.get(qualifiedId);
    }

    public synchronized List<Setting<?>> getAll() {
        return Collections.unmodifiableList(new ArrayList<Setting<?>>(settingsByQualifiedId.values()));
    }

    public synchronized List<KeybindSetting> getAllKeybindSettings() {
        List<KeybindSetting> result = new ArrayList<KeybindSetting>();
        for (Setting<?> s : settingsByQualifiedId.values()) {
            if (s instanceof KeybindSetting) {
                result.add((KeybindSetting) s);
            }
        }
        return result;
    }

    public synchronized void resetAll() {
        for (Setting<?> s : settingsByQualifiedId.values()) {
            s.reset();
        }
    }
}
