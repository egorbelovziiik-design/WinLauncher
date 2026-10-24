package nexus.core.event;

import nexus.core.setting.Setting;

/** Fired whenever a setting value changes. */
public final class SettingChangeEvent extends Event {

    public final Setting<?> setting;

    public SettingChangeEvent(Setting<?> setting) {
        this.setting = setting;
    }

    @Override
    public boolean isCancellable() {
        return false;
    }
}
