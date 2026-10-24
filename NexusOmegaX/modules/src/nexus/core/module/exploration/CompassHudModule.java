package nexus.core.module.exploration;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;
import nexus.util.MathUtil;

/** Compass ribbon showing the cardinal directions. */
public final class CompassHudModule extends Module {

    private final NumberSetting width;
    private final NumberSetting y;
    private final BooleanSetting showDegrees;

    public CompassHudModule() {
        super("compasshud", "CompassHUD", "Compass ribbon with cardinal directions.",
                ModuleCategory.EXPLORATION);
        this.width = number("Width", "Ribbon width", 200.0, 80.0, 800.0, 2.0);
        this.y = number("Y", "Vertical position", 24.0, 0.0, 1080.0, 1.0);
        this.showDegrees = bool("Degrees", "Show degree markers", true);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        double ribbonWidth = width.value();
        double centerX = event.width / 2.0;
        double left = centerX - ribbonWidth / 2.0;
        double top = y.value();
        event.renderer.fillRoundRect(left, top, ribbonWidth, 18.0, 4.0, ColorUtil.withAlpha(0xFF08080D, 165));
        int accent = nexus.core.Nexus.instance().themes().accent(System.currentTimeMillis());
        float yaw = (float) MathUtil.wrapDegrees(player().yaw());
        for (int degree = -90; degree <= 90; degree += 15) {
            float offset = (float) MathUtil.wrapDegrees(degree);
            if (Math.abs(offset - yaw) > 90.0f) {
                continue;
            }
            double position = centerX + (offset - yaw) / 180.0 * ribbonWidth;
            boolean cardinal = Math.abs(offset % 90.0f) < 0.01f;
            event.renderer.fillRect(position - 1.0, top + (cardinal ? 2.0 : 5.0), 2.0,
                    cardinal ? 14.0 : 8.0, cardinal ? accent : 0xFFC084FC);
            if (cardinal) {
                String label = label(offset);
                event.renderer.drawCenteredText(label, position, top + 3.0, 0xFFF5F3FF, true, 0.9);
            } else if (showDegrees.isEnabled()) {
                event.renderer.drawCenteredText(String.valueOf(Math.abs(degree)), position, top + 8.0,
                        ColorUtil.withAlpha(0xFFF5F3FF, 130), true, 0.7);
            }
        }
        event.renderer.fillRect(centerX - 0.75, top, 1.5, 18.0, 0xFFF87171);
    }

    private String label(float yaw) {
        if (Math.abs(yaw) < 1.0f || Math.abs(Math.abs(yaw) - 360.0f) < 1.0f) {
            return "S";
        }
        if (Math.abs(yaw - 90.0f) < 1.0f) {
            return "W";
        }
        if (Math.abs(yaw + 90.0f) < 1.0f) {
            return "E";
        }
        return "N";
    }
}
