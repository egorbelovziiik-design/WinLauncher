package nexus.core.module.movement;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Improves water movement: dolphin style boosts and no sinking while idle. */
public final class WaterMovementModule extends Module {

    private final BooleanSetting noSink;
    private final NumberSetting boost;
    private final BooleanSetting onlySwimming;

    public WaterMovementModule() {
        super("watermovement", "WaterMovement", "Better control and speed while swimming.",
                ModuleCategory.MOVEMENT);
        this.noSink = bool("No Sink", "Stop sinking when no key is pressed", true);
        this.boost = number("Boost", "Swim speed boost", 1.2, 1.0, 3.0, 0.05);
        this.onlySwimming = bool("Only While Swimming", "Only boost while moving", true);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !player().isInWater()) {
            return;
        }
        if (noSink.isEnabled() && player().velocity().y < -0.05 && !mc().input().isKeyDown(341)) {
            player().setVelocity(new nexus.api.math.Vec3d(player().velocity().x, -0.02, player().velocity().z));
        }
        if (onlySwimming.isEnabled() && player().horizontalSpeed() < 0.02) {
            return;
        }
        nexus.api.math.Vec3d velocity = player().velocity();
        player().setVelocity(new nexus.api.math.Vec3d(velocity.x * boost.value(), velocity.y, velocity.z * boost.value()));
    }
}
