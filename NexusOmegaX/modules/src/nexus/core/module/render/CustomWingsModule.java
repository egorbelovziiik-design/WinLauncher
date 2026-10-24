package nexus.core.module.render;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Shows cosmetic wings on your player. */
public final class CustomWingsModule extends Module {

    private final ColorSetting color;
    private final NumberSetting size;
    private final BooleanSetting flap;

    public CustomWingsModule() {
        super("customwings", "CustomWings", "Shows cosmetic wings on your player.", ModuleCategory.RENDER);
        this.color = color("Color", "Wing color", 0xFFC084FC);
        this.size = number("Size", "Wing size", 1.0, 0.3, 2.5, 0.05);
        this.flap = bool("Flap", "Animate flapping while moving", true);
    }

    public int wingColor(long timeMs) {
        return color.effectiveColor(timeMs);
    }

    public double wingSize() {
        return size.value();
    }

    public boolean animate() {
        return flap.isEnabled();
    }
}
