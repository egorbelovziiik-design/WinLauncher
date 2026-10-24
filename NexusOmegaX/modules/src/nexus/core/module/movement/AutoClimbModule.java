package nexus.core.module.movement;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import nexus.core.automation.MacroManager;

/** Climbs ladders and vines automatically when you walk into them. */
public final class AutoClimbModule extends Module {

    private final BooleanSetting requireLook;
    private final NumberSetting speed;

    public AutoClimbModule() {
        super("autoclimb", "AutoClimb", "Climbs ladders and vines automatically.", ModuleCategory.MOVEMENT);
        this.requireLook = bool("Require Look", "Only climb when looking at the ladder", false);
        this.speed = number("Speed", "Climb boost", 1.0, 0.5, 2.0, 0.1);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !player().isClimbing()) {
            return;
        }
        if (requireLook.isEnabled() && Math.abs(player().pitch()) < 20.0f) {
            return;
        }
        if (player().velocity().y < 0.12 * speed.value()) {
            player().setVelocity(player().velocity().add(0.0, 0.06 * speed.value(), 0.0));
        }
        mc().input().setKeyState(MacroManager.KeyMappingKeys.FORWARD, true);
    }

    @Override
    public void onDisable() {
        mc().input().setKeyState(MacroManager.KeyMappingKeys.FORWARD, false);
    }
}
