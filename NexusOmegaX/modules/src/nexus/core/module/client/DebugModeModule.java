package nexus.core.module.client;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

import java.util.List;

/** Debug overlay with the event timings, memory and last error. */
public final class DebugModeModule extends Module {

    private final BooleanSetting showEvents;
    private final BooleanSetting showMemory;
    private final NumberSetting x;
    private final NumberSetting y;

    public DebugModeModule() {
        super("debugmode", "DebugMode", "Debug overlay with timings and memory.", ModuleCategory.CLIENT);
        this.showEvents = bool("Events", "Show event timings", true);
        this.showMemory = bool("Memory", "Show memory usage", true);
        this.x = number("X", "Horizontal position", 6.0, 0.0, 1920.0, 1.0);
        this.y = number("Y", "Vertical position", 300.0, 0.0, 1080.0, 1.0);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        double posX = x.value();
        double posY = y.value();
        event.renderer.fillRoundRect(posX - 4.0, posY - 4.0, 220.0, 96.0, 5.0,
                ColorUtil.withAlpha(0xFF08080D, 190));
        String[] lines = new String[]{
                "Nexus " + nexus.core.NexusInfo.BUILD,
                "MC " + mc().minecraftVersion() + " / " + mc().loaderName(),
                "tick " + nexus.core.Nexus.instance().tickCount()
                        + "  handlers " + nexus.core.Nexus.instance().bus().totalHandlers(),
                showMemory.isEnabled() ? memoryLine() : "",
                showEvents.isEnabled() ? "last error: " + lastError() : ""
        };
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].isEmpty()) {
                continue;
            }
            event.renderer.drawText(lines[i], posX, posY + i * 12.0, 0xFF4ADE80, true, 0.9);
        }
    }

    private String memoryLine() {
        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
        long max = runtime.maxMemory() / 1048576L;
        return "mem " + used + "/" + max + " MB  threads " + Thread.activeCount();
    }

    private String lastError() {
        String tail = nexus.util.Log.tail(1);
        return tail == null || tail.isEmpty() ? "none" : tail;
    }
}
