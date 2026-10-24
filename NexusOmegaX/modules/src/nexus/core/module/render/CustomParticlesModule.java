package nexus.core.module.render;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Controls how many particles are rendered and how they look. */
public final class CustomParticlesModule extends Module {

    private final ModeSetting density;
    private final BooleanSetting onlyOwn;
    private final NumberSetting size;

    public CustomParticlesModule() {
        super("customparticles", "CustomParticles", "Controls particle density and size.",
                ModuleCategory.RENDER);
        this.density = mode("Density", "Particle amount", "Normal", "None", "Minimal", "Normal", "Fancy");
        this.onlyOwn = bool("Only Own", "Only show particles you create", false);
        this.size = number("Size", "Particle size multiplier", 1.0, 0.2, 3.0, 0.1);
    }

    public int maxParticles() {
        if (!isEnabled()) {
            return -1;
        }
        if (density.is("None")) {
            return 0;
        }
        if (density.is("Minimal")) {
            return 256;
        }
        if (density.is("Fancy")) {
            return 8192;
        }
        return 2048;
    }

    public double sizeMultiplier() {
        return isEnabled() ? size.value() : 1.0;
    }

    @Override
    public String hudInfo() {
        return density.get();
    }
}
