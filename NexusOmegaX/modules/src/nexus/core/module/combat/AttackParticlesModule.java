package nexus.core.module.combat;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Continuous particle trail while the attack button is held. */
public final class AttackParticlesModule extends Module {

    private final ModeSetting particle;
    private final NumberSetting rate;
    private final NumberSetting spread;

    public AttackParticlesModule() {
        super("attackparticles", "AttackParticles", "Emits particles from your hand while attacking.",
                ModuleCategory.COMBAT);
        this.particle = mode("Particle", "Particle type", "Crit", "Crit", "Flame", "Spark", "Enchant");
        this.rate = number("Rate", "Particles per second", 20.0, 1.0, 80.0, 1.0);
        this.spread = number("Spread", "Particle spread", 0.15, 0.0, 1.0, 0.05);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !mc().input().isMouseDown(0)) {
            return;
        }
        if (Math.random() > rate.value() / 20.0) {
            return;
        }
        String id = particle.is("Flame") ? "flame" : (particle.is("Spark") ? "firework" : "crit");
        nexus.api.math.Vec3d eye = player().eyePosition();
        double yaw = Math.toRadians(player().yaw() + 90.0);
        double pitch = Math.toRadians(player().pitch());
        nexus.api.math.Vec3d direction = new nexus.api.math.Vec3d(
                -Math.cos(pitch) * Math.cos(yaw), -Math.sin(pitch), -Math.cos(pitch) * Math.sin(yaw));
        nexus.api.math.Vec3d point = eye.add(direction.multiply(1.2));
        mc().resources().spawnParticle(id, point.x, point.y, point.z,
                spread.value(), spread.value(), spread.value(), 1);
    }
}
