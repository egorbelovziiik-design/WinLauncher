package nexus.core.module.exploration;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Highlights buried treasure markers and suspicious blocks. */
public final class TreasureHunterModule extends Module {

    private final ColorSetting color;
    private final NumberSetting radius;
    private final MultiSelectSetting targets;

    public TreasureHunterModule() {
        super("treasurehunter", "TreasureHunter", "Highlights treasure related blocks.",
                ModuleCategory.EXPLORATION);
        this.color = color("Color", "Highlight color", 0xFFFBBF24);
        this.radius = number("Radius", "Scan radius", 24.0, 4.0, 64.0, 1.0);
        this.targets = multi("Targets", "Blocks to highlight", new String[]{"Chest", "Sus Sand",
                "Sus Gravel", "Shulker", "Barrel"}, "Chest", "Sus Sand", "Sus Gravel");
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        String[] ids = new String[]{"chest", "suspicious_sand", "suspicious_gravel", "shulker_box", "barrel"};
        for (String selected : targets.selected()) {
            int index = indexOf(selected);
            if (index < 0) {
                continue;
            }
            int playerX = player().blockX();
            int playerY = player().blockY();
            int playerZ = player().blockZ();
            int radiusInt = (int) radius.value();
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
                        if (block == null || !block.id().contains(ids[index])) {
                            continue;
                        }
                        nexus.api.math.Box3 box = nexus.api.math.Box3.block(x, y, z);
                        event.renderer.drawBox(box, ColorUtil.withAlpha(value, 50), value, true);
                    }
                }
            }
        }
    }

    private int indexOf(String label) {
        String[] labels = new String[]{"Chest", "Sus Sand", "Sus Gravel", "Shulker", "Barrel"};
        for (int i = 0; i < labels.length; i++) {
            if (labels[i].equals(label)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String hudInfo() {
        return targets.selected().size() + " targets";
    }
}
