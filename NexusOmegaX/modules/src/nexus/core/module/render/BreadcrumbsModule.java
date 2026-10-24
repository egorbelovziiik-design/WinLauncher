package nexus.core.module.render;

import nexus.core.event.Render3DEvent;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;

/** Leaves a line behind you so you can find your way back. */
public final class BreadcrumbsModule extends Module {

    private final ColorSetting color;
    private final NumberSetting maxPoints;
    private final NumberSetting interval;
    private final NumberSetting thickness;
    private final List<nexus.api.math.Vec3d> points = new ArrayList<nexus.api.math.Vec3d>(512);
    private final nexus.util.TimeUtil.Timer timer = new nexus.util.TimeUtil.Timer();

    public BreadcrumbsModule() {
        super("breadcrumbs", "Breadcrumbs", "Leaves a trail behind you.", ModuleCategory.RENDER);
        this.color = color("Color", "Trail color", 0xFFC084FC);
        this.maxPoints = number("Max Points", "Maximum stored points", 400.0, 20.0, 4000.0, 10.0);
        this.interval = number("Interval", "Delay between points in ms", 120.0, 20.0, 2000.0, 10.0);
        this.thickness = number("Thickness", "Line thickness", 2.0, 0.5, 6.0, 0.5);
    }

    @Override
    public void onEnable() {
        points.clear();
        timer.reset();
    }

    @Override
    public void onDisable() {
        points.clear();
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed((long) interval.value(), true)) {
            return;
        }
        points.add(player().position().add(0.0, 0.05, 0.0));
        while (points.size() > maxPoints.value()) {
            points.remove(0);
        }
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (points.size() < 2) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        for (int i = 0; i < points.size() - 1; i++) {
            double alpha = (i / (double) points.size()) * 0.8 + 0.2;
            event.renderer.drawLine3D(points.get(i), points.get(i + 1),
                    ColorUtil.withAlpha(value, (int) (255 * alpha)), thickness.value());
        }
    }

    public int points() {
        return points.size();
    }
}
