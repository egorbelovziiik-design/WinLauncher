package nexus.core.module.movement;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Walks up blocks without jumping (client assisted step height). */
public final class StepModule extends Module {

    private final NumberSetting height;
    private final BooleanSetting onlySprinting;

    public StepModule() {
        super("step", "Step", "Steps up blocks automatically without jumping.", ModuleCategory.MOVEMENT);
        this.height = number("Height", "Maximum step height in blocks", 1.0, 0.6, 2.0, 0.1);
        this.onlySprinting = bool("Only Sprinting", "Only step up while sprinting", false);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !player().isOnGround()) {
            return;
        }
        if (onlySprinting.isEnabled() && !player().isSprinting()) {
            return;
        }
        if (player().velocity().y > 0.0) {
            return;
        }
        double yaw = Math.toRadians(player().yaw());
        double aheadX = player().position().x - Math.sin(yaw) * 0.6;
        double aheadZ = player().position().z + Math.cos(yaw) * 0.6;
        int footY = (int) Math.floor(player().position().y);
        for (double step = 0.6; step <= height.value(); step += 0.2) {
            int blockY = footY + (int) Math.ceil(step);
            if (mc().world().isSolid((int) Math.floor(aheadX), blockY, (int) Math.floor(aheadZ))) {
                continue;
            }
            if (!mc().world().isSolid((int) Math.floor(aheadX), footY, (int) Math.floor(aheadZ))) {
                continue;
            }
            player().setVelocity(player().velocity().add(0.0, 0.42, 0.0));
            markActivity();
            return;
        }
    }

    @Override
    public String hudInfo() {
        return String.valueOf(nexus.util.MathUtil.round(height.value(), 1));
    }
}
