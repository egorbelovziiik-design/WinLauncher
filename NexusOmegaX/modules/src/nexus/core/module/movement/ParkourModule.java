package nexus.core.module.movement;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import nexus.core.automation.MacroManager;

/** Jumps automatically right before you reach the edge of a block. */
public final class ParkourModule extends Module {

    private final BooleanSetting requireSprint;
    private final NumberSetting lookAhead;

    public ParkourModule() {
        super("parkour", "Parkour", "Jumps automatically at the edge of a block while running.",
                ModuleCategory.MOVEMENT);
        this.requireSprint = bool("Require Sprint", "Only jump while sprinting", false);
        this.lookAhead = number("Look Ahead", "Distance checked ahead in blocks", 0.9, 0.2, 2.0, 0.1);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !player().isOnGround()) {
            return;
        }
        if (requireSprint.isEnabled() && !player().isSprinting()) {
            return;
        }
        double yaw = Math.toRadians(player().yaw());
        double aheadX = player().position().x - Math.sin(yaw) * lookAhead.value();
        double aheadZ = player().position().z + Math.cos(yaw) * lookAhead.value();
        if (!mc().world().isSolid((int) Math.floor(aheadX), (int) Math.floor(player().position().y - 1.0),
                (int) Math.floor(aheadZ))) {
            mc().input().setKeyState(MacroManager.KeyMappingKeys.JUMP, true);
            markActivity();
        }
    }

    @Override
    public void onDisable() {
        mc().input().setKeyState(MacroManager.KeyMappingKeys.JUMP, false);
    }
}
