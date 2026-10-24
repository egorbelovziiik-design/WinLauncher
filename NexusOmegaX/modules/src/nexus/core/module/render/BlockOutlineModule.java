package nexus.core.module.render;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Customizes the block outline you see when looking at a block. */
public final class BlockOutlineModule extends Module {

    private final ColorSetting color;
    private final NumberSetting thickness;
    private final BooleanSetting rainbow;
    private final BooleanSetting hideVanilla;

    public BlockOutlineModule() {
        super("blockoutline", "BlockOutline", "Customizes the block selection outline.", ModuleCategory.RENDER);
        this.color = color("Color", "Outline color", 0xFF000000);
        this.thickness = number("Thickness", "Outline thickness", 2.0, 0.5, 8.0, 0.5);
        this.rainbow = bool("Rainbow", "Cycle the color", false);
        this.hideVanilla = bool("Hide Vanilla", "Hide the vanilla outline", false);
    }

    public int outlineColor(long timeMs) {
        return color.effectiveColor(timeMs);
    }

    public double outlineThickness() {
        return thickness.value();
    }

    public boolean rainbow() {
        return rainbow.isEnabled();
    }

    public boolean hideVanillaOutline() {
        return hideVanilla.isEnabled();
    }

    public boolean active() {
        return isEnabled();
    }
}
