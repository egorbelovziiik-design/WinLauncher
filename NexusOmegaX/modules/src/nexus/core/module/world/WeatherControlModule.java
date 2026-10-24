package nexus.core.module.world;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Forces clear weather on the client. */
public final class WeatherControlModule extends Module {

    private final ModeSetting weather;
    private final BooleanSetting stopThunder;

    public WeatherControlModule() {
        super("weathercontrol", "WeatherControl", "Forces a weather state on the client.",
                ModuleCategory.WORLD);
        this.weather = mode("Weather", "Weather to force", "Clear", "Clear", "Rain", "Thunder");
        this.stopThunder = bool("No Thunder", "Disable thunder flashes", true);
    }

    public String forcedWeather() {
        return weather.get();
    }

    public boolean thunderDisabled() {
        return isEnabled() && stopThunder.isEnabled();
    }

    @Override
    public void onEnable() {
        mc().world().reloadRenderer();
    }

    @Override
    public void onDisable() {
        mc().world().reloadRenderer();
    }

    @Override
    public void cleanup() {
        mc().world().reloadRenderer();
    }
}
