package nexus.core.module.misc;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Compact FPS, memory and ping overlay. */
public final class PerformanceOverlayModule extends Module {

    private final NumberSetting x;
    private final NumberSetting y;
    private final BooleanSetting showPing;
    private final BooleanSetting showMemory;
    private final BooleanSetting background;

    public PerformanceOverlayModule() {
        super("performanceoverlay", "PerformanceOverlay", "Compact FPS, memory and ping overlay.",
                ModuleCategory.MISC);
        this.x = number("X", "Horizontal position", 6.0, 0.0, 1920.0, 1.0);
        this.y = number("Y", "Vertical position", 6.0, 0.0, 1080.0, 1.0);
        this.showPing = bool("Ping", "Show the ping", true);
        this.showMemory = bool("Memory", "Show the memory usage", true);
        this.background = bool("Background", "Draw a background plate", true);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        int fps = mc().options().currentFps();
        StringBuilder sb = new StringBuilder();
        sb.append(fps).append(" fps");
        if (showPing.isEnabled() && mc().isInGame()) {
            sb.append("  ").append(player().ping()).append(" ms");
        }
        if (showMemory.isEnabled()) {
            Runtime runtime = Runtime.getRuntime();
            long used = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
            long max = runtime.maxMemory() / 1048576L;
            sb.append("  ").append(used).append("/").append(max).append(" MB");
        }
        String text = sb.toString();
        double textWidth = event.renderer.textWidth(text, 1.0);
        if (background.isEnabled()) {
            event.renderer.fillRoundRect(x.value() - 3.0, y.value() - 3.0, textWidth + 6.0, 16.0, 3.0,
                    ColorUtil.withAlpha(0xFF08080D, 165));
        }
        int color = fps >= 60 ? 0xFF4ADE80 : (fps >= 30 ? 0xFFFBBF24 : 0xFFF87171);
        event.renderer.drawText(text, x.value(), y.value(), color, true, 1.0);
    }
}
