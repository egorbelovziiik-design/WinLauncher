package nexus.core.module.client;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** General client behaviour: saving, sounds, notifications and animations. */
public final class ClientSettingsModule extends Module {

    private final BooleanSetting autoSave;
    private final BooleanSetting sounds;
    private final BooleanSetting notifications;
    private final NumberSetting animationSpeed;
    private final BooleanSetting startEnabled;

    public ClientSettingsModule() {
        super("clientsettings", "ClientSettings", "General client behaviour.", ModuleCategory.CLIENT);
        this.autoSave = bool("Auto Save", "Save the config automatically", true);
        this.sounds = bool("Sounds", "Play client interface sounds", true);
        this.notifications = bool("Notifications", "Show client notifications", true);
        this.animationSpeed = number("Animation", "Interface animation speed", 1.0, 0.25, 3.0, 0.05);
        this.startEnabled = bool("Enable On Start", "Enable this module when the client starts", true);
    }

    public boolean autoSave() {
        return autoSave.isEnabled();
    }

    public boolean soundsEnabled() {
        return sounds.isEnabled();
    }

    public boolean notificationsEnabled() {
        return notifications.isEnabled();
    }

    public double animationSpeed() {
        return animationSpeed.value();
    }

    @Override
    public String hudInfo() {
        return "x" + animationSpeed.value();
    }
}
