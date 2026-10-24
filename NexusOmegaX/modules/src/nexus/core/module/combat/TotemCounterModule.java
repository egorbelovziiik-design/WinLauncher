package nexus.core.module.combat;

import nexus.core.hud.HudElement;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Enables the totem counter HUD element. */
public final class TotemCounterModule extends Module {

    private final BooleanSetting showIcon;

    public TotemCounterModule() {
        super("totemcounter", "TotemCounter", "Shows how many totems of undying you carry.",
                ModuleCategory.COMBAT);
        this.showIcon = bool("Show Icon", "Draw the totem icon", true);
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
        HudElement element = nexus.core.Nexus.instance().hud().byId("totemcounter");
        if (element != null) {
            element.setEnabled(value);
        }
    }

    public int count() {
        return mc().isInGame() ? player().inventory().count("totem_of_undying") : 0;
    }
}
