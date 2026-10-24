package nexus.core.module.render;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Shows a cosmetic cape on your own player. */
public final class CustomCapeModule extends Module {

    private final ModeSetting style;
    private final ColorSetting primary;
    private final ColorSetting secondary;
    private final NumberSetting wave;

    public CustomCapeModule() {
        super("customcape", "CustomCape", "Shows a cosmetic cape on your player.", ModuleCategory.RENDER);
        this.style = mode("Style", "Cape design", "Gradient", "Gradient", "Wings", "Flag", "Glow");
        this.primary = color("Primary", "Primary color", 0xFF7C3AED);
        this.secondary = color("Secondary", "Secondary color", 0xFFC084FC);
        this.wave = number("Wave", "Wave motion strength", 0.4, 0.0, 2.0, 0.05);
    }

    public int primaryColor(long timeMs) {
        return primary.effectiveColor(timeMs);
    }

    public int secondaryColor(long timeMs) {
        return secondary.effectiveColor(timeMs);
    }

    public String capeStyle() {
        return style.get();
    }

    public double waveStrength() {
        return wave.value();
    }

    @Override
    public String hudInfo() {
        return style.get();
    }
}
