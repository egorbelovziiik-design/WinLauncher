package nexus.core.module.movement;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Local movement speed boost (server authoritative: only works where allowed). */
public final class SpeedLocalModule extends Module {

    private final ModeSetting mode;
    private final NumberSetting multiplier;
    private final BooleanSetting onlyForward;

    public SpeedLocalModule() {
        super("speedlocal", "SpeedLocal", "Boosts movement speed. Requires a server that allows it.",
                ModuleCategory.MOVEMENT);
        this.mode = mode("Mode", "Boost style", "Velocity", "Velocity", "Strafe", "Sprint");
        this.multiplier = number("Multiplier", "Speed multiplier", 1.4, 1.0, 4.0, 0.05);
        this.onlyForward = bool("Only Forward", "Only boost while walking forward", true);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        if (onlyForward.isEnabled() && !mc().input().isKeyDown(87)) {
            return;
        }
        if (mode.is("Sprint")) {
            player().setSprinting(true);
            return;
        }
        double factor = multiplier.value();
        if (player().horizontalSpeed() < 0.01) {
            return;
        }
        nexus.api.math.Vec3d velocity = player().velocity();
        double boost = mode.is("Strafe") ? factor * 0.85 : factor;
        player().setVelocity(new nexus.api.math.Vec3d(velocity.x * boost, velocity.y, velocity.z * boost));
        markActivity();
    }

    @Override
    public String hudInfo() {
        return "x" + nexus.util.MathUtil.round(multiplier.value(), 2);
    }
}
