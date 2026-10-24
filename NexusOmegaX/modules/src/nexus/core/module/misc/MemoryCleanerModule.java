package nexus.core.module.misc;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Runs a garbage collection when the memory usage gets high. */
public final class MemoryCleanerModule extends Module {

    private final NumberSetting threshold;
    private final NumberSetting cooldown;
    private final nexus.util.TimeUtil.Timer timer = new nexus.util.TimeUtil.Timer();
    private int runs;

    public MemoryCleanerModule() {
        super("memorycleaner", "MemoryCleaner", "Frees memory when usage gets high.",
                ModuleCategory.MISC);
        this.threshold = number("Threshold", "Usage percent that triggers a cleanup", 85.0, 40.0, 99.0, 1.0);
        this.cooldown = number("Cooldown", "Seconds between cleanups", 60.0, 5.0, 600.0, 5.0);
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        if (!isEnabled() || !timer.hasPassed((long) (cooldown.value() * 1000.0))) {
            return;
        }
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();
        if (max <= 0L || used * 100L / max < threshold.value()) {
            return;
        }
        System.gc();
        runs++;
        timer.reset();
        markActivity();
    }

    public int runs() {
        return runs;
    }

    public double usagePercent() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) * 100.0 / runtime.maxMemory();
    }

    @Override
    public String hudInfo() {
        return String.format("%.0f%%", Double.valueOf(usagePercent()));
    }
}
