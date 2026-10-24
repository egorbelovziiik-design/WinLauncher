package nexus.core.module.combat;

import nexus.core.event.Render2DEvent;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.MathUtil;

/** Spawns a moving target on screen and measures how fast you can aim at it. */
public final class AimTrainerModule extends Module {

    private final NumberSetting size;
    private final NumberSetting speed;
    private final ColorSetting color;
    private final BooleanSetting requireClick;

    private double targetX;
    private double targetY;
    private long spawnTime;
    private int hits;
    private int misses;
    private long totalReaction;

    public AimTrainerModule() {
        super("aimtrainer", "AimTrainer", "Trains your aiming speed with on screen targets.",
                ModuleCategory.COMBAT);
        this.size = number("Size", "Target size in pixels", 18.0, 6.0, 80.0, 1.0);
        this.speed = number("Speed", "Target movement speed", 1.0, 0.0, 6.0, 0.1);
        this.color = color("Color", "Target color", 0xFFC084FC);
        this.requireClick = bool("Require Click", "Only count hits when you click", true);
    }

    @Override
    public void onEnable() {
        respawn(320, 240);
        hits = 0;
        misses = 0;
        totalReaction = 0L;
    }

    private void respawn(int width, int height) {
        targetX = 30.0 + Math.random() * Math.max(1.0, width - 60.0);
        targetY = 30.0 + Math.random() * Math.max(1.0, height - 60.0);
        spawnTime = System.currentTimeMillis();
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        double delta = speed.value() * 1.6;
        targetX += (Math.random() - 0.5) * delta * 2.0;
        targetY += (Math.random() - 0.5) * delta * 2.0;
        targetX = MathUtil.clamp(targetX, 10.0, mc().window().guiWidth() - 10.0);
        targetY = MathUtil.clamp(targetY, 10.0, mc().window().guiHeight() - 10.0);
        if (System.currentTimeMillis() - spawnTime > 4000L) {
            misses++;
            respawn(mc().window().guiWidth(), mc().window().guiHeight());
        }
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        int value = color.effectiveColor(System.currentTimeMillis());
        double half = size.value() / 2.0;
        event.renderer.circle(targetX, targetY, half, value);
        event.renderer.circleOutline(targetX, targetY, half, 1.5, 0xFFFFFFFF);
        if (!requireClick.isEnabled() || mc().input().isMouseDown(0)) {
            double mouseX = mc().input().mouseX();
            double mouseY = mc().input().mouseY();
            if (MathUtil.distance(mouseX, mouseY, targetX, targetY) <= half) {
                hits++;
                totalReaction += System.currentTimeMillis() - spawnTime;
                respawn(event.width, event.height);
            }
        }
        int attempts = hits + misses;
        String text = "hits " + hits + " | miss " + misses
                + (hits > 0 ? " | avg " + (totalReaction / hits) + "ms" : "");
        event.renderer.drawCenteredText(text, event.width / 2.0, 24.0, value, true, 0.9);
        if (attempts > 0) {
            event.renderer.drawCenteredText("accuracy " + (hits * 100 / attempts) + "%",
                    event.width / 2.0, 36.0, nexus.core.Nexus.instance().themes().textDim(), true, 0.8);
        }
    }
}
