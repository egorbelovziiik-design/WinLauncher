package nexus.core.module.render;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;
import nexus.util.MathUtil;

/** Square minimap with terrain colors sampled from the world. */
public final class MiniMapModule extends Module {

    private final NumberSetting size;
    private final NumberSetting blocks;
    private final BooleanSetting rotate;
    private final NumberSetting positionX;
    private final NumberSetting positionY;

    public MiniMapModule() {
        super("minimap", "MiniMap", "Draws a small map of the surrounding terrain.", ModuleCategory.RENDER);
        this.size = number("Size", "Map size in pixels", 96.0, 48.0, 256.0, 1.0);
        this.blocks = number("Blocks", "Blocks covered", 64.0, 16.0, 256.0, 8.0);
        this.rotate = bool("Rotate", "Rotate with your view", false);
        this.positionX = number("X", "Horizontal position", 8.0, 0.0, 1920.0, 1.0);
        this.positionY = number("Y", "Vertical position", 120.0, 0.0, 1080.0, 1.0);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        double size = this.size.value();
        double x = MathUtil.clamp(positionX.value(), 0.0, Math.max(0.0, event.width - size));
        double y = MathUtil.clamp(positionY.value(), 0.0, Math.max(0.0, event.height - size));
        event.renderer.fillRoundRect(x, y, size, size, 6.0, ColorUtil.withAlpha(0xFF08080D, 180));
        event.renderer.outlineRoundRect(x, y, size, size, 6.0, 1.0,
                ColorUtil.withAlpha(nexus.core.Nexus.instance().themes().accent(System.currentTimeMillis()), 140));
        event.renderer.pushScissor(x + 1.0, y + 1.0, size - 2.0, size - 2.0);
        int resolution = 24;
        double scale = size / resolution;
        double blocksPerPixel = blocks.value() / resolution;
        int playerX = player().blockX();
        int playerZ = player().blockZ();
        float yaw = rotate.isEnabled() ? player().yaw() : 0.0f;
        double sin = Math.sin(Math.toRadians(yaw));
        double cos = Math.cos(Math.toRadians(yaw));
        for (int ix = 0; ix < resolution; ix++) {
            for (int iz = 0; iz < resolution; iz++) {
                double localX = (ix - resolution / 2.0) * blocksPerPixel;
                double localZ = (iz - resolution / 2.0) * blocksPerPixel;
                int worldX = playerX + (int) (localX * cos - localZ * sin);
                int worldZ = playerZ + (int) (localX * sin + localZ * cos);
                int height = surfaceHeight(worldX, worldZ);
                event.renderer.fillRect(x + ix * scale, y + iz * scale, scale, scale, terrainColor(worldX, worldZ, height));
            }
        }
        event.renderer.popScissor();
        event.renderer.circle(x + size / 2.0 - 2.0, y + size / 2.0 - 2.0, 4.0, 0xFFFFFFFF);
    }

    private int surfaceHeight(int x, int z) {
        for (int y = mc().world().maxHeight(); y > mc().world().minHeight(); y--) {
            if (!mc().world().isAir(x, y, z)) {
                return y;
            }
        }
        return mc().world().minHeight();
    }

    private int terrainColor(int x, int z, int height) {
        nexus.api.IBlockRef block = mc().world().blockAt(x, height, z);
        String id = block.id();
        if (id.contains("water")) {
            return 0xFF3B82F6;
        }
        if (id.contains("sand")) {
            return 0xFFE4C290;
        }
        if (id.contains("stone")) {
            return 0xFF6B7280;
        }
        if (id.contains("grass") || id.contains("leaves")) {
            return 0xFF4ADE80;
        }
        if (id.contains("snow")) {
            return 0xFFE5E7EB;
        }
        if (id.contains("netherrack") || id.contains("lava")) {
            return 0xFFB91C1C;
        }
        return 0xFF57534E;
    }
}
