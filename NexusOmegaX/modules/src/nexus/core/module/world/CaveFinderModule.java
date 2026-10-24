package nexus.core.module.world;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Highlights air pockets and caves below you. */
public final class CaveFinderModule extends Module {

    private final ColorSetting color;
    private final NumberSetting radius;
    private final NumberSetting depth;
    private final BooleanSetting tracers;

    public CaveFinderModule() {
        super("cavefinder", "CaveFinder", "Highlights caves and air pockets around you.",
                ModuleCategory.WORLD);
        this.color = color("Color", "Cave color", 0xFF4ADE80);
        this.radius = number("Radius", "Horizontal radius", 24.0, 4.0, 64.0, 1.0);
        this.depth = number("Depth", "Vertical depth", 32.0, 4.0, 128.0, 1.0);
        this.tracers = bool("Tracers", "Draw lines to caves", false);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        int playerX = player().blockX();
        int playerY = player().blockY();
        int playerZ = player().blockZ();
        int horizontal = (int) radius.value();
        int vertical = (int) depth.value();
        for (int dx = -horizontal; dx <= horizontal; dx += 2) {
            for (int dz = -horizontal; dz <= horizontal; dz += 2) {
                for (int dy = -vertical; dy < 0; dy += 2) {
                    int x = playerX + dx;
                    int y = playerY + dy;
                    int z = playerZ + dz;
                    if (y < mc().world().minHeight() || !mc().world().isAir(x, y, z)) {
                        continue;
                    }
                    if (!mc().world().isSolid(x, y + 1, z) && !mc().world().isSolid(x, y - 1, z)) {
                        continue;
                    }
                    nexus.api.math.Box3 box = nexus.api.math.Box3.block(x, y, z);
                    event.renderer.drawBox(box, ColorUtil.withAlpha(value, 45), value, true);
                    if (tracers.isEnabled()) {
                        event.renderer.drawTracer(new nexus.api.math.Vec3d(x + 0.5, y + 0.5, z + 0.5),
                                value, 1.0);
                    }
                }
            }
        }
    }
}
