package nexus.core.module.render;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Extra visual weather effects. */
public final class WeatherEffectsModule extends Module {

    private final ModeSetting style;
    private final NumberSetting intensity;
    private final ColorSetting color;

    public WeatherEffectsModule() {
        super("weathereffects", "WeatherEffects", "Extra weather visuals such as snow or embers.",
                ModuleCategory.RENDER);
        this.style = mode("Style", "Effect style", "Snow", "Snow", "Embers", "Leaves", "Bubbles");
        this.intensity = number("Intensity", "Effect intensity", 1.0, 0.1, 3.0, 0.1);
        this.color = color("Color", "Effect color", 0xFFFFFFFF);
    }

    public String effectStyle() {
        return style.get();
    }

    public double intensity() {
        return intensity.value();
    }

    public int effectColor(long timeMs) {
        return color.effectiveColor(timeMs);
    }

    @Override
    public String hudInfo() {
        return style.get();
    }
}
