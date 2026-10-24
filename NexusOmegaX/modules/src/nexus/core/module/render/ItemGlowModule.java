package nexus.core.module.render;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Makes your held item glow. */
public final class ItemGlowModule extends Module {

    private final ColorSetting color;
    private final NumberSetting strength;
    private final BooleanSetting pulse;

    public ItemGlowModule() {
        super("itemglow", "ItemGlow", "Makes your held item glow.", ModuleCategory.RENDER);
        this.color = color("Color", "Glow color", 0xFFC084FC);
        this.strength = number("Strength", "Glow strength", 0.6, 0.05, 2.0, 0.05);
        this.pulse = bool("Pulse", "Pulse the glow", false);
    }

    public int glowColor(long timeMs) {
        return color.effectiveColor(timeMs);
    }

    public double glowStrength() {
        return strength.value();
    }

    public boolean pulse() {
        return pulse.isEnabled();
    }
}
