package nexus.core.module.render;

import nexus.core.hud.HudElement;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Enables and configures the radar HUD element. */
public final class RadarModule extends Module {

    private final NumberSetting range;
    private final NumberSetting size;
    private final BooleanSetting rotate;

    public RadarModule() {
        super("radar", "Radar", "Shows nearby entities on a circular radar.", ModuleCategory.RENDER);
        this.range = number("Range", "Radar range in blocks", 48.0, 8.0, 256.0, 1.0);
        this.size = number("Size", "Radar diameter", 78.0, 40.0, 200.0, 1.0);
        this.rotate = bool("Rotate", "Rotate with your view", true);
    }

    @Override
    public void onEnable() {
        apply(true);
    }

    @Override
    public void onDisable() {
        apply(false);
    }

    private void apply(boolean value) {
        HudElement element = nexus.core.Nexus.instance().hud().byId("radar");
        if (element == null) {
            return;
        }
        element.setEnabled(value);
        set(element.setting("Range"), range.value());
        set(element.setting("Size"), size.value());
        nexus.core.setting.Setting<?> rotateSetting = element.setting("Rotate");
        if (rotateSetting instanceof BooleanSetting) {
            ((BooleanSetting) rotateSetting).set(Boolean.valueOf(rotate.isEnabled()));
        }
    }

    private void set(nexus.core.setting.Setting<?> setting, double value) {
        if (setting instanceof NumberSetting) {
            ((NumberSetting) setting).set(Double.valueOf(value));
        }
    }
}
