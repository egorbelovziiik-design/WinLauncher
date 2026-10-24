package nexus.core.module.client;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Animated client watermark with the theme gradient. */
public final class WatermarkModule extends Module {

    private final ModeSetting position;
    private final NumberSetting scale;
    private final BooleanSetting rainbow;
    private final StringSetting text;

    public WatermarkModule() {
        super("watermark", "Watermark", "Animated client watermark.", ModuleCategory.CLIENT);
        this.position = mode("Position", "Screen corner", "Top Left", "Top Left", "Top Right",
                "Bottom Left", "Bottom Right", "Center");
        this.scale = number("Scale", "Text scale", 1.2, 0.5, 3.0, 0.05);
        this.rainbow = bool("Rainbow", "Cycle the colors", true);
        this.text = text("Text", "Watermark text", nexus.core.NexusInfo.shortWatermark());
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        String label = text.rawValue();
        double scale = this.scale.value();
        double width = event.renderer.textWidth(label, scale);
        double height = event.renderer.fontHeight(scale) + 4.0;
        double x = 6.0;
        double y = 6.0;
        if (position.is("Top Right") || position.is("Bottom Right")) {
            x = event.width - width - 6.0;
        }
        if (position.is("Bottom Left") || position.is("Bottom Right")) {
            y = event.height - height - 6.0;
        }
        if (position.is("Center")) {
            x = event.width / 2.0 - width / 2.0;
            y = event.height * 0.06;
        }
        event.renderer.fillRoundRect(x - 4.0, y - 3.0, width + 8.0, height, 4.0,
                ColorUtil.withAlpha(0xFF08080D, 150));
        int color = rainbow.isEnabled()
                ? nexus.util.ColorUtil.rainbow(System.currentTimeMillis(), 1.0, 1.0f, 1.0f, 255)
                : nexus.core.Nexus.instance().themes().accent(System.currentTimeMillis());
        event.renderer.drawText(label, x, y, color, true, scale);
    }

    @Override
    public String hudInfo() {
        return position.get();
    }
}
