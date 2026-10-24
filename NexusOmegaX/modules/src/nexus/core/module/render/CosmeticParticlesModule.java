package nexus.core.module.render;

import nexus.api.math.Vec3d;
import nexus.core.event.Render3DEvent;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;

/** Purely cosmetic particle effects around your player. */
public final class CosmeticParticlesModule extends Module {

    private final ColorSetting color;
    private final NumberSetting count;
    private final NumberSetting radius;
    private final ModeSetting pattern;
    private final List<Particle> particles = new ArrayList<Particle>(128);
    private double angle;

    private static final class Particle {
        double x;
        double y;
        double z;
        int life;
        int color;

        Particle(double x, double y, double z, int life, int color) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.life = life;
            this.color = color;
        }
    }

    public CosmeticParticlesModule() {
        super("cosmeticparticles", "CosmeticParticles", "Cosmetic particle effects around your player.",
                ModuleCategory.RENDER);
        this.color = color("Color", "Particle color", 0xFFC084FC);
        this.count = number("Count", "Particles per tick", 2.0, 1.0, 12.0, 1.0);
        this.radius = number("Radius", "Spawn radius", 0.8, 0.2, 3.0, 0.1);
        this.pattern = mode("Pattern", "Spawn pattern", "Orbit", "Orbit", "Rise", "Burst");
    }

    @Override
    public void onEnable() {
        particles.clear();
    }

    @Override
    public void onDisable() {
        particles.clear();
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        Vec3d base = player().position();
        int value = color.effectiveColor(System.currentTimeMillis());
        for (int i = 0; i < count.value(); i++) {
            if (pattern.is("Rise")) {
                particles.add(new Particle(base.x + (Math.random() - 0.5) * radius.value() * 2.0,
                        base.y + 0.1, base.z + (Math.random() - 0.5) * radius.value() * 2.0, 40, value));
            } else {
                angle += 0.2;
                particles.add(new Particle(base.x, base.y + 1.0, base.z, 40, value));
            }
        }
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle particle = particles.get(i);
            particle.life--;
            if (pattern.is("Rise")) {
                particle.y += 0.05;
            }
            if (particle.life <= 0) {
                particles.remove(i);
            }
        }
        if (particles.size() > 300) {
            particles.subList(0, particles.size() - 300).clear();
        }
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        Vec3d base = player().position().add(0.0, 1.0, 0.0);
        for (int i = 0; i < particles.size(); i++) {
            Particle particle = particles.get(i);
            double alpha = particle.life / 40.0;
            if (pattern.is("Orbit")) {
                double localAngle = angle + i * (Math.PI * 2.0 / Math.max(1, particles.size()));
                Vec3d position = new Vec3d(base.x + Math.cos(localAngle) * radius.value(),
                        base.y + Math.sin(i * 0.1) * 0.4,
                        base.z + Math.sin(localAngle) * radius.value());
                event.renderer.drawWorldPoint(position, 0.05,
                        (int) (alpha * 255) << 24 | (particle.color & 0x00FFFFFF));
            } else {
                event.renderer.drawWorldPoint(new Vec3d(particle.x, particle.y, particle.z), 0.05,
                        ColorUtil.withAlpha(particle.color, (int) (alpha * 255)));
            }
        }
    }
}
