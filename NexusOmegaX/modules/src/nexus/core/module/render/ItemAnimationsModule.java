package nexus.core.module.render;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Animates items when you switch or use them. */
public final class ItemAnimationsModule extends Module {

    private final ModeSetting style;
    private final NumberSetting speed;
    private final BooleanSetting onSwitch;

    public ItemAnimationsModule() {
        super("itemanimations", "ItemAnimations", "Animates items when switching or using them.",
                ModuleCategory.RENDER);
        this.style = mode("Style", "Animation style", "Slide", "Slide", "Fade", "Scale", "Spin");
        this.speed = number("Speed", "Animation speed", 1.0, 0.2, 3.0, 0.05);
        this.onSwitch = bool("On Switch", "Animate on hotbar switch", true);
    }

    public String animationStyle() {
        return isEnabled() ? style.get() : "None";
    }

    public double speed() {
        return speed.value();
    }

    public boolean animateOnSwitch() {
        return onSwitch.isEnabled();
    }
}
