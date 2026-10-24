package nexus.core.module.render;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Removes or shrinks the fire overlay while burning. */
public final class NoFireOverlayModule extends Module {

    private final ModeSetting mode;

    public NoFireOverlayModule() {
        super("nofireoverlay", "NoFireOverlay", "Removes or shrinks the on screen fire overlay.",
                ModuleCategory.RENDER);
        this.mode = mode("Mode", "Overlay handling", "Remove", "Remove", "Shrink");
    }

    public double overlayScale() {
        if (!isEnabled()) {
            return 1.0;
        }
        return mode.is("Remove") ? 0.0 : 0.35;
    }
}
