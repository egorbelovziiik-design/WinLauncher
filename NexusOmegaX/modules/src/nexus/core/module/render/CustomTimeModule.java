package nexus.core.module.render;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Locks the world time to a value you choose (client side). */
public final class CustomTimeModule extends Module {

    private final ModeSetting preset;
    private final NumberSetting custom;

    public CustomTimeModule() {
        super("customtime", "CustomTime", "Locks the client side world time.", ModuleCategory.RENDER);
        this.preset = mode("Time", "Time preset", "Noon", "Noon", "Midnight", "Sunrise", "Sunset", "Custom");
        this.custom = number("Custom", "Custom time value (0-24000)", 6000.0, 0.0, 24000.0, 100.0);
    }

    public long time() {
        if (preset.is("Noon")) {
            return 6000L;
        }
        if (preset.is("Midnight")) {
            return 18000L;
        }
        if (preset.is("Sunrise")) {
            return 23000L;
        }
        if (preset.is("Sunset")) {
            return 12000L;
        }
        return (long) custom.value();
    }

    public boolean isActive() {
        return isEnabled();
    }

    @Override
    public String hudInfo() {
        return preset.get();
    }
}
