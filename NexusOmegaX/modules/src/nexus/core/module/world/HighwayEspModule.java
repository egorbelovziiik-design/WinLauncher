package nexus.core.module.world;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Highlights long flat tunnels (nether highways) along an axis. */
public final class HighwayEspModule extends Module {

    private final ColorSetting color;
    private final NumberSetting distance;
    private final ModeSetting axis;

    public HighwayEspModule() {
        super("highwayesp", "HighwayESP", "Highlights long tunnels along an axis.", ModuleCategory.WORLD);
        this.color = color("Color", "Highway color", 0xFF22D3EE);
        this.distance = number("Distance", "Scan distance in blocks", 128.0, 16.0, 1024.0, 16.0);
        this.axis = mode("Axis", "Axis to scan", "X", "X", "Z", "Both");
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        int distanceInt = (int) distance.value();
        int baseX = player().blockX();
        int baseY = player().blockY();
        int baseZ = player().blockZ();
        if (axis.is("X") || axis.is("Both")) {
            scan(event, baseX, baseY, baseZ, distanceInt, value, true);
        }
        if (axis.is("Z") || axis.is("Both")) {
            scan(event, baseX, baseY, baseZ, distanceInt, value, false);
        }
    }

    private void scan(Render3DEvent event, int baseX, int baseY, int baseZ, int length, int color, boolean alongX) {
        for (int offset = -length; offset <= length; offset++) {
            int x = alongX ? baseX + offset : baseX;
            int z = alongX ? baseZ : baseZ + offset;
            for (int dy = -6; dy <= 6; dy++) {
                int y = baseY + dy;
                if (y < mc().world().minHeight() || y > mc().world().maxHeight()) {
                    continue;
                }
                if (mc().world().isSolid(x, y, z) || !mc().world().isSolid(x, y + 3, z)) {
                    continue;
                }
                nexus.api.math.Box3 box = nexus.api.math.Box3.block(x, y + 1, z);
                event.renderer.drawBox(box, ColorUtil.withAlpha(color, 30), ColorUtil.withAlpha(color, 120), true);
                break;
            }
        }
    }

    @Override
    public String hudInfo() {
        return axis.get();
    }
}
