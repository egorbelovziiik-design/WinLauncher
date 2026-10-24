package nexus.core.module.combat;

import nexus.core.event.AttackEvent;
import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Measures the time between your attacks. */
public final class CombatTimerModule extends Module {

    private final BooleanSetting showAverage;
    private final ColorSetting color;
    private long lastAttack = -1L;
    private long lastInterval;
    private long total;
    private int samples;

    public CombatTimerModule() {
        super("combattimer", "CombatTimer", "Measures and displays the delay between your attacks.",
                ModuleCategory.COMBAT);
        this.showAverage = bool("Show Average", "Also show the average delay", true);
        this.color = color("Color", "Text color", 0xFFC084FC);
    }

    @Override
    public void onEnable() {
        lastAttack = -1L;
        total = 0L;
        samples = 0;
    }

    @Override
    public void onAttack(AttackEvent event) {
        long now = System.currentTimeMillis();
        if (lastAttack > 0L) {
            lastInterval = now - lastAttack;
            total += lastInterval;
            samples++;
        }
        lastAttack = now;
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (lastInterval <= 0L) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        String text = "last " + lastInterval + "ms";
        if (showAverage.isEnabled() && samples > 0) {
            text += " | avg " + (total / samples) + "ms";
        }
        event.renderer.drawText(text, 6.0, event.height * 0.55, value, true, 0.85);
    }
}
