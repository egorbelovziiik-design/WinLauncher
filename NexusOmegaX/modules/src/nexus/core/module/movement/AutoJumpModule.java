package nexus.core.module.movement;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import nexus.core.automation.MacroManager;

/** Jumps automatically whenever you are on the ground. */
public final class AutoJumpModule extends Module {

    private final BooleanSetting onlyWalking;
    private final NumberSetting delay;
    private final nexus.util.TimeUtil.Timer timer = new nexus.util.TimeUtil.Timer();

    public AutoJumpModule() {
        super("autojump", "AutoJump", "Jumps automatically while you move.", ModuleCategory.MOVEMENT);
        this.onlyWalking = bool("Only While Moving", "Only jump while moving", true);
        this.delay = number("Delay", "Minimum delay between jumps", 250.0, 0.0, 2000.0, 50.0);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !player().isOnGround() || !timer.hasPassed((long) delay.value())) {
            return;
        }
        if (onlyWalking.isEnabled() && player().horizontalSpeed() < 0.02) {
            return;
        }
        mc().input().setKeyState(MacroManager.KeyMappingKeys.JUMP, true);
        timer.reset();
    }

    @Override
    public void onDisable() {
        mc().input().setKeyState(MacroManager.KeyMappingKeys.JUMP, false);
    }
}
