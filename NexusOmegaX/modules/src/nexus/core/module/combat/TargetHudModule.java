package nexus.core.module.combat;

import nexus.core.event.TickEvent;
import nexus.core.hud.HudElement;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Enables and configures the TargetHUD element. */
public final class TargetHudModule extends Module {

    private final NumberSetting range;
    private final BooleanSetting showDistance;

    public TargetHudModule() {
        super("targethud", "TargetHUD", "Shows a panel with the health and distance of the current target.",
                ModuleCategory.COMBAT);
        this.range = number("Range", "Target search range", 24.0, 4.0, 128.0, 1.0);
        this.showDistance = bool("Distance", "Show the distance to the target", true);
    }

    @Override
    public void onEnable() {
        apply(true);
    }

    @Override
    public void onDisable() {
        apply(false);
    }

    @Override
    public void onTick(TickEvent event) {
        apply(isEnabled());
    }

    private void apply(boolean value) {
        HudElement element = nexus.core.Nexus.instance().hud().byId("targethud");
        if (element == null) {
            return;
        }
        element.setEnabled(value);
        nexus.core.setting.Setting<?> setting = element.setting("Range");
        if (setting instanceof NumberSetting) {
            ((NumberSetting) setting).set(Double.valueOf(range.value()));
        }
        nexus.core.setting.Setting<?> distance = element.setting("Show Distance");
        if (distance instanceof BooleanSetting) {
            ((BooleanSetting) distance).set(Boolean.valueOf(showDistance.isEnabled()));
        }
    }
}
