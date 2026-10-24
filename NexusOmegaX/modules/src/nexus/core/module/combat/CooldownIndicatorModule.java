package nexus.core.module.combat;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;
import nexus.util.MathUtil;

/** Shows the attack cooldown as a ring around the crosshair. */
public final class CooldownIndicatorModule extends Module {

    private final NumberSetting radius;
    private final ColorSetting ready;
    private final ColorSetting charging;

    public CooldownIndicatorModule() {
        super("cooldownindicator", "CooldownIndicator", "Shows the attack cooldown around the crosshair.",
                ModuleCategory.COMBAT);
        this.radius = number("Radius", "Ring radius in pixels", 12.0, 6.0, 40.0, 1.0);
        this.ready = color("Ready", "Color when fully charged", 0xFF4ADE80);
        this.charging = color("Charging", "Color while charging", 0xFFFBBF24);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        double charge = MathUtil.clamp(player().attackCooldown(), 0.0, 1.0);
        double centerX = event.width / 2.0;
        double centerY = event.height / 2.0;
        int color = charge >= 1.0 ? ready.effectiveColor(System.currentTimeMillis())
                : charging.effectiveColor(System.currentTimeMillis());
        int segments = 40;
        int filled = (int) Math.round(segments * charge);
        for (int i = 0; i < segments; i++) {
            double a1 = (i / (double) segments) * Math.PI * 2.0 - Math.PI / 2.0;
            double x = centerX + Math.cos(a1) * radius.value();
            double y = centerY + Math.sin(a1) * radius.value();
            int alpha = i < filled ? ColorUtil.alpha(color) : 50;
            event.renderer.fillRect(x, y, 2.0, 2.0, ColorUtil.withAlpha(color, alpha));
        }
    }
}
