package nexus.core.hud.elements;

import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;
import nexus.core.setting.NumberSetting;
import nexus.util.ColorUtil;

/** JVM heap memory usage bar. */
public final class MemoryUsageElement extends HudElement {

    private final NumberSetting barWidth;

    public MemoryUsageElement() {
        super("memoryusage", "MemoryUsage");
        this.barWidth = new NumberSetting("Width", "Bar width", 90.0, 40.0, 220.0, 1.0);
    }

    private String text() {
        nexus.core.performance.PerformanceMonitor monitor = Nexus.instance().performance();
        return String.format("MEM %d/%d MB (%d%%)", monitor.usedMemoryMb(), monitor.maxMemoryMb(), monitor.memoryPercent());
    }

    @Override
    public double width() {
        return Math.max(textWidth(text(), scale()), barWidth.value() * scale());
    }

    @Override
    public double height() {
        return fontHeight(scale()) + 6.0 * scale();
    }

    @Override
    protected void draw(Render2DEvent event, double x, double y) {
        double s = scale();
        nexus.core.performance.PerformanceMonitor monitor = Nexus.instance().performance();
        event.renderer.drawText(text(), x, y, color(System.currentTimeMillis()), shadow(), s);
        double barY = y + fontHeight(s) + 2.0;
        double w = barWidth.value() * s;
        double ratio = monitor.memoryPercent() / 100.0;
        event.renderer.fillRect(x, barY, w, 4.0 * s, ColorUtil.withAlpha(0xFF000000, 120));
        event.renderer.fillRect(x, barY, w * ratio, 4.0 * s,
                monitor.memoryPercent() > 85 ? 0xFFF87171 : (monitor.memoryPercent() > 60 ? 0xFFFBBF24 : 0xFF4ADE80));
    }

    @Override
    public java.util.List<nexus.core.setting.Setting<?>> settings() {
        java.util.List<nexus.core.setting.Setting<?>> list = super.settings();
        list.add(barWidth);
        return list;
    }

    @Override
    public String previewText() {
        return "MEM 512/4096 MB (12%)";
    }
}
