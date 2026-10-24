package nexus.core.module.world;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Locks the client side world time to a fixed value. */
public final class WorldTimeModule extends Module {

    private final ModeSetting time;
    private final NumberSetting custom;

    public WorldTimeModule() {
        super("worldtime", "WorldTime", "Locks the client side world time.", ModuleCategory.WORLD);
        this.time = mode("Time", "Time of day", "Day", "Day", "Night", "Sunrise", "Sunset", "Custom");
        this.custom = number("Custom", "Custom time (0-24000)", 6000.0, 0.0, 24000.0, 100.0);
    }

    public long lockedTime() {
        if (time.is("Day")) {
            return 6000L;
        }
        if (time.is("Night")) {
            return 18000L;
        }
        if (time.is("Sunrise")) {
            return 23000L;
        }
        if (time.is("Sunset")) {
            return 12000L;
        }
        return (long) custom.value();
    }

    @Override
    public String hudInfo() {
        return time.get();
    }
}
