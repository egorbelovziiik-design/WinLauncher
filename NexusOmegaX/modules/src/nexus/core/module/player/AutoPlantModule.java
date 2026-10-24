package nexus.core.module.player;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Plants seeds and saplings on farmland automatically. */
public final class AutoPlantModule extends Module {

    private final NumberSetting range;
    private final ModeSetting crop;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();

    public AutoPlantModule() {
        super("autoplant", "AutoPlant", "Plants seeds and saplings automatically.", ModuleCategory.PLAYER);
        this.range = number("Range", "Planting radius in blocks", 4.0, 1.0, 10.0, 1.0);
        this.crop = mode("Crop", "Item to plant", "Seeds", "Seeds", "Carrot", "Potato", "Beetroot", "Sapling");
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed(300L, true)) {
            return;
        }
        String itemId = crop.is("Seeds") ? "wheat_seeds" : crop.get().toLowerCase();
        int slot = player().inventory().findHotbar(itemId);
        if (slot < 0) {
            return;
        }
        int rangeInt = (int) range.value();
        int playerX = player().blockX();
        int playerY = player().blockY();
        int playerZ = player().blockZ();
        for (int dx = -rangeInt; dx <= rangeInt; dx++) {
            for (int dz = -rangeInt; dz <= rangeInt; dz++) {
                int x = playerX + dx;
                int z = playerZ + dz;
                if (!mc().world().isAir(x, playerY, z)) {
                    continue;
                }
                String below = mc().world().blockAt(x, playerY - 1, z).id();
                if (!below.contains("farmland") && !below.contains("dirt") && !below.contains("grass")
                        && !below.contains("soul_sand")) {
                    continue;
                }
                int previous = player().inventory().selectedSlot();
                player().inventory().setSelectedSlot(slot);
                mc().interaction().lookAtBlock(x, playerY - 1, z);
                mc().interaction().placeBlock(x, playerY - 1, z, 1);
                player().inventory().setSelectedSlot(previous);
                markActivity();
                return;
            }
        }
    }
}
