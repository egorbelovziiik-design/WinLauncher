package nexus.core.module.configs;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Controls how and when the configuration is written to disk. */
public final class ConfigManagerModule extends Module {

    private final BooleanSetting autoSave;
    private final NumberSetting interval;
    private final BooleanSetting saveOnShutdown;
    private final BooleanSetting prettyJson;
    private final nexus.util.TimeUtil.Timer timer = new nexus.util.TimeUtil.Timer();

    public ConfigManagerModule() {
        super("configmanager", "ConfigManager", "Controls how the configuration is saved.",
                ModuleCategory.CONFIGS);
        this.autoSave = bool("Auto Save", "Save the config automatically", true);
        this.interval = number("Interval", "Seconds between saves", 30.0, 5.0, 600.0, 5.0);
        this.saveOnShutdown = bool("Save On Exit", "Save when the client closes", true);
        this.prettyJson = bool("Pretty JSON", "Write human readable files", true);
    }

    @Override
    public void onEnable() {
        timer.reset();
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        if (!isEnabled() || !autoSave.isEnabled()) {
            return;
        }
        if (!timer.hasPassed((long) (interval.value() * 1000.0), true)) {
            return;
        }
        if (nexus.core.Nexus.instance().configManager().isDirty()) {
            nexus.core.Nexus.instance().configManager().saveAll(true);
            markActivity();
        }
    }

    public boolean saveOnShutdown() {
        return isEnabled() && saveOnShutdown.isEnabled();
    }

    public boolean prettyJson() {
        return prettyJson.isEnabled();
    }

    public String saveNow() {
        nexus.core.Nexus.instance().configManager().saveAll(true);
        markActivity();
        return "Configuration saved";
    }

    @Override
    public String hudInfo() {
        return nexus.core.Nexus.instance().configManager().activeProfile();
    }
}
