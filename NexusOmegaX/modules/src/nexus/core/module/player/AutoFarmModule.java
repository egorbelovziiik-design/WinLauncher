package nexus.core.module.player;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Harvests and replants crops in range. */
public final class AutoFarmModule extends Module {

    private final NumberSetting range;
    private final BooleanSetting replant;
    private final BooleanSetting harvestOnly;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();

    public AutoFarmModule() {
        super("autofarm", "AutoFarm", "Harvests and replants crops automatically.", ModuleCategory.PLAYER);
        this.range = number("Range", "Working radius in blocks", 5.0, 1.0, 12.0, 1.0);
        this.replant = bool("Replant", "Replant crops after harvesting", true);
        this.harvestOnly = bool("Harvest Only", "Do not break the crop blocks", false);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed(250L, true)) {
            return;
        }
        int rangeInt = (int) range.value();
        int playerX = player().blockX();
        int playerY = player().blockY();
        int playerZ = player().blockZ();
        for (int dx = -rangeInt; dx <= rangeInt; dx++) {
            for (int dz = -rangeInt; dz <= rangeInt; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int x = playerX + dx;
                    int y = playerY + dy;
                    int z = playerZ + dz;
                    nexus.api.IBlockRef block = mc().world().blockAt(x, y, z);
                    if (block == null || block.isAir()) {
                        continue;
                    }
                    String id = block.id();
                    if (isCrop(id)) {
                        mc().interaction().lookAtBlock(x, y, z);
                        mc().interaction().breakBlock(x, y, z);
                        if (replant.isEnabled()) {
                            int seeds = player().inventory().findHotbar(seedFor(id));
                            if (seeds >= 0) {
                                player().inventory().setSelectedSlot(seeds);
                                mc().interaction().placeBlock(x, y - 1, z, 1);
                            }
                        }
                        markActivity();
                        return;
                    }
                }
            }
        }
    }

    private boolean isCrop(String id) {
        return id.contains("wheat") || id.contains("carrot") || id.contains("potato")
                || id.contains("beetroot") || id.contains("nether_wart") || id.contains("cocoa");
    }

    private String seedFor(String crop) {
        if (crop.contains("wheat")) {
            return "wheat_seeds";
        }
        if (crop.contains("carrot")) {
            return "carrot";
        }
        if (crop.contains("potato")) {
            return "potato";
        }
        if (crop.contains("beetroot")) {
            return "beetroot_seeds";
        }
        return "seeds";
    }
}
