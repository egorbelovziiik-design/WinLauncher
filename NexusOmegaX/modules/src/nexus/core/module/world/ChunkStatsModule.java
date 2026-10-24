package nexus.core.module.world;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Counts loaded chunks and estimates the memory used by the world. */
public final class ChunkStatsModule extends Module {

    private final NumberSetting interval;
    private final nexus.util.TimeUtil.Timer timer = new nexus.util.TimeUtil.Timer();
    private int loaded;
    private long memory;

    public ChunkStatsModule() {
        super("chunkstats", "ChunkStats", "Counts loaded chunks and estimates world memory.",
                ModuleCategory.WORLD);
        this.interval = number("Interval", "Update interval in ms", 1000.0, 100.0, 10000.0, 100.0);
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed((long) interval.value(), true)) {
            return;
        }
        int radius = mc().options().renderDistance();
        loaded = 0;
        int chunkX = player().blockX() >> 4;
        int chunkZ = player().blockZ() >> 4;
        for (int cx = chunkX - radius; cx <= chunkX + radius; cx++) {
            for (int cz = chunkZ - radius; cz <= chunkZ + radius; cz++) {
                if (mc().world().isChunkLoaded(cx, cz)) {
                    loaded++;
                }
            }
        }
        Runtime runtime = Runtime.getRuntime();
        memory = runtime.totalMemory() - runtime.freeMemory();
    }

    public int loadedChunks() {
        return loaded;
    }

    public long usedMemory() {
        return memory;
    }

    @Override
    public String hudInfo() {
        return loaded + " chunks";
    }
}
