package nexus.core.module.combat;

import nexus.api.IEntity;
import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Draws a circle showing the reach of your attacks. */
public final class AttackRangeDisplayModule extends Module {

    private final NumberSetting range;
    private final ColorSetting color;
    private final BooleanSetting showTargetRing;

    public AttackRangeDisplayModule() {
        super("attackrangedisplay", "AttackRangeDisplay", "Draws a circle that shows your attack reach.",
                ModuleCategory.COMBAT);
        this.range = number("Range", "Attack range to display", 3.0, 1.0, 6.0, 0.1);
        this.color = color("Color", "Circle color", 0x66C084FC);
        this.showTargetRing = bool("Target Ring", "Draw a ring under the current target", true);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        nexus.api.math.Vec3d base = player().position();
        drawCircle(event, base, range.value(), value);
        if (showTargetRing.isEnabled()) {
            IEntity target = mc().crosshairTarget();
            if (target != null) {
                drawCircle(event, target.position(), 0.6, ColorUtil.withAlpha(0xFFF87171, 180));
            }
        }
    }

    private void drawCircle(Render3DEvent event, nexus.api.math.Vec3d center, double radius, int value) {
        int segments = 48;
        for (int i = 0; i < segments; i++) {
            double a1 = (i / (double) segments) * Math.PI * 2.0;
            double a2 = ((i + 1) / (double) segments) * Math.PI * 2.0;
            nexus.api.math.Vec3d p1 = new nexus.api.math.Vec3d(
                    center.x + Math.cos(a1) * radius, center.y + 0.03, center.z + Math.sin(a1) * radius);
            nexus.api.math.Vec3d p2 = new nexus.api.math.Vec3d(
                    center.x + Math.cos(a2) * radius, center.y + 0.03, center.z + Math.sin(a2) * radius);
            event.renderer.drawLine3D(p1, p2, value, 1.5);
        }
    }
}
