package nexus.core.module.combat;

import nexus.api.IEntity;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.core.util.TargetUtil;

/** Smoothly rotates the camera toward the closest valid target. */
public final class AimAssistModule extends Module {

    private final NumberSetting speed;
    private final NumberSetting range;
    private final NumberSetting fov;
    private final BooleanSetting players;
    private final BooleanSetting mobs;
    private final BooleanSetting requireHold;
    private final BooleanSetting onlyOnAttack;

    public AimAssistModule() {
        super("aimassist", "AimAssist", "Smoothly rotates the view toward the nearest target while attacking.",
                ModuleCategory.COMBAT);
        this.speed = number("Speed", "Rotation speed in degrees per tick", 8.0, 0.5, 30.0, 0.5);
        this.range = number("Range", "Maximum target distance", 4.5, 1.0, 8.0, 0.1);
        this.fov = number("FOV", "Maximum angle from the crosshair", 90.0, 10.0, 180.0, 5.0);
        this.players = bool("Players", "Target players", true);
        this.mobs = bool("Mobs", "Target mobs", true);
        this.requireHold = bool("Only While Attacking", "Only assist while the attack key is held", false);
        this.onlyOnAttack = bool("Only On Click", "Only assist right after an attack", false);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        if (requireHold.isEnabled() && !mc().input().isMouseDown(0)) {
            return;
        }
        if (onlyOnAttack.isEnabled() && player().attackCooldown() < 0.9) {
            return;
        }
        IEntity target = TargetUtil.bestTarget(range.value(), players.isEnabled(), mobs.isEnabled(),
                true, true, fov.value());
        if (target == null) {
            return;
        }
        player().rotateTowards(target.eyePosition(), speed.value());
        markActivity();
    }

    @Override
    public String hudInfo() {
        return String.valueOf(Math.round(speed.value()));
    }
}
