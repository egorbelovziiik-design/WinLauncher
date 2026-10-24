package nexus.core.module.movement;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Holds the sprint key while you move forward. */
public final class SprintModule extends Module {

    private static final int SPRINT = nexus.core.automation.MacroManager.KeyMappingKeys.SPRINT;

    private final BooleanSetting requireForward;

    public SprintModule() {
        super("sprint", "Sprint", "Keeps sprinting while you move forward (hold to sprint).",
                ModuleCategory.MOVEMENT);
        this.requireForward = bool("Require Forward", "Only sprint while moving forward", true);
    }

    @Override
    public void onEnable() {
        mc().input().setKeyState(SPRINT, true);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        boolean forward = !requireForward.isEnabled() || mc().input().isKeyDown(87);
        mc().input().setKeyState(SPRINT, forward);
        if (forward) {
            player().setSprinting(true);
        }
    }

    @Override
    public void onDisable() {
        mc().input().setKeyState(SPRINT, false);
        player().setSprinting(false);
    }
}
