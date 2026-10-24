package nexus.core.module.render;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Custom moon appearance. */
public final class CustomMoonModule extends Module {

    private final ColorSetting color;
    private final NumberSetting size;
    private final BooleanSetting alwaysFull;

    public CustomMoonModule() {
        super("custommoon", "CustomMoon", "Changes the appearance of the moon.", ModuleCategory.RENDER);
        this.color = color("Color", "Moon color", 0xFFE5E7EB);
        this.size = number("Size", "Moon size multiplier", 1.0, 0.2, 4.0, 0.1);
        this.alwaysFull = bool("Always Full", "Always render the full moon", false);
    }

    public int moonColor(long timeMs) {
        return color.effectiveColor(timeMs);
    }

    public double moonSize() {
        return size.value();
    }

    public boolean alwaysFull() {
        return alwaysFull.isEnabled();
    }
}
