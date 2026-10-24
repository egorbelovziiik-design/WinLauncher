package nexus.core.module.misc;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Caps the frame rate to save power and reduce noise. */
public final class FpsLimiterModule extends Module {

    private final NumberSetting limit;
    private final BooleanSetting onlyUnfocused;
    private long lastFrame = -1L;

    public FpsLimiterModule() {
        super("fpslimiter", "FPSLimiter", "Caps the frame rate.", ModuleCategory.MISC);
        this.limit = number("Limit", "Maximum frames per second", 60.0, 10.0, 240.0, 5.0);
        this.onlyUnfocused = bool("Only Unfocused", "Only limit when the window is unfocused", false);
    }

    /** Returns true when the current frame should be skipped. */
    public boolean skipFrame(boolean focused) {
        if (!isEnabled() || (onlyUnfocused.isEnabled() && focused)) {
            return false;
        }
        long now = System.nanoTime();
        double targetMs = 1000.0 / limit.value();
        if (lastFrame < 0L) {
            lastFrame = now;
            return false;
        }
        double elapsed = (now - lastFrame) / 1000000.0;
        if (elapsed < targetMs) {
            return true;
        }
        lastFrame = now;
        return false;
    }

    @Override
    public String hudInfo() {
        return (int) limit.value() + " fps";
    }
}
