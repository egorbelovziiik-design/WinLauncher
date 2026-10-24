package nexus.core.module.player;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Reconnects to the last server after a disconnect. */
public final class AutoReconnectModule extends Module {

    private final NumberSetting delay;
    private final NumberSetting attempts;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();
    private int tries;

    public AutoReconnectModule() {
        super("autoreconnect", "AutoReconnect", "Reconnects to the last server automatically.",
                ModuleCategory.PLAYER);
        this.delay = number("Delay", "Delay before reconnecting in ms", 3000.0, 500.0, 30000.0, 500.0);
        this.attempts = number("Attempts", "Maximum reconnect attempts", 5.0, 1.0, 50.0, 1.0);
    }

    @Override
    public void onEnable() {
        tries = 0;
        timer.reset();
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc().isInGame() && mc().connection().isConnected()) {
            tries = 0;
            timer.reset();
            return;
        }
        if (tries >= (int) attempts.value()) {
            return;
        }
        if (!timer.hasPassed((long) delay.value(), true)) {
            return;
        }
        String last = nexus.core.Nexus.instance().session().lastServer();
        if (last == null || last.isEmpty()) {
            return;
        }
        tries++;
        mc().connection().disconnect("reconnecting");
        player().sendCommand("/server " + last);
        nexus.core.Nexus.instance().notifications().info("Reconnect", "Attempt " + tries + " -> " + last);
        markActivity();
    }

    public int attemptsUsed() {
        return tries;
    }
}
