package nexus.core.module.player;

import nexus.core.event.Render2DEvent;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Casts, detects bites and recasts automatically. */
public final class AutoFishModule extends Module {

    private final NumberSetting recastDelay;
    private final BooleanSetting autoCast;
    private final BooleanSetting autoReel;
    private final BooleanSetting showCatches;

    private int catches;
    private long lastCatch;
    private boolean waiting;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();

    public AutoFishModule() {
        super("autofish", "AutoFish", "Casts and reels in automatically while fishing.",
                ModuleCategory.PLAYER);
        this.recastDelay = number("Recast Delay", "Delay before casting again in ms", 600.0, 100.0, 5000.0, 100.0);
        this.autoCast = bool("Auto Cast", "Cast the rod automatically", true);
        this.autoReel = bool("Auto Reel", "Reel in when a fish bites", true);
        this.showCatches = bool("Show Catches", "Count and display your catches", true);
    }

    @Override
    public void onEnable() {
        catches = 0;
        waiting = false;
        timer.reset();
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        if (!player().inventory().heldItem().id().contains("fishing_rod")) {
            int rod = player().inventory().findHotbar("fishing_rod");
            if (rod < 0) {
                return;
            }
            player().inventory().setSelectedSlot(rod);
            return;
        }
        if (!timer.hasPassed((long) recastDelay.value())) {
            return;
        }
        if (autoReel.isEnabled() && isBiting()) {
            player().startUsingItem();
            catches++;
            lastCatch = System.currentTimeMillis();
            waiting = true;
            timer.reset();
            markActivity();
            return;
        }
        if (waiting || (autoCast.isEnabled() && !player().isUsingItem())) {
            player().startUsingItem();
            waiting = false;
            timer.reset();
        }
    }

    /** A bite is detected through the client side bobber motion / sound hook. */
    private boolean isBiting() {
        return player().isUsingItem() && player().velocity().y < -0.02 && player().isInWater();
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!showCatches.isEnabled()) {
            return;
        }
        String text = "catches " + catches + (lastCatch > 0 ? " | " + TimeUtil.formatClock(lastCatch) : "");
        event.renderer.drawText(text, 6.0, event.height * 0.34, 0xFFC084FC, true, 0.85);
    }

    public int catches() {
        return catches;
    }
}
