package nexus.core.module.render;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Brighter, denser and colored night sky. */
public final class CustomStarsModule extends Module {

    private final NumberSetting density;
    private final NumberSetting brightness;
    private final ColorSetting color;

    public CustomStarsModule() {
        super("customstars", "CustomStars", "Brighter and denser stars in the night sky.",
                ModuleCategory.RENDER);
        this.density = number("Density", "Star density multiplier", 2.0, 0.0, 10.0, 0.5);
        this.brightness = number("Brightness", "Star brightness", 1.0, 0.1, 3.0, 0.1);
        this.color = color("Color", "Star tint", 0xFFFFFFFF);
    }

    public double density() {
        return density.value();
    }

    public double brightness() {
        return brightness.value();
    }

    public int starColor(long timeMs) {
        return color.effectiveColor(timeMs);
    }
}
