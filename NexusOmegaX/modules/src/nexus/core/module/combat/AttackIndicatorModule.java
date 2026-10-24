package nexus.core.module.combat;

import nexus.api.IEntity;
import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Draws a ring around every entity that is about to be hit. */
public final class AttackIndicatorModule extends Module {

    private final ColorSetting color;
    private final NumberSetting range;
    private final NumberSetting radius;
    private final BooleanSetting onlyCrosshair;

    public AttackIndicatorModule() {
        super("attackindicator", "AttackIndicator", "Shows which entity is inside your attack range.",
                ModuleCategory.COMBAT);
        this.color = color("Color", "Indicator color", 0xFFF87171);
        this.range = number("Range", "Attack range", 3.6, 1.0, 6.0, 0.1);
        this.radius = number("Radius", "Ring radius", 0.7, 0.2, 2.0, 0.05);
        this.onlyCrosshair = bool("Only Target", "Only mark the entity under the crosshair", false);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        for (IEntity entity : mc().world().entities()) {
            if (entity.isLocalPlayer() || !entity.isAlive() || !entity.isAttackable()) {
                continue;
            }
            if (onlyCrosshair.isEnabled() && entity != mc().crosshairTarget()) {
                continue;
            }
            if (entity.distanceToPlayer() > range.value()) {
                continue;
            }
            nexus.api.math.Vec3d base = entity.position();
            double r = radius.value();
            int segments = 24;
            for (int i = 0; i < segments; i++) {
                double a1 = (i / (double) segments) * Math.PI * 2.0;
                double a2 = ((i + 1) / (double) segments) * Math.PI * 2.0;
                nexus.api.math.Vec3d p1 = new nexus.api.math.Vec3d(base.x + Math.cos(a1) * r, base.y + 0.05, base.z + Math.sin(a1) * r);
                nexus.api.math.Vec3d p2 = new nexus.api.math.Vec3d(base.x + Math.cos(a2) * r, base.y + 0.05, base.z + Math.sin(a2) * r);
                event.renderer.drawLine3D(p1, p2, value, 1.5);
            }
        }
    }
}
