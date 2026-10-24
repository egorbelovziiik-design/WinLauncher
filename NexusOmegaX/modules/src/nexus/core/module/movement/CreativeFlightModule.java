package nexus.core.module.movement;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import nexus.core.automation.MacroManager;

/** Comfort controls for creative / spectator flight. */
public final class CreativeFlightModule extends Module {

    private final NumberSetting speed;
    private final BooleanSetting autoSprint;
    private final BooleanSetting hoverStop;

    public CreativeFlightModule() {
        super("creativeflight", "CreativeFlight", "Comfort controls for creative and spectator flight.",
                ModuleCategory.MOVEMENT);
        this.speed = number("Speed", "Flight speed multiplier", 1.0, 0.5, 6.0, 0.5);
        this.autoSprint = bool("Auto Sprint", "Hold sprint while flying", true);
        this.hoverStop = bool("Hover Stop", "Stop instantly when no key is pressed", false);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        boolean flying = player().isCreative() || player().isSpectator();
        if (!flying) {
            return;
        }
        if (autoSprint.isEnabled()) {
            mc().input().setKeyState(MacroManager.KeyMappingKeys.SPRINT, true);
        }
        if (speed.value() > 1.0 && player().velocity().length() > 0.01) {
            player().setVelocity(player().velocity().multiply(1.0 + (speed.value() - 1.0) * 0.15));
        }
        if (hoverStop.isEnabled() && !mc().input().isKeyDown(87) && !mc().input().isKeyDown(83)
                && !mc().input().isKeyDown(65) && !mc().input().isKeyDown(68)) {
            player().setVelocity(player().velocity().multiply(0.2));
        }
    }

    @Override
    public void onDisable() {
        mc().input().setKeyState(MacroManager.KeyMappingKeys.SPRINT, false);
    }
}
