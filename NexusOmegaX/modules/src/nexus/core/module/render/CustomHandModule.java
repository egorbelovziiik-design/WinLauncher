package nexus.core.module.render;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Changes how your hand and item are rendered. */
public final class CustomHandModule extends Module {

    private final ModeSetting style;
    private final NumberSetting scale;
    private final NumberSetting swingSpeed;

    public CustomHandModule() {
        super("customhand", "CustomHand", "Changes the way your hand is rendered.", ModuleCategory.RENDER);
        this.style = mode("Style", "Hand style", "Default", "Default", "Small", "Invisible", "Spinning");
        this.scale = number("Scale", "Item scale", 1.0, 0.2, 2.0, 0.05);
        this.swingSpeed = number("Swing Speed", "Swing animation speed", 1.0, 0.2, 3.0, 0.05);
    }

    public String handStyle() {
        return style.get();
    }

    public double itemScale() {
        return isEnabled() ? scale.value() : 1.0;
    }

    public double swingSpeed() {
        return isEnabled() ? swingSpeed.value() : 1.0;
    }

    @Override
    public String hudInfo() {
        return style.get();
    }
}
