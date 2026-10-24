package nexus.core.module.combat;

import nexus.api.IEntity;
import nexus.core.event.Render3DEvent;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.MathUtil;

/** Practice assistant: keeps a consistent distance and strafes around the target. */
public final class TrainingBotModule extends Module {

    private final NumberSetting keepDistance;
    private final BooleanSetting strafe;
    private final NumberSetting strafeSpeed;
    private final BooleanSetting autoHit;
    private boolean direction = true;

    public TrainingBotModule() {
        super("trainingbot", "TrainingBot", "Practice helper that keeps distance and strafes around a target.",
                ModuleCategory.COMBAT);
        this.keepDistance = number("Distance", "Distance to keep from the target", 3.0, 1.0, 6.0, 0.1);
        this.strafe = bool("Strafe", "Strafe around the target", true);
        this.strafeSpeed = number("Strafe Speed", "Strafe movement speed", 1.0, 0.2, 3.0, 0.1);
        this.autoHit = bool("Auto Hit", "Attack when the target is in range", false);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        IEntity target = mc().crosshairTarget();
        if (target == null && !mc().world().entities().isEmpty()) {
            target = nexus.core.util.TargetUtil.bestTarget(8.0, true, true, true, true, 180.0);
        }
        if (target == null) {
            return;
        }
        double distance = target.distanceToPlayer();
        player().lookAt(target.eyePosition());
        double forward = distance > keepDistance.value() + 0.3 ? 1.0
                : (distance < keepDistance.value() - 0.3 ? -1.0 : 0.0);
        mc().input().setKeyState(MovementKeys.FORWARD, forward > 0.0);
        mc().input().setKeyState(MovementKeys.BACK, forward < 0.0);
        if (strafe.isEnabled()) {
            if (Math.random() < 0.01 * strafeSpeed.value()) {
                direction = !direction;
            }
            mc().input().setKeyState(MovementKeys.LEFT, direction);
            mc().input().setKeyState(MovementKeys.RIGHT, !direction);
        }
        if (autoHit.isEnabled() && distance <= 3.6 && player().attackCooldown() > 0.95) {
            mc().interaction().attackEntity(target);
            player().swingHand();
        }
        markActivity();
    }

    @Override
    public void onDisable() {
        mc().input().releaseAllKeys();
    }

    /** Movement key ids resolved by the adapter (see MacroManager.KeyMappingKeys). */
    private static final class MovementKeys {
        private static final int FORWARD = nexus.core.automation.MacroManager.KeyMappingKeys.FORWARD;
        private static final int BACK = nexus.core.automation.MacroManager.KeyMappingKeys.BACK;
        private static final int LEFT = nexus.core.automation.MacroManager.KeyMappingKeys.LEFT;
        private static final int RIGHT = nexus.core.automation.MacroManager.KeyMappingKeys.RIGHT;
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        // no 3D pass needed
    }

    @Override
    public String hudInfo() {
        return MathUtil.round(keepDistance.value(), 1) + "m";
    }
}
