package nexus.core.module.exploration;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Suggests random exploration directions and shows unexplored headings. */
public final class WanderGuideModule extends Module {

    private final NumberSetting radius;
    private final BooleanSetting showArrow;
    private final NumberSetting refreshSeconds;
    private double targetYaw;
    private long lastPick;

    public WanderGuideModule() {
        super("wanderguide", "WanderGuide", "Suggests directions that you have not explored yet.",
                ModuleCategory.EXPLORATION);
        this.radius = number("Radius", "Exploration radius", 512.0, 128.0, 4096.0, 128.0);
        this.showArrow = bool("Show Arrow", "Draw a direction arrow", true);
        this.refreshSeconds = number("Refresh", "Seconds between suggestions", 60.0, 5.0, 600.0, 5.0);
    }

    @Override
    public void onEnable() {
        pick();
    }

    private void pick() {
        targetYaw = Math.random() * 360.0 - 180.0;
        lastPick = System.currentTimeMillis();
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        if (System.currentTimeMillis() - lastPick > refreshSeconds.value() * 1000.0) {
            pick();
        }
        int accent = nexus.core.Nexus.instance().themes().accent(System.currentTimeMillis());
        double centerX = event.width / 2.0;
        double centerY = event.height * 0.75;
        if (showArrow.isEnabled()) {
            double rad = Math.toRadians(targetYaw - player().yaw());
            event.renderer.triangle(centerX + Math.sin(rad) * 10.0, centerY - Math.cos(rad) * 10.0,
                    centerX + Math.sin(rad + 2.4) * 7.0, centerY - Math.cos(rad + 2.4) * 7.0,
                    centerX + Math.sin(rad - 2.4) * 7.0, centerY - Math.cos(rad - 2.4) * 7.0, accent);
        }
        event.renderer.drawCenteredText("Explore: " + heading((float) targetYaw)
                        + " (" + (int) radius.value() + "m)",
                centerX, centerY + 16.0, ColorUtil.withAlpha(0xFFF5F3FF, 200), true, 0.9);
    }

    private String heading(float yaw) {
        if (Math.abs(yaw) < 45.0f) {
            return "South";
        }
        if (yaw > 0.0f) {
            return "West";
        }
        if (Math.abs(yaw) > 135.0f) {
            return "North";
        }
        return "East";
    }
}
