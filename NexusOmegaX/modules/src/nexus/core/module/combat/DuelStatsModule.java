package nexus.core.module.combat;

import nexus.core.event.AttackEvent;
import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Tracks hits landed and taken during a duel. */
public final class DuelStatsModule extends Module {

    private final BooleanSetting autoReset;
    private final NumberSetting resetAfter;
    private final ColorSetting color;

    private int hitsLanded;
    private int hitsTaken;
    private long firstHit = -1L;
    private long lastActivity = -1L;
    private float previousHealth;

    public DuelStatsModule() {
        super("duelstats", "DuelStats", "Counts the hits you land and take during a fight.",
                ModuleCategory.COMBAT);
        this.autoReset = bool("Auto Reset", "Reset the stats after a pause", true);
        this.resetAfter = number("Reset After", "Seconds of inactivity before reset", 15.0, 3.0, 120.0, 1.0);
        this.color = color("Color", "Text color", 0xFFC084FC);
    }

    @Override
    public void onEnable() {
        reset();
    }

    private void reset() {
        hitsLanded = 0;
        hitsTaken = 0;
        firstHit = -1L;
        previousHealth = mc().isInGame() ? player().health() : 20.0f;
    }

    @Override
    public void onAttack(AttackEvent event) {
        if (event.target == null) {
            return;
        }
        hitsLanded++;
        lastActivity = System.currentTimeMillis();
        if (firstHit < 0L) {
            firstHit = lastActivity;
        }
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        float health = player().health();
        if (health < previousHealth) {
            hitsTaken++;
            lastActivity = System.currentTimeMillis();
        }
        previousHealth = health;
        if (autoReset.isEnabled() && lastActivity > 0L
                && System.currentTimeMillis() - lastActivity > (long) (resetAfter.value() * 1000.0)) {
            reset();
        }
        if (hitsLanded == 0 && hitsTaken == 0) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        String line1 = "landed " + hitsLanded + "  taken " + hitsTaken;
        String line2 = "fight " + TimeUtil.format(firstHit < 0L ? 0L : System.currentTimeMillis() - firstHit);
        event.renderer.drawText(line1, 6.0, event.height * 0.68, value, true, 0.85);
        event.renderer.drawText(line2, 6.0, event.height * 0.68 + 11.0,
                nexus.core.Nexus.instance().themes().textDim(), true, 0.8);
    }
}
