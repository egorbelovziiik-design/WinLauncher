package nexus.core.module.movement;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Particle trail while you move. */
public final class MovementParticlesModule extends Module {

    private final ModeSetting particle;
    private final NumberSetting rate;
    private final BooleanSetting onlySprinting;

    public MovementParticlesModule() {
        super("movementparticles", "MovementParticles", "Leaves a particle trail behind you while moving.",
                ModuleCategory.MOVEMENT);
        this.particle = mode("Particle", "Particle type", "Cloud", "Cloud", "Flame", "Spark", "EndRod", "Heart");
        this.rate = number("Rate", "Particles per second", 12.0, 1.0, 60.0, 1.0);
        this.onlySprinting = bool("Only Sprinting", "Only emit particles while sprinting", false);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        if (onlySprinting.isEnabled() && !player().isSprinting()) {
            return;
        }
        if (player().horizontalSpeed() < 0.05) {
            return;
        }
        if (Math.random() > rate.value() / 20.0) {
            return;
        }
        String id = "cloud";
        if (particle.is("Flame")) {
            id = "flame";
        } else if (particle.is("Spark")) {
            id = "firework";
        } else if (particle.is("EndRod")) {
            id = "end_rod";
        } else if (particle.is("Heart")) {
            id = "heart";
        }
        nexus.api.math.Vec3d position = player().position();
        mc().resources().spawnParticle(id, position.x, position.y + 0.1, position.z, 0.05, 0.05, 0.05, 1);
    }
}
