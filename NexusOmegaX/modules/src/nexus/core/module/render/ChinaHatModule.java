package nexus.core.module.render;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Draws a decorative cone above players. */
public final class ChinaHatModule extends Module {

    private final ColorSetting color;
    private final NumberSetting size;
    private final BooleanSetting onlySelf;

    public ChinaHatModule() {
        super("chinahat", "ChinaHat", "Draws a decorative cone above players.", ModuleCategory.RENDER);
        this.color = color("Color", "Cone color", 0xFFC084FC);
        this.size = number("Size", "Cone radius", 0.6, 0.2, 2.0, 0.05);
        this.onlySelf = bool("Only Self", "Only draw it on yourself", false);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        if (onlySelf.isEnabled()) {
            drawCone(event, player().position(), value);
            return;
        }
        for (nexus.api.IEntity entity : mc().world().entities()) {
            if (!entity.isPlayer() || !entity.isAlive()) {
                continue;
            }
            drawCone(event, entity.position(), value);
        }
    }

    private void drawCone(Render3DEvent event, nexus.api.math.Vec3d base, int color) {
        nexus.api.math.Vec3d top = base.add(0.0, 2.1, 0.0);
        nexus.api.math.Vec3d ring = base.add(0.0, 1.5, 0.0);
        int segments = 20;
        for (int i = 0; i < segments; i++) {
            double a1 = (i / (double) segments) * Math.PI * 2.0;
            double a2 = ((i + 1) / (double) segments) * Math.PI * 2.0;
            nexus.api.math.Vec3d p1 = new nexus.api.math.Vec3d(ring.x + Math.cos(a1) * size.value(),
                    ring.y, ring.z + Math.sin(a1) * size.value());
            nexus.api.math.Vec3d p2 = new nexus.api.math.Vec3d(ring.x + Math.cos(a2) * size.value(),
                    ring.y, ring.z + Math.sin(a2) * size.value());
            event.renderer.drawLine3D(p1, p2, color, 1.5);
            event.renderer.drawLine3D(p1, top, ColorUtil.withAlpha(color, 130), 1.0);
        }
    }
}
