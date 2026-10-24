package nexus.core.module.movement;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Prevents you from walking off edges by sneaking automatically. */
public final class SafeWalkModule extends Module {

    private final BooleanSetting onlyOnGround;
    private final BooleanSetting includeSneak;

    public SafeWalkModule() {
        super("safewalk", "SafeWalk", "Automatically sneaks near edges so you cannot fall off.",
                ModuleCategory.MOVEMENT);
        this.onlyOnGround = bool("Only On Ground", "Only prevent falling while grounded", true);
        this.includeSneak = bool("Force Sneak", "Hold the sneak key near edges", true);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        boolean grounded = !onlyOnGround.isEnabled() || player().isOnGround();
        boolean nearEdge = grounded && !hasGroundAhead();
        player().setSneaking(nearEdge && includeSneak.isEnabled());
    }

    private boolean hasGroundAhead() {
        double yaw = Math.toRadians(player().yaw());
        for (int i = 1; i <= 2; i++) {
            double offset = i * 0.35;
            double aheadX = player().position().x - Math.sin(yaw) * offset;
            double aheadZ = player().position().z + Math.cos(yaw) * offset;
            if (!mc().world().isSolid((int) Math.floor(aheadX),
                    (int) Math.floor(player().position().y - 1.0), (int) Math.floor(aheadZ))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onDisable() {
        player().setSneaking(false);
    }
}
