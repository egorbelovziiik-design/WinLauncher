package nexus.core.module.combat;

import nexus.core.event.AttackEvent;
import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Counts how many of your attacks actually connect. */
public final class TargetPracticeModule extends Module {

    private final BooleanSetting showPercentage;
    private final ColorSetting color;
    private int swings;
    private int hits;

    public TargetPracticeModule() {
        super("targetpractice", "TargetPractice", "Tracks your hit accuracy during practice sessions.",
                ModuleCategory.COMBAT);
        this.showPercentage = bool("Show Percentage", "Show accuracy as a percentage", true);
        this.color = color("Color", "Text color", 0xFFC084FC);
    }

    @Override
    public void onEnable() {
        swings = 0;
        hits = 0;
    }

    @Override
    public void onAttack(AttackEvent event) {
        swings++;
        if (event.target != null) {
            hits++;
        }
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (swings == 0) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        double accuracy = hits * 100.0 / swings;
        String text = showPercentage.isEnabled()
                ? String.format("accuracy %.1f%% (%d/%d)", accuracy, hits, swings)
                : "hits " + hits + "/" + swings;
        event.renderer.drawCenteredText(text, event.width / 2.0, event.height - 40.0, value, true, 0.9);
    }

    public double accuracy() {
        return swings == 0 ? 0.0 : hits * 100.0 / swings;
    }
}
