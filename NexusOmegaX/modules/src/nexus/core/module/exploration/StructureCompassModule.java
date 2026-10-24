package nexus.core.module.exploration;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;
import nexus.util.MathUtil;

/** Points towards the nearest waypoint or structure. */
public final class StructureCompassModule extends Module {

    private final StringSetting target;
    private final NumberSetting size;
    private final NumberSetting y;
    private final BooleanSetting showDistance;

    public StructureCompassModule() {
        super("structurecompass", "StructureCompass", "Points towards a waypoint or structure.",
                ModuleCategory.EXPLORATION);
        this.target = text("Target", "Waypoint name to point at", "base");
        this.size = number("Size", "Arrow size", 12.0, 4.0, 64.0, 1.0);
        this.y = number("Y", "Vertical position", 60.0, 0.0, 1080.0, 1.0);
        this.showDistance = bool("Distance", "Show the distance", true);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        nexus.core.world.WaypointManager.Waypoint point =
                nexus.core.Nexus.instance().waypoints().byName(target.rawValue());
        if (point == null) {
            return;
        }
        int accent = nexus.core.Nexus.instance().themes().accent(System.currentTimeMillis());
        double dx = point.x() - player().position().x;
        double dz = point.z() - player().position().z;
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double delta = MathUtil.wrapDegrees((float) targetYaw - player().yaw());
        double centerX = event.width / 2.0;
        double centerY = y.value();
        event.renderer.pushScissor(0.0, 0.0, event.width, event.height);
        double rad = Math.toRadians(delta);
        double sizeValue = size.value();
        double tipX = centerX + Math.sin(rad) * sizeValue;
        double tipY = centerY - Math.cos(rad) * sizeValue;
        double leftX = centerX + Math.sin(rad + 2.4) * sizeValue * 0.7;
        double leftY = centerY - Math.cos(rad + 2.4) * sizeValue * 0.7;
        double rightX = centerX + Math.sin(rad - 2.4) * sizeValue * 0.7;
        double rightY = centerY - Math.cos(rad - 2.4) * sizeValue * 0.7;
        event.renderer.triangle(tipX, tipY, leftX, leftY, rightX, rightY, accent);
        event.renderer.popScissor();
        if (showDistance.isEnabled()) {
            double distance = Math.sqrt(dx * dx + dz * dz);
            event.renderer.drawCenteredText(point.name() + " " + (int) distance + "m",
                    centerX, centerY + sizeValue + 6.0, ColorUtil.withAlpha(0xFFF5F3FF, 220), true, 0.9);
        }
    }
}
