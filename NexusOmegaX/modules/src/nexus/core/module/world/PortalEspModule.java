package nexus.core.module.world;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Highlights nether and end portals. */
public final class PortalEspModule extends Module {

    private final ColorSetting netherColor;
    private final ColorSetting endColor;
    private final NumberSetting range;
    private final BooleanSetting tracers;

    public PortalEspModule() {
        super("portalesp", "PortalESP", "Highlights nether and end portals.", ModuleCategory.WORLD);
        this.netherColor = color("Nether", "Nether portal color", 0xFFA855F7);
        this.endColor = color("End", "End portal color", 0xFF22D3EE);
        this.range = number("Range", "Scan radius in blocks", 32.0, 4.0, 96.0, 1.0);
        this.tracers = bool("Tracers", "Draw tracers to portals", true);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        long now = System.currentTimeMillis();
        render(event, "nether_portal", netherColor.effectiveColor(now));
        render(event, "end_portal", endColor.effectiveColor(now));
        render(event, "end_gateway", endColor.effectiveColor(now));
    }

    private void render(Render3DEvent event, String id, int color) {
        int playerX = player().blockX();
        int playerY = player().blockY();
        int playerZ = player().blockZ();
        int radiusInt = (int) range.value();
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
                    if (block == null || !block.id().contains(id)) {
                        continue;
                    }
                    nexus.api.math.Box3 box = nexus.api.math.Box3.block(x, y, z);
                    event.renderer.drawBox(box, (color & 0x00FFFFFF) | 0x37000000, color, true);
                    if (tracers.isEnabled()) {
                        event.renderer.drawTracer(new nexus.api.math.Vec3d(x + 0.5, y + 0.5, z + 0.5),
                                color, 1.0);
                    }
                }
            }
        }
    }
}
