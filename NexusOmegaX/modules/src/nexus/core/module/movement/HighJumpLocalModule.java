package nexus.core.module.movement;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import nexus.core.automation.MacroManager;

/** Higher jumps (requires a permissive server or a singleplayer world). */
public final class HighJumpLocalModule extends Module {

    private final NumberSetting height;
    private final BooleanSetting requireKey;
    private boolean triggered;

    public HighJumpLocalModule() {
        super("highjumplocal", "HighJumpLocal", "Jump higher. Requires a world where the server allows it.",
                ModuleCategory.MOVEMENT);
        this.height = number("Height", "Extra jump velocity", 0.35, 0.05, 2.0, 0.05);
        this.requireKey = bool("Require Jump Key", "Only boost when the jump key is held", true);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        boolean wantsJump = !requireKey.isEnabled() || mc().input().isKeyDown(nexus.api.KeyCodes.SPACE)
                || mc().input().isKeyDown(MacroManager.KeyMappingKeys.JUMP);
        if (!wantsJump) {
            triggered = false;
            return;
        }
        if (player().isOnGround() && player().velocity().y > 0.0 && !triggered) {
            player().setVelocity(player().velocity().add(0.0, height.value(), 0.0));
            triggered = true;
            markActivity();
        }
        if (player().isOnGround() && player().velocity().y <= 0.0) {
            triggered = false;
        }
    }

    @Override
    public String hudInfo() {
        return "+" + nexus.util.MathUtil.round(height.value(), 2);
    }
}
