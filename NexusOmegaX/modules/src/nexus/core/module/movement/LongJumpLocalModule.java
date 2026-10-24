package nexus.core.module.movement;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import nexus.core.automation.MacroManager;

/** Boosts the next jump into a long jump (server authoritative). */
public final class LongJumpLocalModule extends Module {

    private final NumberSetting forward;
    private final NumberSetting upward;
    private final BooleanSetting requireSprint;

    public LongJumpLocalModule() {
        super("longjumplocal", "LongJumpLocal", "Turns your jump into a long jump. Requires a permissive server.",
                ModuleCategory.MOVEMENT);
        this.forward = number("Forward", "Forward boost", 0.45, 0.0, 2.0, 0.05);
        this.upward = number("Upward", "Upward boost", 0.25, 0.0, 1.5, 0.05);
        this.requireSprint = bool("Require Sprint", "Only boost while sprinting", true);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !player().isOnGround()) {
            return;
        }
        if (requireSprint.isEnabled() && !player().isSprinting()) {
            return;
        }
        if (!mc().input().isKeyDown(MacroManager.KeyMappingKeys.JUMP) && !mc().input().isKeyDown(nexus.api.KeyCodes.SPACE)) {
            return;
        }
        if (player().velocity().y <= 0.0) {
            return;
        }
        double yaw = Math.toRadians(player().yaw());
        player().setVelocity(player().velocity().add(-Math.sin(yaw) * forward.value(), upward.value(),
                Math.cos(yaw) * forward.value()));
        markActivity();
    }
}
