package nexus.core.module.misc;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;
import nexus.util.TimeUtil;

/** A simple stopwatch with split times. */
public final class StopWatchModule extends Module {

    private final NumberSetting x;
    private final NumberSetting y;
    private long start = -1L;
    private long split = -1L;
    private boolean running;

    public StopWatchModule() {
        super("stopwatch", "StopWatch", "A stopwatch with split times.", ModuleCategory.MISC);
        this.x = number("X", "Horizontal position", 8.0, 0.0, 1920.0, 1.0);
        this.y = number("Y", "Vertical position", 200.0, 0.0, 1080.0, 1.0);
    }

    public void start() {
        start = System.currentTimeMillis();
        split = start;
        running = true;
    }

    public void stop() {
        running = false;
    }

    public void reset() {
        start = -1L;
        split = -1L;
        running = false;
    }

    public void takeSplit() {
        split = System.currentTimeMillis();
    }

    public String elapsed() {
        if (start < 0L) {
            return "00:00.0";
        }
        long value = (running ? System.currentTimeMillis() : split) - start;
        return format(value);
    }

    public String splitText() {
        if (split < 0L || start < 0L) {
            return "00:00.0";
        }
        return format(split - start);
    }

    private String format(long millis) {
        long seconds = millis / 1000L;
        long minutes = seconds / 60L;
        long tenths = (millis % 1000L) / 100L;
        return String.format("%02d:%02d.%d", Long.valueOf(minutes), Long.valueOf(seconds % 60L),
                Long.valueOf(tenths));
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (start < 0L) {
            return;
        }
        String text = "Stopwatch " + elapsed() + "  (split " + splitText() + ")";
        event.renderer.fillRoundRect(x.value() - 3.0, y.value() - 3.0,
                event.renderer.textWidth(text, 1.0) + 6.0, 16.0, 3.0, ColorUtil.withAlpha(0xFF08080D, 170));
        event.renderer.drawText(text, x.value(), y.value(), 0xFF4ADE80, true, 1.0);
    }

    @Override
    public String hudInfo() {
        return elapsed();
    }
}
