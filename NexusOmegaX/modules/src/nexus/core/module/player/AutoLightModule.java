package nexus.core.module.player;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Highlights dark spots where mobs can spawn. */
public final class AutoLightModule extends Module {

    private final NumberSetting range;
    private final NumberSetting dangerLevel;
    private final ColorSetting color;

    public AutoLightModule() {
        super("autolight", "AutoLight", "Marks dark areas where mobs can spawn.", ModuleCategory.PLAYER);
        this.range = number("Range", "Scan radius in blocks", 12.0, 4.0, 32.0, 1.0);
        this.dangerLevel = number("Danger Level", "Light level considered dangerous", 7.0, 0.0, 15.0, 1.0);
        this.color = color("Color", "Marker color", 0xFFF87171);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int rangeInt = (int) range.value();
        int playerX = player().blockX();
        int playerY = player().blockY();
        int playerZ = player().blockZ();
        int value = color.effectiveColor(System.currentTimeMillis());
        for (int dx = -rangeInt; dx <= rangeInt; dx++) {
            for (int dz = -rangeInt; dz <= rangeInt; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    int x = playerX + dx;
                    int y = playerY + dy;
                    int z = playerZ + dz;
                    if (!mc().world().isAir(x, y, z) || !mc().world().isSolid(x, y - 1, z)) {
                        continue;
                    }
                    if (mc().world().lightLevel(x, y, z) > dangerLevel.value()) {
                        continue;
                    }
                    nexus.api.math.Box3 box = nexus.api.math.Box3.block(x, y, z);
                    event.renderer.drawBox(box, ColorUtil.withAlpha(value, 35),
                            ColorUtil.withAlpha(value, 160), false);
                }
            }
        }
    }
}
