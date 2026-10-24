package nexus.core.module.automation;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Breaks every block in a radius (creative / single player tool). */
public final class NukerModule extends Module {

    private final NumberSetting radius;
    private final ModeSetting filter;
    private final NumberSetting perTick;
    private final BooleanSetting flatten;

    public NukerModule() {
        super("nuker", "Nuker", "Breaks every block in a radius around you.", ModuleCategory.AUTOMATION);
        this.radius = number("Radius", "Breaking radius", 3.0, 1.0, 6.0, 1.0);
        this.filter = mode("Filter", "Which blocks to break", "All", "All", "Ores", "No Stone");
        this.perTick = number("Per Tick", "Blocks broken per tick", 3.0, 1.0, 32.0, 1.0);
        this.flatten = bool("Flatten", "Only break down to your feet level", false);
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int broken = 0;
        int x = player().blockX();
        int y = player().blockY();
        int z = player().blockZ();
        int radiusInt = (int) radius.value();
        for (int dx = -radiusInt; dx <= radiusInt && broken < perTick.value(); dx++) {
            for (int dz = -radiusInt; dz <= radiusInt && broken < perTick.value(); dz++) {
                for (int dy = -1; dy <= radiusInt && broken < perTick.value(); dy++) {
                    if (flatten.isEnabled() && dy > 0) {
                        continue;
                    }
                    int bx = x + dx;
                    int by = y + dy;
                    int bz = z + dz;
                    if (mc().world().isAir(bx, by, bz)) {
                        continue;
                    }
                    nexus.api.IBlockRef block = mc().world().blockAt(bx, by, bz);
                    if (!accepted(block)) {
                        continue;
                    }
                    mc().interaction().breakBlock(bx, by, bz);
                    broken++;
                }
            }
        }
        if (broken > 0) {
            markActivity();
        }
    }

    private boolean accepted(nexus.api.IBlockRef block) {
        if (filter.is("Ores")) {
            return block.isOre();
        }
        if (filter.is("No Stone")) {
            return !block.id().contains("stone") && !block.id().contains("dirt")
                    && !block.id().contains("grass_block") && !block.id().contains("deepslate");
        }
        return true;
    }

    @Override
    public String hudInfo() {
        return (int) radius.value() + " blocks";
    }
}
