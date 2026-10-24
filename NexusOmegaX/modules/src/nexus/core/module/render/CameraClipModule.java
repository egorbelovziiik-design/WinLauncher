package nexus.core.module.render;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Removes the near clipping plane so you can look through walls up close. */
public final class CameraClipModule extends Module {

    private final NumberSetting distance;

    public CameraClipModule() {
        super("cameraclip", "CameraClip", "Reduces the camera clipping plane.", ModuleCategory.RENDER);
        this.distance = number("Distance", "Near clip distance", 0.05, 0.01, 1.0, 0.01);
    }

    @Override
    public void onEnable() {
        nexus.core.Nexus.instance().notifications().info("CameraClip", "Active while enabled");
    }

    public double clipDistance() {
        return isEnabled() ? distance.value() : 0.05;
    }

    @Override
    public String hudInfo() {
        return String.valueOf(distance.value());
    }
}
