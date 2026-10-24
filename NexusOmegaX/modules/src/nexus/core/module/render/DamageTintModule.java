package nexus.core.module.render;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Colored screen flash and tint when you take damage. */
public final class DamageTintModule extends Module {

    private final ColorSetting color;
    private final NumberSetting duration;
    private final NumberSetting intensity;

    public DamageTintModule() {
        super("damagetint", "DamageTint", "Colored screen tint when you take damage.",
                ModuleCategory.RENDER);
        this.color = color("Color", "Tint color", 0xFFEF4444);
        this.duration = number("Duration", "Tint duration in ms", 300.0, 50.0, 2000.0, 50.0);
        this.intensity = number("Intensity", "Maximum opacity", 90.0, 0.0, 255.0, 5.0);
    }

    private long lastHit = -1L;

    public void onDamaged() {
        lastHit = System.currentTimeMillis();
    }

    public int currentTint(long timeMs) {
        if (!isEnabled() || lastHit < 0L) {
            return 0;
        }
        double progress = (timeMs - lastHit) / duration.value();
        if (progress >= 1.0) {
            lastHit = -1L;
            return 0;
        }
        int alpha = (int) (intensity.value() * (1.0 - progress));
        return (alpha << 24) | (color.effectiveColor(timeMs) & 0x00FFFFFF);
    }
}
