package nexus.core.module.world;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Finds blocks that do not belong to natural terrain (player built bases). */
public final class BaseFinderModule extends Module {

    private final MultiSelectSetting suspicious;
    private final ColorSetting color;
    private final NumberSetting radius;
    private final BooleanSetting tracers;

    public BaseFinderModule() {
        super("basefinder", "BaseFinder", "Finds player built structures by their blocks.",
                ModuleCategory.WORLD);
        this.suspicious = multi("Blocks", "Blocks considered player made", new String[]{"Crafting Table",
                "Furnace", "Chest", "Torch", "Bed", "Ladder", "Hopper", "Piston", "Observer", "Redstone"},
                "Crafting Table", "Furnace", "Torch", "Bed");
        this.color = color("Color", "Highlight color", 0xFFF87171);
        this.radius = number("Radius", "Scan radius in blocks", 48.0, 8.0, 128.0, 1.0);
        this.tracers = bool("Tracers", "Draw tracers to findings", true);
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
        for (int dx = -radiusInt; dx <= radiusInt; dx++) {
            for (int dz = -radiusInt; dz <= radiusInt; dz++) {
                for (int dy = -16; dy <= 16; dy++) {
                    int x = playerX + dx;
                    int y = playerY + dy;
                    int z = playerZ + dz;
                    if (y < mc().world().minHeight() || y > mc().world().maxHeight()) {
                        continue;
                    }
                    nexus.api.IBlockRef block = mc().world().blockAt(x, y, z);
                    if (block == null || block.isAir() || !matches(block.id())) {
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

    private boolean matches(String id) {
        for (String selected : suspicious.selected()) {
            if (id.contains(selected.toLowerCase().replace(' ', '_'))) {
                return true;
            }
        }
        return false;
    }
}
