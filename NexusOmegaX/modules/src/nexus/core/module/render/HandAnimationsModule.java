package nexus.core.module.render;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Adds extra motion to your hand (bobbing and punching). */
public final class HandAnimationsModule extends Module {

    private final BooleanSetting punch;
    private final NumberSetting amount;
    private final BooleanSetting smooth;

    public HandAnimationsModule() {
        super("handanimations", "HandAnimations", "Adds extra motion to the held item.",
                ModuleCategory.RENDER);
        this.punch = bool("Punch", "Punch forward while swinging", true);
        this.amount = number("Amount", "Animation strength", 1.0, 0.1, 3.0, 0.05);
        this.smooth = bool("Smooth", "Smooth the animation", true);
    }

    public double amount() {
        return isEnabled() ? amount.value() : 0.0;
    }

    public boolean punch() {
        return punch.isEnabled();
    }

    public boolean smooth() {
        return smooth.isEnabled();
    }
}
