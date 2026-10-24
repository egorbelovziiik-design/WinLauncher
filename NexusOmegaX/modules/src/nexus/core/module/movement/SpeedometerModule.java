package nexus.core.module.movement;

import nexus.core.hud.HudElement;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Enables the speedometer HUD element. */
public final class SpeedometerModule extends Module {

    private final ModeSetting unit;

    public SpeedometerModule() {
        super("speedometer", "Speedometer", "Shows your current movement speed.", ModuleCategory.MOVEMENT);
        this.unit = mode("Unit", "Displayed unit", "BPS", "BPS", "M/S", "KM/H");
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
        HudElement element = nexus.core.Nexus.instance().hud().byId("speedometer");
        if (element == null) {
            return;
        }
        element.setEnabled(value);
        nexus.core.setting.Setting<?> setting = element.setting("Unit");
        if (setting instanceof ModeSetting) {
            ((ModeSetting) setting).set(unit.get());
        }
    }
}
