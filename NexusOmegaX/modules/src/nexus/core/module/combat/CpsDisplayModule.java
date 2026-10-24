package nexus.core.module.combat;

import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Enables the CPS HUD element and configures its layout. */
public final class CpsDisplayModule extends Module {

    private final BooleanSetting splitButtons;

    public CpsDisplayModule() {
        super("cpsdisplay", "CPSDisplay", "Shows your clicks per second on the HUD.",
                ModuleCategory.COMBAT);
        this.splitButtons = bool("Split Buttons", "Show left and right clicks separately", true);
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
    public void onRender2D(Render2DEvent event) {
        apply(isEnabled());
    }

    private void apply(boolean value) {
        HudElement element = nexus.core.Nexus.instance().hud().byId("cps");
        if (element == null) {
            return;
        }
        element.setEnabled(value);
        nexus.core.setting.Setting<?> setting = element.setting("Split Buttons");
        if (setting instanceof BooleanSetting) {
            ((BooleanSetting) setting).set(Boolean.valueOf(splitButtons.isEnabled()));
        }
    }
}
