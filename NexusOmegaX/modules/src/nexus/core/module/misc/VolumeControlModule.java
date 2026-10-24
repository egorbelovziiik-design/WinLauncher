package nexus.core.module.misc;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Master volume slider that is saved with your profile. */
public final class VolumeControlModule extends Module {

    private final NumberSetting volume;
    private final BooleanSetting restoreOnDisable;
    private float previous = 1.0f;

    public VolumeControlModule() {
        super("volumecontrol", "VolumeControl", "Master volume slider saved with your profile.",
                ModuleCategory.MISC);
        this.volume = number("Volume", "Master volume in percent", 100.0, 0.0, 100.0, 5.0);
        this.restoreOnDisable = bool("Restore", "Restore the previous volume when disabled", true);
    }

    @Override
    public void onEnable() {
        previous = mc().options().soundVolumeMaster();
        apply();
    }

    @Override
    public void onDisable() {
        if (restoreOnDisable.isEnabled()) {
            mc().options().setSoundVolumeMaster(previous);
        }
    }

    @Override
    public void cleanup() {
        if (restoreOnDisable.isEnabled()) {
            mc().options().setSoundVolumeMaster(previous);
        }
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        if (isEnabled()) {
            apply();
        }
    }

    private void apply() {
        mc().options().setSoundVolumeMaster((float) (volume.value() / 100.0));
    }

    @Override
    public String hudInfo() {
        return (int) volume.value() + "%";
    }
}
