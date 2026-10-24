package nexus.core.module.render;

import nexus.api.IOptions;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.render.RenderManager;
import nexus.core.setting.*;

/** Permanent night vision effect (client side brightness boost). */
public final class NightVisionModule extends Module {

    private final NumberSetting strength;
    private final BooleanSetting smooth;

    public NightVisionModule() {
        super("nightvision", "NightVision", "Permanent client side night vision.", ModuleCategory.RENDER);
        this.strength = number("Strength", "Brightness strength", 6.0, 0.5, 15.0, 0.5);
        this.smooth = bool("Smooth", "Fade the brightness in and out", true);
    }

    private double previous = -1.0;

    @Override
    public void onEnable() {
        final IOptions options = mc().options();
        previous = options.gamma();
        double target = Math.max(previous, strength.value());
        options.setGamma(smooth.isEnabled() ? previous : target);
        nexus.core.Nexus.instance().render().registerOverride("nightvision", new RenderManager.Override() {
            @Override
            public void restore() {
                options.setGamma(previous < 0.0 ? 1.0 : previous);
            }
        });
        if (smooth.isEnabled()) {
            nexus.core.Nexus.instance().client().schedule(new Runnable() {
                @Override
                public void run() {
                    if (isEnabled()) {
                        options.setGamma(target);
                    }
                }
            }, 10);
        }
    }

    @Override
    public void onDisable() {
        nexus.core.Nexus.instance().render().releaseOverride("nightvision");
    }

    @Override
    public void cleanup() {
        onDisable();
    }
}
