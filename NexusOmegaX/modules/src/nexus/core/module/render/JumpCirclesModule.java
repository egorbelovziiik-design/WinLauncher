package nexus.core.module.render;

import nexus.core.event.Render3DEvent;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Draws an expanding circle when you jump or land. */
public final class JumpCirclesModule extends Module {

    private final ColorSetting color;
    private final NumberSetting duration;
    private final NumberSetting maxRadius;
    private long triggerTime = -1L;
    private nexus.api.math.Vec3d position;
    private boolean wasGrounded = true;

    public JumpCirclesModule() {
        super("jumpcircles", "JumpCircles", "Draws an expanding circle when you jump or land.",
                ModuleCategory.RENDER);
        this.color = color("Color", "Circle color", 0xFFC084FC);
        this.duration = number("Duration", "Effect duration in ms", 600.0, 100.0, 3000.0, 50.0);
        this.maxRadius = number("Max Radius", "Maximum radius", 1.2, 0.2, 4.0, 0.1);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        boolean grounded = player().isOnGround();
        if (wasGrounded != grounded) {
            triggerTime = System.currentTimeMillis();
            position = player().position();
        }
        wasGrounded = grounded;
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (triggerTime < 0L || position == null) {
            return;
        }
        double progress = (System.currentTimeMillis() - triggerTime) / duration.value();
        if (progress >= 1.0) {
            triggerTime = -1L;
            return;
        }
        double radius = maxRadius.value() * progress;
        int alpha = (int) (220.0 * (1.0 - progress));
        int value = ColorUtil.withAlpha(color.effectiveColor(System.currentTimeMillis()), alpha);
        int segments = 32;
        for (int i = 0; i < segments; i++) {
            double a1 = (i / (double) segments) * Math.PI * 2.0;
            double a2 = ((i + 1) / (double) segments) * Math.PI * 2.0;
            nexus.api.math.Vec3d p1 = new nexus.api.math.Vec3d(position.x + Math.cos(a1) * radius,
                    position.y + 0.02, position.z + Math.sin(a1) * radius);
            nexus.api.math.Vec3d p2 = new nexus.api.math.Vec3d(position.x + Math.cos(a2) * radius,
                    position.y + 0.02, position.z + Math.sin(a2) * radius);
            event.renderer.drawLine3D(p1, p2, value, 2.0);
        }
    }
}
