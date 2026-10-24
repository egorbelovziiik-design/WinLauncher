package nexus.core.module.render;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Renders your saved waypoints in the world. */
public final class WaypointsModule extends Module {

    private final NumberSetting maxDistance;
    private final BooleanSetting beacons;
    private final BooleanSetting tracers;
    private final BooleanSetting showNames;

    public WaypointsModule() {
        super("waypoints", "Waypoints", "Renders your saved waypoints in the world.", ModuleCategory.RENDER);
        this.maxDistance = number("Max Distance", "Render distance limit", 2048.0, 64.0, 8192.0, 64.0);
        this.beacons = bool("Beacons", "Draw a vertical light beam", true);
        this.tracers = bool("Tracers", "Draw a line to each waypoint", false);
        this.showNames = bool("Names", "Show the waypoint name", true);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        nexus.core.world.WaypointManager manager = nexus.core.Nexus.instance().waypoints();
        for (nexus.core.world.WaypointManager.Waypoint waypoint : manager.all()) {
            if (!waypoint.isEnabled() || !waypoint.dimension().equalsIgnoreCase(mc().world().dimensionId())) {
                continue;
            }
            nexus.api.math.Vec3d position = new nexus.api.math.Vec3d(waypoint.x(), waypoint.y(), waypoint.z());
            double distance = player().position().distanceTo(position);
            if (distance > maxDistance.value()) {
                continue;
            }
            int color = waypoint.color();
            nexus.api.math.Box3 box = new nexus.api.math.Box3(waypoint.x() - 0.35, waypoint.y() - 0.35,
                    waypoint.z() - 0.35, waypoint.x() + 0.35, waypoint.y() + 0.35, waypoint.z() + 0.35);
            event.renderer.drawBox(box, ColorUtil.withAlpha(color, 60), color, true);
            if (beacons.isEnabled()) {
                event.renderer.drawLine3D(new nexus.api.math.Vec3d(waypoint.x(), waypoint.y(), waypoint.z()),
                        new nexus.api.math.Vec3d(waypoint.x(), waypoint.y() + 128.0, waypoint.z()),
                        ColorUtil.withAlpha(color, 70), 1.5);
            }
            if (tracers.isEnabled()) {
                event.renderer.drawTracer(position, color, 1.0);
            }
            if (showNames.isEnabled()) {
                event.renderer.drawNametag(new nexus.api.math.Vec3d(waypoint.x(), waypoint.y() + 1.0, waypoint.z()),
                        waypoint.name() + String.format(" %.0fm", distance), color, 0.9, true,
                        ColorUtil.withAlpha(0xFF08080D, 150));
            }
        }
    }
}
