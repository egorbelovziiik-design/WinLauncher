package nexus.core.module.movement;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Helps with elytra flight: auto start, pitch assist and low durability warning. */
public final class ElytraHelperModule extends Module {

    private final BooleanSetting autoStart;
    private final NumberSetting startHeight;
    private final NumberSetting warnDurability;
    private final BooleanSetting levelFlight;

    public ElytraHelperModule() {
        super("elytrahelper", "ElytraHelper", "Assists elytra flight and warns about low durability.",
                ModuleCategory.MOVEMENT);
        this.autoStart = bool("Auto Start", "Activate the elytra when falling", true);
        this.startHeight = number("Start Height", "Minimum fall distance before starting", 2.0, 0.5, 20.0, 0.5);
        this.warnDurability = number("Warn At", "Warn below this durability in percent", 15.0, 1.0, 90.0, 1.0);
        this.levelFlight = bool("Level Flight", "Keep a gentle upward pitch", false);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        nexus.api.IItemStack chest = player().inventory().chestplate();
        if (chest == null || chest.isEmpty() || !chest.id().contains("elytra")) {
            return;
        }
        if (chest.isDamageable() && chest.durabilityRatio() * 100.0 <= warnDurability.value()) {
            nexus.core.Nexus.instance().notifications().warn("Elytra", "Durability low");
        }
        if (autoStart.isEnabled() && player().fallDistance() >= startHeight.value()
                && !player().isOnGround() && !player().isFallFlying()) {
            player().jump();
        }
        if (levelFlight.isEnabled() && player().isFallFlying() && player().pitch() < -4.0f) {
            player().setRotation(player().yaw(), -4.0f);
        }
    }
}
