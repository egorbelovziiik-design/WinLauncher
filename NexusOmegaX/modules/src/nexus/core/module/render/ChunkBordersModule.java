package nexus.core.module.render;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Draws the borders of loaded chunks. */
public final class ChunkBordersModule extends Module {

    private final ColorSetting color;
    private final NumberSetting radius;
    private final BooleanSetting showHeight;

    public ChunkBordersModule() {
        super("chunkborders", "ChunkBorders", "Draws the borders of loaded chunks.", ModuleCategory.RENDER);
        this.color = color("Color", "Border color", 0xFF60A5FA);
        this.radius = number("Radius", "Chunks around the player", 4.0, 1.0, 16.0, 1.0);
        this.showHeight = bool("Full Height", "Draw the full world height", false);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int chunkX = player().blockX() >> 4;
        int chunkZ = player().blockZ() >> 4;
        int radiusInt = (int) radius.value();
        int value = color.effectiveColor(System.currentTimeMillis());
        int bottom = showHeight.isEnabled() ? mc().world().minHeight() : player().blockY() - 1;
        int top = showHeight.isEnabled() ? mc().world().maxHeight() : player().blockY() + 3;
        for (int cx = chunkX - radiusInt; cx <= chunkX + radiusInt; cx++) {
            for (int cz = chunkZ - radiusInt; cz <= chunkZ + radiusInt; cz++) {
                if (!mc().world().isChunkLoaded(cx, cz)) {
                    continue;
                }
                int startX = cx << 4;
                int startZ = cz << 4;
                for (int corner = 0; corner < 4; corner++) {
                    double x = corner == 0 || corner == 3 ? startX : startX + 16;
                    double z = corner < 2 ? startZ : startZ + 16;
                    event.renderer.drawLine3D(new nexus.api.math.Vec3d(x, bottom, z),
                            new nexus.api.math.Vec3d(x, top, z), value, 1.0);
                }
                event.renderer.drawWorldQuad(new nexus.api.math.Vec3d(startX, bottom, startZ),
                        new nexus.api.math.Vec3d(startX + 16, bottom, startZ),
                        new nexus.api.math.Vec3d(startX + 16, bottom, startZ + 16),
                        new nexus.api.math.Vec3d(startX, bottom, startZ + 16),
                        ColorUtil.withAlpha(value, 25));
            }
        }
    }
}
