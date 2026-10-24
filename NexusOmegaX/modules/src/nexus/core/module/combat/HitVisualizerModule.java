package nexus.core.module.combat;

import nexus.api.IEntity;
import nexus.core.event.AttackEvent;
import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;
import nexus.util.MathUtil;

/** Draws an expanding sphere at the exact impact point of your hits. */
public final class HitVisualizerModule extends Module {

    private final ColorSetting color;
    private final NumberSetting duration;
    private final NumberSetting maxRadius;

    private nexus.api.math.Vec3d impact;
    private long hitTime = -1L;

    public HitVisualizerModule() {
        super("hitvisualizer", "HitVisualizer", "Visualizes the impact point of your attacks with an expanding sphere.",
                ModuleCategory.COMBAT);
        this.color = color("Color", "Sphere color", 0xFFC084FC);
        this.duration = number("Duration", "Effect duration in ms", 420.0, 100.0, 2000.0, 50.0);
        this.maxRadius = number("Max Radius", "Maximum sphere radius", 0.9, 0.2, 3.0, 0.1);
    }

    @Override
    public void onAttack(AttackEvent event) {
        IEntity target = event.target;
        if (target == null) {
            return;
        }
        impact = target.position().add(0.0, target.height() * 0.55, 0.0);
        hitTime = System.currentTimeMillis();
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (impact == null || hitTime < 0L) {
            return;
        }
        double progress = (System.currentTimeMillis() - hitTime) / duration.value();
        if (progress >= 1.0) {
            impact = null;
            return;
        }
        double radius = maxRadius.value() * MathUtil.clamp(progress, 0.0, 1.0);
        int alpha = (int) (200.0 * (1.0 - progress));
        int value = ColorUtil.withAlpha(color.effectiveColor(System.currentTimeMillis()), alpha);
        int segments = 16;
        for (int ring = 0; ring < 3; ring++) {
            double ringRadius = radius * (0.5 + ring * 0.25);
            double angleOffset = ring * (Math.PI / 3.0);
            for (int i = 0; i < segments; i++) {
                double a1 = (i / (double) segments) * Math.PI * 2.0 + angleOffset;
                double a2 = ((i + 1) / (double) segments) * Math.PI * 2.0 + angleOffset;
                nexus.api.math.Vec3d p1 = new nexus.api.math.Vec3d(
                        impact.x + Math.cos(a1) * ringRadius, impact.y + Math.sin(a1) * ringRadius, impact.z);
                nexus.api.math.Vec3d p2 = new nexus.api.math.Vec3d(
                        impact.x + Math.cos(a2) * ringRadius, impact.y + Math.sin(a2) * ringRadius, impact.z);
                event.renderer.drawLine3D(p1, p2, value, 1.5);
            }
        }
    }
}
