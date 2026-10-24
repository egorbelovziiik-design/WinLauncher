package nexus.core.module.automation;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.util.HashSet;
import java.util.Set;

/** Breaks a whole connected vein of the same block at once. */
public final class VeinMinerModule extends Module {

    private final NumberSetting limit;
    private final BooleanSetting sameTypeOnly;
    private final Set<Long> queued = new HashSet<Long>();

    public VeinMinerModule() {
        super("veinminer", "VeinMiner", "Breaks a connected vein of the same block.",
                ModuleCategory.AUTOMATION);
        this.limit = number("Limit", "Maximum blocks per vein", 32.0, 2.0, 256.0, 1.0);
        this.sameTypeOnly = bool("Same Type", "Only break the same block type", true);
    }

    @Override
    public void onEnable() {
        queued.clear();
    }

    @Override
    public void onDisable() {
        queued.clear();
    }

    @Override
    public void cleanup() {
        queued.clear();
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int[] hit = mc().interaction().hitBlock();
        if (hit == null) {
            return;
        }
        String targetId = mc().world().blockAt(hit[0], hit[1], hit[2]).id();
        queued.clear();
        collect(hit[0], hit[1], hit[2], targetId, 0);
        int mined = 0;
        for (Long packed : queued) {
            if (mined >= limit.value()) {
                break;
            }
            int bx = (int) (packed.longValue() >> 40);
            int by = (int) ((packed.longValue() >> 20) & 0xFFFFF);
            int bz = (int) (packed.longValue() & 0xFFFFF);
            mc().interaction().breakBlock(bx, by, bz);
            mined++;
        }
        if (mined > 0) {
            markActivity();
        }
    }

    private void collect(int x, int y, int z, String targetId, int depth) {
        if (depth > 12 || queued.size() >= limit.value()) {
            return;
        }
        long key = ((long) (x & 0xFFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (z & 0xFFFFF);
        if (!queued.add(Long.valueOf(key))) {
            return;
        }
        int[][] neighbours = new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] offset : neighbours) {
            int nx = x + offset[0];
            int ny = y + offset[1];
            int nz = z + offset[2];
            if (mc().world().isAir(nx, ny, nz)) {
                continue;
            }
            nexus.api.IBlockRef block = mc().world().blockAt(nx, ny, nz);
            if (sameTypeOnly.isEnabled() && !block.id().equals(targetId)) {
                continue;
            }
            collect(nx, ny, nz, targetId, depth + 1);
        }
    }

    @Override
    public String hudInfo() {
        return (int) limit.value() + " max";
    }
}
