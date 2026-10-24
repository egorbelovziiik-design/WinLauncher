package nexus.core.module.world;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Highlights flowing water and lava sources. */
public final class LiquidsEspModule extends Module {

    private final ColorSetting waterColor;
    private final ColorSetting lavaColor;
    private final NumberSetting radius;
    private final BooleanSetting sourcesOnly;

    public LiquidsEspModule() {
        super("liquidsesp", "LiquidsESP", "Highlights water and lava sources.", ModuleCategory.WORLD);
        this.waterColor = color("Water", "Water color", 0xFF3B82F6);
        this.lavaColor = color("Lava", "Lava color", 0xFFF97316);
        this.radius = number("Radius", "Scan radius", 16.0, 4.0, 64.0, 1.0);
        this.sourcesOnly = bool("Sources Only", "Only highlight source blocks", true);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int water = waterColor.effectiveColor(System.currentTimeMillis());
        int lava = lavaColor.effectiveColor(System.currentTimeMillis());
        int playerX = player().blockX();
        int playerY = player().blockY();
        int playerZ = player().blockZ();
        int radiusInt = (int) radius.value();
        for (int dx = -radiusInt; dx <= radiusInt; dx++) {
            for (int dz = -radiusInt; dz <= radiusInt; dz++) {
                for (int dy = -radiusInt; dy <= radiusInt; dy++) {
                    int x = playerX + dx;
                    int y = playerY + dy;
                    int z = playerZ + dz;
                    if (y < mc().world().minHeight() || y > mc().world().maxHeight()) {
                        continue;
                    }
                    nexus.api.IBlockRef block = mc().world().blockAt(x, y, z);
                    if (block == null) {
                        continue;
                    }
                    boolean isWater = block.id().contains("water");
                    boolean isLava = block.id().contains("lava");
                    if (!isWater && !isLava) {
                        continue;
                    }
                    if (sourcesOnly.isEnabled() && !block.isSource()) {
                        continue;
                    }
                    nexus.api.math.Box3 box = nexus.api.math.Box3.block(x, y, z);
                    event.renderer.drawBox(box, ColorUtil.withAlpha(isWater ? water : lava, 45),
                            isWater ? water : lava, true);
                }
            }
        }
    }
}
