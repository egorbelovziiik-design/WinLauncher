package nexus.core.module.render;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Disables rain and snow rendering. */
public final class NoWeatherModule extends Module {

    private final BooleanSetting keepSound;

    public NoWeatherModule() {
        super("noweather", "NoWeather", "Disables rain and snow rendering.", ModuleCategory.RENDER);
        this.keepSound = bool("Keep Sound", "Keep the rain sound", false);
    }

    public boolean suppressRain() {
        return isEnabled();
    }

    public boolean suppressRainSound() {
        return isEnabled() && !keepSound.isEnabled();
    }
}
