package nexus.core.module.combat;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Clicks the attack button with a configurable, humanized CPS. */
public final class AutoClickerModule extends Module {

    private final NumberSetting minCps;
    private final NumberSetting maxCps;
    private final BooleanSetting onlyInGame;
    private final BooleanSetting onlyWithWeapon;
    private final BooleanSetting breakBlocks;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();
    private long nextDelay = 100L;

    public AutoClickerModule() {
        super("autoclicker", "AutoClicker", "Automatically clicks with a randomized CPS within the configured range.",
                ModuleCategory.COMBAT);
        this.minCps = number("Min CPS", "Lowest clicks per second", 8.0, 1.0, 30.0, 1.0);
        this.maxCps = number("Max CPS", "Highest clicks per second", 13.0, 1.0, 30.0, 1.0);
        this.onlyInGame = bool("Only In Game", "Only click while a world is loaded", true);
        this.onlyWithWeapon = bool("Only With Weapon", "Only click while holding a weapon", false);
        this.breakBlocks = bool("Hold To Break", "Keep clicking while the button is held instead of toggling", false);
    }

    @Override
    public void onEnable() {
        timer.reset();
        scheduleNext();
    }

    private void scheduleNext() {
        double min = Math.min(minCps.value(), maxCps.value());
        double max = Math.max(minCps.value(), maxCps.value());
        double cps = min + (max - min) * Math.random();
        nextDelay = (long) (1000.0 / Math.max(1.0, cps));
    }

    @Override
    public void onTick(TickEvent event) {
        if (onlyInGame.isEnabled() && !mc().isInGame()) {
            return;
        }
        if (breakBlocks.isEnabled() && !mc().input().isMouseDown(0)) {
            return;
        }
        if (onlyWithWeapon.isEnabled() && !player().inventory().heldItem().isWeapon()) {
            return;
        }
        if (!timer.hasPassed(nextDelay, true)) {
            return;
        }
        scheduleNext();
        mc().interaction().attackEntity(mc().crosshairTarget());
        player().swingHand();
        markActivity();
    }

    @Override
    public String hudInfo() {
        return (int) minCps.value() + "-" + (int) maxCps.value();
    }
}
