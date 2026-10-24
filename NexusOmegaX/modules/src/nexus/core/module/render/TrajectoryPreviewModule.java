package nexus.core.module.render;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Predicts the flight path of arrows, pearls and throwables. */
public final class TrajectoryPreviewModule extends Module {

    private final ColorSetting color;
    private final NumberSetting steps;
    private final NumberSetting thickness;
    private final BooleanSetting showLanding;

    public TrajectoryPreviewModule() {
        super("trajectorypreview", "TrajectoryPreview", "Shows where your projectiles will land.",
                ModuleCategory.RENDER);
        this.color = color("Color", "Path color", 0xFF4ADE80);
        this.steps = number("Steps", "Simulation steps", 60.0, 10.0, 200.0, 5.0);
        this.thickness = number("Thickness", "Line thickness", 1.5, 0.5, 5.0, 0.5);
        this.showLanding = bool("Show Landing", "Mark the landing point", true);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        String held = player().inventory().heldItem().id();
        boolean throwable = held.contains("bow") || held.contains("pearl") || held.contains("snowball")
                || held.contains("egg") || held.contains("trident") || held.contains("potion");
        if (!throwable) {
            return;
        }
        double gravity = held.contains("bow") ? 0.05 : 0.03;
        double speed = held.contains("bow") ? 3.0 : 1.5;
        double yaw = Math.toRadians(player().yaw());
        double pitch = Math.toRadians(player().pitch());
        nexus.api.math.Vec3d position = player().eyePosition();
        nexus.api.math.Vec3d velocity = new nexus.api.math.Vec3d(
                -Math.cos(pitch) * Math.sin(yaw) * speed,
                -Math.sin(pitch) * speed,
                Math.cos(pitch) * Math.cos(yaw) * speed);
        int value = color.effectiveColor(System.currentTimeMillis());
        nexus.api.math.Vec3d previous = position;
        for (int i = 0; i < (int) steps.value(); i++) {
            nexus.api.math.Vec3d next = position.add(velocity);
            velocity = new nexus.api.math.Vec3d(velocity.x * 0.99, velocity.y - gravity, velocity.z * 0.99);
            if (mc().world().isSolid((int) Math.floor(next.x), (int) Math.floor(next.y), (int) Math.floor(next.z))) {
                if (showLanding.isEnabled()) {
                    nexus.api.math.Box3 box = nexus.api.math.Box3.block((int) Math.floor(next.x),
                            (int) Math.floor(next.y), (int) Math.floor(next.z));
                    event.renderer.drawBox(box, ColorUtil.withAlpha(value, 60), value, true);
                }
                break;
            }
            event.renderer.drawLine3D(previous, next, value, thickness.value());
            previous = next;
            position = next;
        }
    }
}
