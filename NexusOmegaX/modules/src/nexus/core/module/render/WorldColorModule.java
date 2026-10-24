package nexus.core.module.render;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Tints the whole world with a color overlay. */
public final class WorldColorModule extends Module {

    private final ColorSetting color;
    private final NumberSetting alpha;
    private final BooleanSetting onlySky;

    public WorldColorModule() {
        super("worldcolor", "WorldColor", "Tints the whole world with a color.", ModuleCategory.RENDER);
        this.color = color("Color", "Tint color", 0xFF7C3AED);
        this.alpha = number("Opacity", "Tint opacity", 30.0, 0.0, 255.0, 5.0);
        this.onlySky = bool("Sky Only", "Only tint the sky", false);
    }

    public int tintColor(long timeMs) {
        return color.effectiveColor(timeMs);
    }

    public int tintAlpha() {
        return (int) alpha.value();
    }

    public boolean skyOnly() {
        return onlySky.isEnabled();
    }
}
