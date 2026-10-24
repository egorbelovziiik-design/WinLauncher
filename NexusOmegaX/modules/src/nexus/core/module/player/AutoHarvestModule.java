package nexus.core.module.player;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Breaks grown crops and collects the drops in a radius. */
public final class AutoHarvestModule extends Module {

    private final NumberSetting range;
    private final MultiSelectSetting crops;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();

    public AutoHarvestModule() {
        super("autoharvest", "AutoHarvest", "Harvests grown crops in a radius.", ModuleCategory.PLAYER);
        this.range = number("Range", "Harvest radius in blocks", 4.0, 1.0, 10.0, 1.0);
        this.crops = multi("Crops", "Crops to harvest", new String[]{"Wheat", "Carrots", "Potatoes",
                "Beetroots", "Nether Wart", "Melons", "Pumpkins"}, "Wheat", "Carrots", "Potatoes");
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed(200L, true)) {
            return;
        }
        int rangeInt = (int) range.value();
        int playerX = player().blockX();
        int playerY = player().blockY();
        int playerZ = player().blockZ();
        for (int dx = -rangeInt; dx <= rangeInt; dx++) {
            for (int dz = -rangeInt; dz <= rangeInt; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    int x = playerX + dx;
                    int y = playerY + dy;
                    int z = playerZ + dz;
                    nexus.api.IBlockRef block = mc().world().blockAt(x, y, z);
                    if (block == null || block.isAir()) {
                        continue;
                    }
                    if (matches(block.id())) {
                        mc().interaction().lookAtBlock(x, y, z);
                        mc().interaction().breakBlock(x, y, z);
                        markActivity();
                        return;
                    }
                }
            }
        }
    }

    private boolean matches(String id) {
        for (String crop : crops.selected()) {
            String needle = crop.toLowerCase().replace(" ", "_");
            if (needle.endsWith("s")) {
                needle = needle.substring(0, needle.length() - 1);
            }
            if (id.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
