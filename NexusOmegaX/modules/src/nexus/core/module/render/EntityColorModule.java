package nexus.core.module.render;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Tints entities with a color to make them stand out. */
public final class EntityColorModule extends Module {

    private final ColorSetting color;
    private final NumberSetting alpha;
    private final BooleanSetting playersOnly;

    public EntityColorModule() {
        super("entitycolor", "EntityColor", "Tints entities with a color.", ModuleCategory.RENDER);
        this.color = color("Color", "Tint color", 0xFFC084FC);
        this.alpha = number("Opacity", "Tint opacity", 60.0, 0.0, 255.0, 5.0);
        this.playersOnly = bool("Players Only", "Only tint players", true);
    }

    public int tintColor(long timeMs) {
        return color.effectiveColor(timeMs);
    }

    public int tintAlpha() {
        return (int) alpha.value();
    }

    public boolean playersOnly() {
        return playersOnly.isEnabled();
    }
}
