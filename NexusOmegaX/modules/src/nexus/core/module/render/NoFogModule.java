package nexus.core.module.render;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Removes distance fog. */
public final class NoFogModule extends Module {

    private final BooleanSetting keepUnderwater;

    public NoFogModule() {
        super("nofog", "NoFog", "Removes distance fog for a clearer view.", ModuleCategory.RENDER);
        this.keepUnderwater = bool("Keep Underwater Fog", "Keep the fog while underwater", true);
    }

    public boolean suppressFog(boolean underwater) {
        if (!isEnabled()) {
            return false;
        }
        return !(underwater && keepUnderwater.isEnabled());
    }
}
