package nexus.core.module.render;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Paints a custom gradient sky. */
public final class CustomSkyModule extends Module {

    private final ColorSetting top;
    private final ColorSetting bottom;
    private final BooleanSetting stars;
    private final NumberSetting starCount;

    public CustomSkyModule() {
        super("customsky", "CustomSky", "Replaces the sky with a custom gradient.", ModuleCategory.RENDER);
        this.top = color("Top", "Top sky color", 0xFF0B0B1A);
        this.bottom = color("Bottom", "Bottom sky color", 0xFF4C1D95);
        this.stars = bool("Stars", "Draw extra stars", true);
        this.starCount = number("Star Count", "Number of stars", 120.0, 0.0, 600.0, 10.0);
    }

    /** Called by the adapter sky renderer. */
    public int topColor(long timeMs) {
        return top.effectiveColor(timeMs);
    }

    public int bottomColor(long timeMs) {
        return bottom.effectiveColor(timeMs);
    }

    public boolean drawStars() {
        return isEnabled() && stars.isEnabled();
    }

    public int starCount() {
        return (int) starCount.value();
    }

    public boolean enabled() {
        return isEnabled();
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled() || !stars.isEnabled()) {
            return;
        }
        // stars are drawn in the sky pass, this only previews the palette
        event.renderer.fillGradientRect(event.width - 60.0, 6.0, 54.0, 10.0,
                top.effectiveColor(System.currentTimeMillis()),
                bottom.effectiveColor(System.currentTimeMillis()), true);
        event.renderer.outlineRect(event.width - 60.0, 6.0, 54.0, 10.0, 1.0,
                ColorUtil.withAlpha(0xFFFFFFFF, 60));
    }
}
