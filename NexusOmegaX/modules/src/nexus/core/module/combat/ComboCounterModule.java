package nexus.core.module.combat;

import nexus.core.event.AttackEvent;
import nexus.core.event.Render2DEvent;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.Easing;

/** Counts consecutive hits and shows a combo counter on screen. */
public final class ComboCounterModule extends Module {

    private final NumberSetting resetTime;
    private final BooleanSetting showBest;
    private final ColorSetting color;

    private int combo;
    private int best;
    private long lastHit;
    private double animation;

    public ComboCounterModule() {
        super("combocounter", "ComboCounter", "Counts consecutive hits and displays the current combo.",
                ModuleCategory.COMBAT);
        this.resetTime = number("Reset Time", "Time before the combo resets in ms", 2000.0, 250.0, 10000.0, 250.0);
        this.showBest = bool("Show Best", "Also show the best combo", true);
        this.color = color("Color", "Counter color", 0xFFC084FC);
    }

    @Override
    public void onEnable() {
        combo = 0;
        best = 0;
        animation = 0.0;
    }

    @Override
    public void onAttack(AttackEvent event) {
        if (event.target == null) {
            return;
        }
        combo++;
        if (combo > best) {
            best = combo;
        }
        lastHit = System.currentTimeMillis();
        animation = 0.0;
    }

    @Override
    public void onTick(TickEvent event) {
        if (combo > 0 && System.currentTimeMillis() - lastHit > (long) resetTime.value()) {
            combo = 0;
        }
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (combo < 2) {
            return;
        }
        animation = Math.min(1.0, animation + event.deltaMs / 220.0);
        double scale = 1.0 + (1.0 - Easing.ease(Easing.EASE_OUT_BACK, animation)) * 0.6;
        int value = color.effectiveColor(System.currentTimeMillis());
        String text = combo + " COMBO";
        double x = event.width / 2.0 - event.renderer.textWidth(text, scale) / 2.0;
        double y = event.height * 0.32;
        event.renderer.drawText(text, x, y, value, true, scale);
        if (showBest.isEnabled() && best > combo) {
            String bestText = "best " + best;
            event.renderer.drawCenteredText(bestText, event.width / 2.0, y + 14.0 * scale,
                    nexus.core.Nexus.instance().themes().textDim(), true, 0.8 * scale);
        }
    }

    public int combo() {
        return combo;
    }

    public int best() {
        return best;
    }
}
