package nexus.core.module.world;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Highlights bedrock and shows the ceiling height for roof building. */
public final class BedrockFinderModule extends Module {

    private final ColorSetting color;
    private final NumberSetting radius;
    private final BooleanSetting ceilingOnly;

    public BedrockFinderModule() {
        super("bedrockfinder", "BedrockFinder", "Highlights bedrock, useful for nether roofs.",
                ModuleCategory.WORLD);
        this.color = color("Color", "Bedrock color", 0xFF6B7280);
        this.radius = number("Radius", "Scan radius", 16.0, 4.0, 64.0, 1.0);
        this.ceilingOnly = bool("Ceiling Only", "Only highlight the top bedrock layer", true);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        int playerX = player().blockX();
        int playerZ = player().blockZ();
        int radiusInt = (int) radius.value();
        int top = mc().world().maxHeight();
        int bottom = mc().world().minHeight();
        for (int dx = -radiusInt; dx <= radiusInt; dx++) {
            for (int dz = -radiusInt; dz <= radiusInt; dz++) {
                int x = playerX + dx;
                int z = playerZ + dz;
                for (int y = top; y >= bottom; y--) {
                    nexus.api.IBlockRef block = mc().world().blockAt(x, y, z);
                    if (block == null || !block.id().contains("bedrock")) {
                        continue;
                    }
                    nexus.api.math.Box3 box = nexus.api.math.Box3.block(x, y, z);
                    event.renderer.drawBox(box, ColorUtil.withAlpha(value, 40), value, true);
                    break;
                }
                if (!ceilingOnly.isEnabled()) {
                    for (int y = bottom; y <= top; y++) {
                        nexus.api.IBlockRef block = mc().world().blockAt(x, y, z);
                        if (block != null && block.id().contains("bedrock")) {
                            nexus.api.math.Box3 box = nexus.api.math.Box3.block(x, y, z);
                            event.renderer.drawBox(box, ColorUtil.withAlpha(value, 40), value, true);
                            break;
                        }
                    }
                }
            }
        }
    }
}
