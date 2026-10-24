package nexus.core.module.player;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Respawns automatically after death. */
public final class AutoRespawnModule extends Module {

    private final NumberSetting delay;
    private final BooleanSetting onlyHardcoreOff;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();

    public AutoRespawnModule() {
        super("autorespawn", "AutoRespawn", "Respawns automatically after you die.",
                ModuleCategory.PLAYER);
        this.delay = number("Delay", "Delay before respawning in ms", 800.0, 0.0, 5000.0, 100.0);
        this.onlyHardcoreOff = bool("Skip Hardcore", "Do nothing in hardcore worlds", true);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        if (!player().isDead()) {
            timer.reset();
            return;
        }
        if (onlyHardcoreOff.isEnabled() && !player().isCreative() && !player().isSpectator()
                && mc().connection().isSingleplayer() && mc().world().timeOfDay() < 0) {
            return;
        }
        if (timer.hasPassed((long) delay.value(), true)) {
            player().respawn();
            nexus.core.Nexus.instance().notifications().info("Respawn", "Respawned automatically");
            markActivity();
        }
    }
}
