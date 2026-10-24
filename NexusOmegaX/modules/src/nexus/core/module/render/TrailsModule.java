package nexus.core.module.render;

import nexus.api.IEntity;
import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.util.HashMap;
import java.util.Map;

/** Draws a fading trail behind moving entities. */
public final class TrailsModule extends Module {

    private final ColorSetting color;
    private final NumberSetting length;
    private final NumberSetting thickness;
    private final BooleanSetting playersOnly;
    private final Map<Integer, nexus.api.math.Vec3d[]> history = new HashMap<Integer, nexus.api.math.Vec3d[]>();

    public TrailsModule() {
        super("trails", "Trails", "Draws a fading trail behind moving entities.", ModuleCategory.RENDER);
        this.color = color("Color", "Trail color", 0xFFC084FC);
        this.length = number("Length", "Number of stored positions", 24.0, 4.0, 96.0, 1.0);
        this.thickness = number("Thickness", "Line thickness", 2.0, 0.5, 6.0, 0.5);
        this.playersOnly = bool("Players Only", "Only draw trails for players", true);
    }

    @Override
    public void onEnable() {
        history.clear();
    }

    @Override
    public void onDisable() {
        history.clear();
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int max = (int) length.value();
        int value = color.effectiveColor(System.currentTimeMillis());
        for (IEntity entity : mc().world().entities()) {
            if (entity.isLocalPlayer() || !entity.isAlive()) {
                continue;
            }
            if (playersOnly.isEnabled() && !entity.isPlayer()) {
                continue;
            }
            Integer key = Integer.valueOf(entity.id());
            nexus.api.math.Vec3d[] points = history.get(key);
            if (points == null || points.length != max) {
                points = new nexus.api.math.Vec3d[max];
                history.put(key, points);
            }
            System.arraycopy(points, 1, points, 0, max - 1);
            points[max - 1] = entity.position().add(0.0, entity.height() * 0.5, 0.0);
            for (int i = 0; i < max - 1; i++) {
                if (points[i] == null || points[i + 1] == null) {
                    continue;
                }
                double alpha = (i / (double) max);
                event.renderer.drawLine3D(points[i], points[i + 1],
                        (int) (255 * alpha) << 24 | (value & 0x00FFFFFF), thickness.value() * alpha);
            }
        }
    }
}
