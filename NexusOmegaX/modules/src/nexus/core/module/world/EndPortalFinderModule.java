package nexus.core.module.world;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Highlights end portal frames and counts the filled eyes. */
public final class EndPortalFinderModule extends Module {

    private final ColorSetting color;
    private final NumberSetting radius;
    private final BooleanSetting showCount;

    public EndPortalFinderModule() {
        super("endportalfinder", "EndPortalFinder", "Highlights end portal frames.",
                ModuleCategory.WORLD);
        this.color = color("Color", "Frame color", 0xFF22D3EE);
        this.radius = number("Radius", "Scan radius", 32.0, 4.0, 128.0, 1.0);
        this.showCount = bool("Show Count", "Show the number of filled eyes", true);
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
        int radiusInt = (int) radius.value();
        int frames = 0;
        for (int dx = -radiusInt; dx <= radiusInt; dx += 2) {
            for (int dz = -radiusInt; dz <= radiusInt; dz += 2) {
                for (int dy = -32; dy <= 16; dy += 2) {
                    int x = playerX + dx;
                    int y = playerY + dy;
                    int z = playerZ + dz;
                    if (y < mc().world().minHeight() || y > mc().world().maxHeight()) {
                        continue;
                    }
                    nexus.api.IBlockRef block = mc().world().blockAt(x, y, z);
                    if (block == null || !block.id().contains("end_portal_frame")) {
                        continue;
                    }
                    frames++;
                    nexus.api.math.Box3 box = nexus.api.math.Box3.block(x, y, z);
                    event.renderer.drawBox(box, ColorUtil.withAlpha(value, 50), value, true);
                }
            }
        }
        if (showCount.isEnabled() && frames > 0) {
            event.renderer.drawNametag(player().eyePosition().add(0.0, 1.0, 0.0),
                    "portal frames: " + frames, value, 0.9, true, 0xCC08080D);
        }
    }
}
