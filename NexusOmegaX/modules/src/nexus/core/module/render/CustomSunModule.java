package nexus.core.module.render;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Custom sun appearance. */
public final class CustomSunModule extends Module {

    private final ColorSetting color;
    private final NumberSetting size;
    private final BooleanSetting glow;

    public CustomSunModule() {
        super("customsun", "CustomSun", "Changes the appearance of the sun.", ModuleCategory.RENDER);
        this.color = color("Color", "Sun color", 0xFFFBBF24);
        this.size = number("Size", "Sun size multiplier", 1.0, 0.2, 4.0, 0.1);
        this.glow = bool("Glow", "Add a glow around the sun", true);
    }

    public int sunColor(long timeMs) {
        return color.effectiveColor(timeMs);
    }

    public double sunSize() {
        return size.value();
    }

    public boolean glow() {
        return glow.isEnabled();
    }
}
