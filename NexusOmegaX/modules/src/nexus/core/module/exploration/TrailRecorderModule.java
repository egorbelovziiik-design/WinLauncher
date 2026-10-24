package nexus.core.module.exploration;

import nexus.core.event.Render3DEvent;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;

/** Records the path you walk so you can replay or review it later. */
public final class TrailRecorderModule extends Module {

    private final NumberSetting interval;
    private final NumberSetting maxPoints;
    private final ColorSetting color;
    private final BooleanSetting showWhileRecording;
    private final List<double[]> path = new ArrayList<double[]>();
    private final nexus.util.TimeUtil.Timer timer = new nexus.util.TimeUtil.Timer();

    public TrailRecorderModule() {
        super("trailrecorder", "TrailRecorder", "Records the path you walk.", ModuleCategory.EXPLORATION);
        this.interval = number("Interval", "Delay between points in ms", 500.0, 50.0, 10000.0, 50.0);
        this.maxPoints = number("Max Points", "Maximum stored points", 2000.0, 50.0, 20000.0, 50.0);
        this.color = color("Color", "Path color", 0xFF4ADE80);
        this.showWhileRecording = bool("Preview", "Draw the path while recording", true);
    }

    @Override
    public void onEnable() {
        path.clear();
        timer.reset();
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed((long) interval.value(), true)) {
            return;
        }
        path.add(new double[]{player().position().x, player().position().y, player().position().z});
        while (path.size() > maxPoints.value()) {
            path.remove(0);
        }
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!showWhileRecording.isEnabled() || path.size() < 2) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        for (int i = 0; i < path.size() - 1; i++) {
            double[] a = path.get(i);
            double[] b = path.get(i + 1);
            event.renderer.drawLine3D(new nexus.api.math.Vec3d(a[0], a[1] + 0.1, a[2]),
                    new nexus.api.math.Vec3d(b[0], b[1] + 0.1, b[2]),
                    ColorUtil.withAlpha(value, 200), 2.0);
        }
    }

    public int points() {
        return path.size();
    }

    public void clear() {
        path.clear();
    }

    public List<double[]> path() {
        return path;
    }

    @Override
    public String hudInfo() {
        return path.size() + " points";
    }
}
