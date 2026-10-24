package nexus.core.module.movement;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import nexus.core.automation.MacroManager;

/** Quality of life helpers while riding horses and other mounts. */
public final class HorseHelperModule extends Module {

    private final BooleanSetting autoSprint;
    private final BooleanSetting autoJump;
    private final BooleanSetting warnHealth;

    public HorseHelperModule() {
        super("horsehelper", "HorseHelper", "Sprint, jump and health helpers while riding.",
                ModuleCategory.MOVEMENT);
        this.autoSprint = bool("Auto Sprint", "Sprint while riding", true);
        this.autoJump = bool("Auto Jump", "Jump small obstacles automatically", true);
        this.warnHealth = bool("Warn Health", "Notify when the mount is hurt", true);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !player().isRiding()) {
            return;
        }
        if (autoSprint.isEnabled()) {
            mc().input().setKeyState(MacroManager.KeyMappingKeys.SPRINT, true);
        }
        if (autoJump.isEnabled() && player().isOnGround()
                && player().horizontalSpeed() > 0.05 && blockedAhead()) {
            mc().input().setKeyState(MacroManager.KeyMappingKeys.JUMP, true);
        }
        if (warnHealth.isEnabled() && player().health() < player().maxHealth() * 0.25f) {
            nexus.core.Nexus.instance().notifications().warn("Mount", "Mount health is low");
        }
    }

    private boolean blockedAhead() {
        double yaw = Math.toRadians(player().yaw());
        double aheadX = player().position().x - Math.sin(yaw) * 1.2;
        double aheadZ = player().position().z + Math.cos(yaw) * 1.2;
        int y = (int) Math.floor(player().position().y);
        return mc().world().isSolid((int) Math.floor(aheadX), y, (int) Math.floor(aheadZ));
    }

    @Override
    public void onDisable() {
        mc().input().setKeyState(MacroManager.KeyMappingKeys.SPRINT, false);
        mc().input().setKeyState(MacroManager.KeyMappingKeys.JUMP, false);
    }
}
