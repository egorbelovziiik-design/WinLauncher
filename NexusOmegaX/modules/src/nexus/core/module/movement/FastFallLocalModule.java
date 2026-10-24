package nexus.core.module.movement;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Falls faster (server authoritative: only works where allowed). */
public final class FastFallLocalModule extends Module {

    private final NumberSetting speed;
    private final BooleanSetting onlyWhenFalling;

    public FastFallLocalModule() {
        super("fastfalllocal", "FastFallLocal", "Fall faster. Requires a server that allows it.",
                ModuleCategory.MOVEMENT);
        this.speed = number("Speed", "Extra fall velocity", 0.12, 0.02, 0.6, 0.02);
        this.onlyWhenFalling = bool("Only When Falling", "Only boost while already falling", true);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || player().isOnGround() || player().isFallFlying()) {
            return;
        }
        if (onlyWhenFalling.isEnabled() && player().velocity().y >= 0.0) {
            return;
        }
        player().setVelocity(player().velocity().add(0.0, -speed.value(), 0.0));
        markActivity();
    }
}
