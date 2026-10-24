package nexus.core.module.render;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Removes the camera shake when you take damage. */
public final class NoHurtCamModule extends Module {

    private final BooleanSetting keepSound;

    public NoHurtCamModule() {
        super("nohurtcam", "NoHurtCam", "Removes the camera shake when you are hurt.", ModuleCategory.RENDER);
        this.keepSound = bool("Keep Sound", "Keep the hurt sound", true);
    }

    public boolean suppressShake() {
        return isEnabled();
    }

    public boolean suppressSound() {
        return isEnabled() && !keepSound.isEnabled();
    }
}
