package nexus.core.module.combat;

import nexus.core.event.Render2DEvent;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.Easing;

/** Shows a red flash and the amount of damage taken. */
public final class DamageIndicatorModule extends Module {

    private final NumberSetting duration;
    private final NumberSetting intensity;
    private final BooleanSetting showAmount;

    private float previousHealth;
    private long hitTime = -1L;
    private double amount;

    public DamageIndicatorModule() {
        super("damageindicator", "DamageIndicator", "Flashes the screen and shows how much damage you took.",
                ModuleCategory.COMBAT);
        this.duration = number("Duration", "Flash duration in ms", 400.0, 100.0, 2000.0, 50.0);
        this.intensity = number("Intensity", "Maximum overlay opacity", 0.35, 0.05, 1.0, 0.05);
        this.showAmount = bool("Show Amount", "Show the damage number", true);
    }

    @Override
    public void onEnable() {
        previousHealth = mc().isInGame() ? player().health() : 20.0f;
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        float health = player().health() + player().absorption();
        if (health < previousHealth) {
            amount = previousHealth - health;
            hitTime = System.currentTimeMillis();
        }
        previousHealth = health;
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (hitTime < 0L) {
            return;
        }
        long age = System.currentTimeMillis() - hitTime;
        if (age > duration.value()) {
            hitTime = -1L;
            return;
        }
        double progress = 1.0 - age / duration.value();
        double eased = Easing.ease(Easing.EASE_OUT, progress);
        int alpha = (int) (255 * intensity.value() * eased);
        event.renderer.fillGradientRect(0.0, 0.0, event.width, event.height * 0.35,
                (alpha << 24) | 0xFF0000, 0x00000000, false);
        event.renderer.fillGradientRect(0.0, event.height, event.width, event.height * 0.65,
                (alpha << 24) | 0xFF0000, 0x00000000, false);
        if (showAmount.isEnabled() && amount > 0.0) {
            String text = "-" + String.format("%.1f", amount);
            event.renderer.drawCenteredText(text, event.width / 2.0, event.height / 2.0 - 30.0,
                    ((int) (255 * eased) << 24) | 0xFF5555, true, 1.2);
        }
    }
}
