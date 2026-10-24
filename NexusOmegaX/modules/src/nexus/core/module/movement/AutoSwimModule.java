package nexus.core.module.movement;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import nexus.core.automation.MacroManager;

/** Floats to the surface automatically while swimming. */
public final class AutoSwimModule extends Module {

    private final BooleanSetting dolphinMode;
    private final BooleanSetting onlyUnderwater;

    public AutoSwimModule() {
        super("autoswim", "AutoSwim", "Automatically swims up to the surface.", ModuleCategory.MOVEMENT);
        this.dolphinMode = bool("Fast Swim", "Swim faster by keeping the forward input", false);
        this.onlyUnderwater = bool("Only Underwater", "Only swim up when fully submerged", true);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        boolean submerged = player().isInWater();
        if (!submerged) {
            return;
        }
        if (onlyUnderwater.isEnabled() && !mc().world().isAir((int) Math.floor(player().position().x),
                (int) Math.floor(player().eyePosition().y + 0.6), (int) Math.floor(player().position().z))) {
            mc().input().setKeyState(MacroManager.KeyMappingKeys.JUMP, true);
        }
        if (dolphinMode.isEnabled()) {
            mc().input().setKeyState(MacroManager.KeyMappingKeys.FORWARD, true);
        }
    }

    @Override
    public void onDisable() {
        mc().input().setKeyState(MacroManager.KeyMappingKeys.JUMP, false);
        mc().input().setKeyState(MacroManager.KeyMappingKeys.FORWARD, false);
    }
}
