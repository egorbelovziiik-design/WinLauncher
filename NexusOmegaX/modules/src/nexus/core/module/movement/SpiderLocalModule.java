package nexus.core.module.movement;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Climbs walls like a spider (server authoritative: only works where allowed). */
public final class SpiderLocalModule extends Module {

    private final NumberSetting speed;
    private final BooleanSetting requireLook;

    public SpiderLocalModule() {
        super("spiderlocal", "SpiderLocal", "Climbs walls. Requires a server that allows it.",
                ModuleCategory.MOVEMENT);
        this.speed = number("Speed", "Climb speed", 0.2, 0.05, 1.0, 0.05);
        this.requireLook = bool("Only While Moving", "Only climb while walking into the wall", true);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || player().isOnGround() || player().isInWater()) {
            return;
        }
        double yaw = Math.toRadians(player().yaw());
        double aheadX = player().position().x - Math.sin(yaw) * 0.55;
        double aheadZ = player().position().z + Math.cos(yaw) * 0.55;
        int y = (int) Math.floor(player().position().y);
        boolean wall = mc().world().isSolid((int) Math.floor(aheadX), y, (int) Math.floor(aheadZ))
                || mc().world().isSolid((int) Math.floor(aheadX + 0.35), y, (int) Math.floor(aheadZ))
                || mc().world().isSolid((int) Math.floor(aheadX - 0.35), y, (int) Math.floor(aheadZ));
        if (!wall) {
            return;
        }
        if (requireLook.isEnabled() && !mc().input().isKeyDown(87)) {
            return;
        }
        if (player().velocity().y < speed.value()) {
            player().setVelocity(new nexus.api.math.Vec3d(player().velocity().x, speed.value(), player().velocity().z));
            markActivity();
        }
    }
}
