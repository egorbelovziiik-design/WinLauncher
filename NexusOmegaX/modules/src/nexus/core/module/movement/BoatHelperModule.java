package nexus.core.module.movement;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import nexus.core.automation.MacroManager;

/** Steadies boats: keeps a straight course and prevents accidental dismounts. */
public final class BoatHelperModule extends Module {

    private final BooleanSetting autoForward;
    private final NumberSetting turnAssist;
    private final BooleanSetting preventDismount;

    public BoatHelperModule() {
        super("boathelper", "BoatHelper", "Makes boat travel steadier and easier to control.",
                ModuleCategory.MOVEMENT);
        this.autoForward = bool("Auto Forward", "Hold forward while riding", false);
        this.turnAssist = number("Turn Assist", "Auto straighten strength", 0.5, 0.0, 3.0, 0.1);
        this.preventDismount = bool("Prevent Dismount", "Ignore accidental sneak dismounts", true);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !player().isRiding()) {
            return;
        }
        if (autoForward.isEnabled()) {
            mc().input().setKeyState(MacroManager.KeyMappingKeys.FORWARD, true);
        }
        if (turnAssist.value() > 0.0 && player().isRiding()) {
            float yaw = player().yaw();
            float snapped = Math.round(yaw / 45.0f) * 45.0f;
            float delta = snapped - yaw;
            if (Math.abs(delta) < 12.0f) {
                player().setRotation(yaw + delta * (float) (turnAssist.value() * 0.08), player().pitch());
            }
        }
    }

    @Override
    public void onDisable() {
        mc().input().setKeyState(MacroManager.KeyMappingKeys.FORWARD, false);
    }
}
