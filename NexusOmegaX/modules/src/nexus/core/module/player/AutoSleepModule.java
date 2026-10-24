package nexus.core.module.player;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Sleeps automatically at night in a singleplayer world. */
public final class AutoSleepModule extends Module {

    private final NumberSetting startHour;
    private final NumberSetting endHour;
    private final BooleanSetting singleplayerOnly;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();

    public AutoSleepModule() {
        super("autosleep", "AutoSleep", "Sleeps automatically when night falls.", ModuleCategory.PLAYER);
        this.startHour = number("Start Hour", "Sleep after this in game hour", 18.0, 12.0, 23.0, 1.0);
        this.endHour = number("End Hour", "Stop trying after this hour", 6.0, 0.0, 12.0, 1.0);
        this.singleplayerOnly = bool("Singleplayer Only", "Only sleep in singleplayer worlds", true);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed(2000L, true)) {
            return;
        }
        if (singleplayerOnly.isEnabled() && !mc().connection().isSingleplayer()) {
            return;
        }
        if (player().isSleeping()) {
            return;
        }
        long time = mc().world().timeOfDay() % 24000L;
        double hour = time / 1000.0;
        boolean night = hour >= startHour.value() || hour <= endHour.value();
        if (!night) {
            return;
        }
        int bed = player().inventory().find("bed");
        if (bed < 0) {
            return;
        }
        if (bed >= 9) {
            player().inventory().swapWithHotbar(bed, player().inventory().selectedSlot());
        }
        player().inventory().setSelectedSlot(player().inventory().findHotbar("bed"));
        player().startUsingItem();
        markActivity();
    }
}
