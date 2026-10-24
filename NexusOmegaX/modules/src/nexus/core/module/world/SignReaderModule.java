package nexus.core.module.world;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Shows the text of signs and item frames through walls. */
public final class SignReaderModule extends Module {

    private final ColorSetting color;
    private final NumberSetting range;
    private final NumberSetting scale;

    public SignReaderModule() {
        super("signreader", "SignReader", "Shows sign text through walls.", ModuleCategory.WORLD);
        this.color = color("Color", "Text color", 0xFFFBBF24);
        this.range = number("Range", "Maximum distance", 24.0, 4.0, 64.0, 1.0);
        this.scale = number("Scale", "Text scale", 0.8, 0.4, 2.0, 0.05);
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
                for (int dy = -8; dy <= 8; dy++) {
                    int x = playerX + dx;
                    int y = playerY + dy;
                    int z = playerZ + dz;
                    if (y < mc().world().minHeight() || y > mc().world().maxHeight()) {
                        continue;
                    }
                    nexus.api.IBlockRef block = mc().world().blockAt(x, y, z);
                    if (block == null || !block.id().contains("sign")) {
                        continue;
                    }
                    String text = mc().world().blockEntityText(x, y, z);
                    if (text == null || text.isEmpty()) {
                        continue;
                    }
                    event.renderer.drawNametag(new nexus.api.math.Vec3d(x + 0.5, y + 1.0, z + 0.5),
                            text, value, scale.value(), true, 0xCC08080D);
                }
            }
        }
    }
}
