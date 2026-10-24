package nexus.core.hud.elements;

import nexus.core.Nexus;
import nexus.core.NexusInfo;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;
import nexus.util.ColorUtil;

/** Client watermark with the signature two tone gradient. */
public final class WatermarkElement extends HudElement {

    public WatermarkElement() {
        super("watermark", "Watermark");
        colorSetting().setColor(0xFFC084FC);
    }

    @Override
    public double width() {
        return textWidth(text(), scale());
    }

    @Override
    public double height() {
        return fontHeight(scale());
    }

    private String text() {
        return NexusInfo.shortWatermark();
    }

    @Override
    protected void draw(Render2DEvent event, double x, double y) {
        long now = System.currentTimeMillis();
        double s = scale();
        String left = "NEXUS ";
        String right = "OMEGA X " + NexusInfo.VERSION;
        int main = color(now);
        event.renderer.drawText(left, x, y, main, shadow(), s);
        event.renderer.drawText(right, x + textWidth(left, s), y,
                ColorUtil.brighten(main, 0.35), shadow(), s);
    }

    @Override
    public String previewText() {
        return NexusInfo.shortWatermark() + " | " + (Nexus.isInitialized() ? Nexus.instance().platform().minecraftVersion() : "");
    }
}
