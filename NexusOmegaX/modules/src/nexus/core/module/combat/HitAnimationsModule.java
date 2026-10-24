package nexus.core.module.combat;

import nexus.core.event.AttackEvent;
import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.Easing;

/** Screen shake and crosshair animation when you land a hit. */
public final class HitAnimationsModule extends Module {

    private final NumberSetting shakeAmount;
    private final NumberSetting duration;
    private final BooleanSetting crosshair;

    private long hitTime = -1L;

    public HitAnimationsModule() {
        super("hitanimations", "HitAnimations", "Adds a subtle shake and crosshair pop when you hit a target.",
                ModuleCategory.COMBAT);
        this.shakeAmount = number("Shake", "Screen shake strength", 0.6, 0.0, 4.0, 0.1);
        this.duration = number("Duration", "Animation duration in ms", 220.0, 60.0, 1000.0, 20.0);
        this.crosshair = bool("Crosshair Pop", "Animate the crosshair on hit", true);
    }

    @Override
    public void onAttack(AttackEvent event) {
        if (event.target != null) {
            hitTime = System.currentTimeMillis();
        }
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (hitTime < 0L) {
            return;
        }
        double progress = (System.currentTimeMillis() - hitTime) / duration.value();
        if (progress >= 1.0) {
            hitTime = -1L;
            return;
        }
        double eased = 1.0 - Easing.ease(Easing.EASE_OUT, progress);
        if (crosshair.isEnabled()) {
            double centerX = event.width / 2.0;
            double centerY = event.height / 2.0;
            double size = 6.0 + eased * 6.0;
            int alpha = (int) (200.0 * eased);
            event.renderer.fillRect(centerX - size, centerY - 0.75, size * 2.0, 1.5, (alpha << 24) | 0xFFFFFF);
            event.renderer.fillRect(centerX - 0.75, centerY - size, 1.5, size * 2.0, (alpha << 24) | 0xFFFFFF);
        }
    }

    /** Current shake offset requested by the render pipeline. */
    public double shakeOffset(long now) {
        if (hitTime < 0L) {
            return 0.0;
        }
        double progress = (now - hitTime) / duration.value();
        if (progress >= 1.0) {
            return 0.0;
        }
        return Math.sin(progress * Math.PI * 6.0) * shakeAmount.value() * (1.0 - progress);
    }
}
