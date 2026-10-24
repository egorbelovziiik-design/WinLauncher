package nexus.core.module.render;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Post processing style screen effects: vignette, scanlines, bloom. */
public final class ScreenEffectsModule extends Module {

    private final BooleanSetting vignette;
    private final BooleanSetting scanlines;
    private final BooleanSetting bloom;
    private final NumberSetting vignetteStrength;
    private final NumberSetting scanlineAlpha;

    public ScreenEffectsModule() {
        super("screeneffects", "ScreenEffects", "Vignette, scanlines and bloom post effects.",
                ModuleCategory.RENDER);
        this.vignette = bool("Vignette", "Darken the screen corners", true);
        this.scanlines = bool("Scanlines", "Draw subtle scanlines", false);
        this.bloom = bool("Bloom", "Brighten bright areas", true);
        this.vignetteStrength = number("Vignette", "Vignette strength", 0.6, 0.05, 1.0, 0.05);
        this.scanlineAlpha = number("Scanline Alpha", "Scanline opacity", 30.0, 0.0, 120.0, 5.0);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (scanlines.isEnabled()) {
            int alpha = (int) scanlineAlpha.value();
            for (double y = 0.0; y < event.height; y += 3.0) {
                event.renderer.fillRect(0.0, y, event.width, 1.0, ColorUtil.withAlpha(0xFF000000, alpha));
            }
        }
        if (vignette.isEnabled()) {
            int strength = (int) (vignetteStrength.value() * 140.0);
            int steps = 24;
            for (int i = 0; i < steps; i++) {
                double inset = i * (Math.min(event.width, event.height) / (double) (steps * 2.0));
                int alpha = (int) (strength * (1.0 - i / (double) steps) / steps);
                event.renderer.outlineRect(inset, inset, event.width - inset * 2.0,
                        event.height - inset * 2.0, 2.0, ColorUtil.withAlpha(0xFF000000, alpha));
            }
        }
        if (bloom.isEnabled()) {
            int accent = nexus.core.Nexus.instance().themes().accent(System.currentTimeMillis());
            event.renderer.fillGradientRect(0.0, 0.0, event.width, 2.0,
                    ColorUtil.withAlpha(accent, 90), 0x00000000, true);
        }
    }
}
