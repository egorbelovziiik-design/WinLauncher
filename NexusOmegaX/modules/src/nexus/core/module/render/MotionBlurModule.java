package nexus.core.module.render;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Adds a motion blur effect while moving fast. */
public final class MotionBlurModule extends Module {

    private final NumberSetting strength;
    private final NumberSetting threshold;
    private final BooleanSetting onlySprint;

    public MotionBlurModule() {
        super("motionblur", "MotionBlur", "Adds motion blur while moving fast.", ModuleCategory.RENDER);
        this.strength = number("Strength", "Blur strength", 0.4, 0.05, 1.0, 0.05);
        this.threshold = number("Threshold", "Speed needed to start blurring", 6.0, 1.0, 30.0, 0.5);
        this.onlySprint = bool("Only Sprint", "Only blur while sprinting", false);
    }

    public double blurAmount(double speed, boolean sprinting) {
        if (!isEnabled()) {
            return 0.0;
        }
        if (onlySprint.isEnabled() && !sprinting) {
            return 0.0;
        }
        if (speed < threshold.value()) {
            return 0.0;
        }
        return strength.value();
    }
}
