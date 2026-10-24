package nexus.core.module.movement;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import nexus.core.automation.MacroManager;

/** Holds the forward key for you. */
public final class AutoWalkModule extends Module {

    private final BooleanSetting sprint;

    public AutoWalkModule() {
        super("autowalk", "AutoWalk", "Holds the forward movement key.", ModuleCategory.MOVEMENT);
        this.sprint = bool("Sprint", "Also sprint while walking", false);
    }

    @Override
    public void onEnable() {
        mc().input().setKeyState(MacroManager.KeyMappingKeys.FORWARD, true);
        if (sprint.isEnabled()) {
            mc().input().setKeyState(MacroManager.KeyMappingKeys.SPRINT, true);
        }
    }

    @Override
    public void onTick(TickEvent event) {
        mc().input().setKeyState(MacroManager.KeyMappingKeys.FORWARD, true);
        if (sprint.isEnabled()) {
            mc().input().setKeyState(MacroManager.KeyMappingKeys.SPRINT, true);
        }
    }

    @Override
    public void onDisable() {
        mc().input().setKeyState(MacroManager.KeyMappingKeys.FORWARD, false);
        mc().input().setKeyState(MacroManager.KeyMappingKeys.SPRINT, false);
    }
}
