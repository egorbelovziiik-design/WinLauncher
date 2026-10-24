package nexus.core.hud.elements;

import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;

/** Process CPU usage reported by the JVM (falls back to the frame time load). */
public final class CpuUsageElement extends HudElement {

    public CpuUsageElement() {
        super("cpuusage", "CPUUsage", false);
    }

    private String text() {
        nexus.core.performance.PerformanceMonitor monitor = Nexus.instance().performance();
        double load = monitor.cpuLoad();
        if (load < 0.0) {
            return "CPU n/a";
        }
        return String.format("CPU %.1f%%", load);
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
    public String previewText() {
        return "CPU 12.4%";
    }
}
