package nexus.core.module.combat;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Randomly flashes a prompt and measures your reaction time. */
public final class ReactionTrainerModule extends Module {

    private final NumberSetting minDelay;
    private final NumberSetting maxDelay;
    private final ColorSetting color;

    private long triggerAt;
    private boolean waiting;
    private long bestReaction = -1L;
    private long lastReaction = -1L;

    public ReactionTrainerModule() {
        super("reactiontrainer", "ReactionTrainer", "Measures how fast you react to an on screen prompt.",
                ModuleCategory.COMBAT);
        this.minDelay = number("Min Delay", "Minimum wait in ms", 1200.0, 200.0, 10000.0, 100.0);
        this.maxDelay = number("Max Delay", "Maximum wait in ms", 4000.0, 500.0, 20000.0, 100.0);
        this.color = color("Color", "Prompt color", 0xFF4ADE80);
    }

    @Override
    public void onEnable() {
        schedule();
    }

    private void schedule() {
        double min = Math.min(minDelay.value(), maxDelay.value());
        double max = Math.max(minDelay.value(), maxDelay.value());
        triggerAt = System.currentTimeMillis() + (long) (min + Math.random() * (max - min));
        waiting = false;
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        long now = System.currentTimeMillis();
        int value = color.effectiveColor(now);
        if (!waiting && now >= triggerAt) {
            waiting = true;
            triggerAt = now;
        }
        if (waiting) {
            event.renderer.drawCenteredText("CLICK NOW", event.width / 2.0, event.height / 2.0 - 20.0, value, true, 1.4);
            if (mc().input().isMouseDown(0)) {
                lastReaction = now - triggerAt;
                if (bestReaction < 0L || lastReaction < bestReaction) {
                    bestReaction = lastReaction;
                }
                schedule();
            }
        }
        String text = "last " + (lastReaction < 0L ? "-" : lastReaction + "ms")
                + "   best " + (bestReaction < 0L ? "-" : bestReaction + "ms");
        event.renderer.drawCenteredText(text, event.width / 2.0, event.height / 2.0 + 6.0,
                nexus.core.Nexus.instance().themes().textDim(), true, 0.9);
    }
}
