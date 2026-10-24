package nexus.core.hud.elements;

import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;
import nexus.core.setting.BooleanSetting;

/** FPS counter with optional minimum / average readouts. */
public final class FpsElement extends HudElement {

    private final BooleanSetting showMin;
    private final BooleanSetting showMs;

    public FpsElement() {
        super("fps", "FPS");
        this.showMin = new BooleanSetting("Show Min", "Show the minimum FPS", false);
        this.showMs = new BooleanSetting("Show Frame Time", "Show the average frame time", false);
    }

    private String text() {
        nexus.core.performance.PerformanceMonitor monitor = Nexus.instance().performance();
        StringBuilder sb = new StringBuilder(String.format("%d FPS", (int) monitor.fps()));
        if (showMin.isEnabled()) {
            sb.append(String.format(" (min %d)", (int) monitor.minFps()));
        }
        if (showMs.isEnabled()) {
            sb.append(String.format(" %.1fms", monitor.averageFrameMs()));
        }
        return sb.toString();
    }

    @Override
    public double width() {
        return textWidth(text(), scale());
    }

    @Override
    public double height() {
        return fontHeight(scale());
    }

    @Override
    protected void draw(Render2DEvent event, double x, double y) {
        event.renderer.drawText(text(), x, y, color(System.currentTimeMillis()), shadow(), scale());
    }

    @Override
    public java.util.List<nexus.core.setting.Setting<?>> settings() {
        java.util.List<nexus.core.setting.Setting<?>> list = super.settings();
        list.add(showMin);
        list.add(showMs);
        return list;
    }

    @Override
    public String previewText() {
        return "144 FPS (min 96) 6.9ms";
    }
}
