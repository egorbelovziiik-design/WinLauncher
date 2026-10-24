package nexus.core.module.world;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Generic block search: finds and counts any block by id. */
public final class BlockFinderModule extends Module {

    private final StringSetting target;
    private final ColorSetting color;
    private final NumberSetting radius;
    private final BooleanSetting showCount;
    private int hits;

    public BlockFinderModule() {
        super("blockfinder", "BlockFinder", "Finds and counts any block by id.", ModuleCategory.WORLD);
        this.target = text("Block", "Block id part to search for", "ancient_debris");
        this.color = color("Color", "Highlight color", 0xFFC084FC);
        this.radius = number("Radius", "Scan radius in blocks", 24.0, 4.0, 64.0, 1.0);
        this.showCount = bool("Show Count", "Show how many were found", true);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        String wanted = target.rawValue().trim();
        if (wanted.isEmpty()) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        hits = 0;
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
                    if (block == null || !block.id().contains(wanted)) {
                        continue;
                    }
                    hits++;
                    nexus.api.math.Box3 box = nexus.api.math.Box3.block(x, y, z);
                    event.renderer.drawBox(box, ColorUtil.withAlpha(value, 55), value, true);
                }
            }
        }
        if (showCount.isEnabled() && hits > 0) {
            event.renderer.drawNametag(player().eyePosition().add(0.0, 1.0, 0.0),
                    wanted + ": " + hits, value, 0.9, true, ColorUtil.withAlpha(0xFF08080D, 160));
        }
    }

    @Override
    public String hudInfo() {
        return hits + " found";
    }
}
