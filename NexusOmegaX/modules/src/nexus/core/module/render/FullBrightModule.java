package nexus.core.module.render;

import nexus.api.IOptions;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.render.RenderManager;
import nexus.core.setting.*;

/** Raises the brightness to see in the dark (gamma override). */
public final class FullBrightModule extends Module {

    private final NumberSetting strength;

    public FullBrightModule() {
        super("fullbright", "FullBright", "Brightens the world by raising the gamma.", ModuleCategory.RENDER);
        this.strength = number("Strength", "Gamma value (1 = normal, 15 = maximum)", 12.0, 1.0, 15.0, 0.5);
    }

    @Override
    public void onEnable() {
        apply();
    }

    @Override
    public void onDisable() {
        restore();
    }

    private void apply() {
        final IOptions options = mc().options();
        final double previous = options.gamma();
        options.setGamma(strength.value());
        mc().options();
        nexus.core.Nexus.instance().render().registerOverride("fullbright", new RenderManager.Override() {
            @Override
            public void restore() {
                options.setGamma(previous);
            }
        });
    }

    private void restore() {
        nexus.core.Nexus.instance().render().releaseOverride("fullbright");
    }

    @Override
    public void cleanup() {
        restore();
    }

    @Override
    public String hudInfo() {
        return String.valueOf((int) strength.value());
    }
}
