package nexus.core.module.combat;

import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Enables the combat related HUD elements at once. */
public final class CombatHudModule extends Module {

    private final BooleanSetting cps;
    private final BooleanSetting targetHud;
    private final BooleanSetting totems;

    public CombatHudModule() {
        super("combathud", "CombatHUD", "Enables every combat related HUD element at once.",
                ModuleCategory.COMBAT);
        this.cps = bool("CPS", "Enable the CPS element", true);
        this.targetHud = bool("TargetHUD", "Enable the TargetHUD element", true);
        this.totems = bool("Totems", "Enable the totem counter", true);
    }

    @Override
    public void onEnable() {
        apply("cps", cps.isEnabled());
        apply("targethud", targetHud.isEnabled());
        apply("totemcounter", totems.isEnabled());
    }

    @Override
    public void onDisable() {
        apply("cps", false);
        apply("targethud", false);
        apply("totemcounter", false);
    }

    private void apply(String id, boolean value) {
        HudElement element = nexus.core.Nexus.instance().hud().byId(id);
        if (element != null) {
            element.setEnabled(value);
        }
    }
}
