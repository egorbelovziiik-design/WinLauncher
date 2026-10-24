package nexus.core.module.world;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Shows how full a container is without opening it. */
public final class ContainerPreviewModule extends Module {

    private final ColorSetting color;
    private final NumberSetting range;
    private final BooleanSetting showFill;

    public ContainerPreviewModule() {
        super("containerpreview", "ContainerPreview", "Shows how full containers are.",
                ModuleCategory.WORLD);
        this.color = color("Color", "Label color", 0xFFFBBF24);
        this.range = number("Range", "Maximum distance", 12.0, 2.0, 48.0, 1.0);
        this.showFill = bool("Show Fill", "Show the fill percentage", true);
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
        int radiusInt = (int) range.value();
        for (int dx = -radiusInt; dx <= radiusInt; dx++) {
            for (int dz = -radiusInt; dz <= radiusInt; dz++) {
                for (int dy = -6; dy <= 6; dy++) {
                    int x = playerX + dx;
                    int y = playerY + dy;
                    int z = playerZ + dz;
                    if (y < mc().world().minHeight() || y > mc().world().maxHeight()) {
                        continue;
                    }
                    nexus.api.IBlockRef block = mc().world().blockAt(x, y, z);
                    if (block == null || !block.id().contains("chest") || block.id().contains("ender")) {
                        continue;
                    }
                    int slots = mc().world().blockEntitySize(x, y, z);
                    if (slots <= 0) {
                        continue;
                    }
                    String label = showFill.isEnabled() ? slots + " slots" : "container";
                    event.renderer.drawNametag(new nexus.api.math.Vec3d(x + 0.5, y + 1.2, z + 0.5),
                            label, value, 0.8, true, 0xCC08080D);
                }
            }
        }
    }
}
