package nexus.core.module.combat;

import nexus.core.event.AttackEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Spawns extra particles when you hit an entity. */
public final class HitParticlesModule extends Module {

    private final ModeSetting particle;
    private final NumberSetting count;
    private final BooleanSetting criticalOnly;

    public HitParticlesModule() {
        super("hitparticles", "HitParticles", "Spawns custom particles at the impact point of your attacks.",
                ModuleCategory.COMBAT);
        this.particle = mode("Particle", "Particle type", "Crit", "Crit", "Flame", "Smoke", "Enchant", "EndRod");
        this.count = number("Count", "Particles per hit", 12.0, 1.0, 60.0, 1.0);
        this.criticalOnly = bool("Critical Only", "Only spawn particles on critical hits", false);
    }

    @Override
    public void onAttack(AttackEvent event) {
        if (event.target == null) {
            return;
        }
        if (criticalOnly.isEnabled() && player().attackCooldown() < 0.9) {
            return;
        }
        String particleId = "crit";
        if (particle.is("Flame")) {
            particleId = "flame";
        } else if (particle.is("Smoke")) {
            particleId = "smoke";
        } else if (particle.is("Enchant")) {
            particleId = "enchant";
        } else if (particle.is("EndRod")) {
            particleId = "end_rod";
        }
        nexus.api.math.Vec3d position = event.target.position().add(0.0, event.target.height() * 0.6, 0.0);
        mc().resources().spawnParticle(particleId, position.x, position.y, position.z,
                0.25, 0.35, 0.25, (int) count.value());
        markActivity();
    }
}
