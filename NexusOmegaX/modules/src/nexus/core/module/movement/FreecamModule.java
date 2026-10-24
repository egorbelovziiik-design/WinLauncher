package nexus.core.module.movement;

import nexus.api.math.Vec3d;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import nexus.core.automation.MacroManager;

/** Spectator style free camera that leaves your character where it was. */
public final class FreecamModule extends Module {

    private final NumberSetting speed;
    private final BooleanSetting freezePlayer;

    private Vec3d camera;
    private float cameraYaw;
    private float cameraPitch;
    private boolean active;

    public FreecamModule() {
        super("freecam", "Freecam", "Fly around freely while your character stays in place.",
                ModuleCategory.MOVEMENT);
        this.speed = number("Speed", "Flight speed in blocks per tick", 0.6, 0.05, 3.0, 0.05);
        this.freezePlayer = bool("Freeze Player", "Prevent the player from moving while freecam is on", true);
    }

    @Override
    public void onEnable() {
        camera = player().eyePosition();
        cameraYaw = player().yaw();
        cameraPitch = player().pitch();
        active = true;
        nexus.core.Nexus.instance().notifications().info("Freecam", "Camera detached");
    }

    @Override
    public void onTick(TickEvent event) {
        if (!active || !mc().isInGame()) {
            return;
        }
        double step = speed.value();
        double yaw = Math.toRadians(cameraYaw);
        double pitch = Math.toRadians(cameraPitch);
        Vec3d forward = new Vec3d(-Math.cos(pitch) * Math.sin(yaw), -Math.sin(pitch), Math.cos(pitch) * Math.cos(yaw));
        Vec3d right = new Vec3d(Math.cos(yaw), 0.0, Math.sin(yaw));
        Vec3d movement = new Vec3d(0.0, 0.0, 0.0);
        if (mc().input().isKeyDown(87)) {
            movement = movement.add(forward.multiply(step));
        }
        if (mc().input().isKeyDown(83)) {
            movement = movement.add(forward.multiply(-step));
        }
        if (mc().input().isKeyDown(65)) {
            movement = movement.add(right.multiply(-step));
        }
        if (mc().input().isKeyDown(68)) {
            movement = movement.add(right.multiply(step));
        }
        if (mc().input().isKeyDown(nexus.api.KeyCodes.SPACE)) {
            movement = movement.add(0.0, step, 0.0);
        }
        if (mc().input().isKeyDown(nexus.core.automation.MacroManager.KeyMappingKeys.SNEAK)
                || mc().input().isKeyDown(340)) {
            movement = movement.add(0.0, -step, 0.0);
        }
        camera = camera.add(movement);
        if (freezePlayer.isEnabled()) {
            player().setVelocity(new Vec3d(0.0, 0.0, 0.0));
        }
        markActivity();
    }

    @Override
    public void onDisable() {
        active = false;
        camera = null;
        mc().input().releaseAllKeys();
    }

    public boolean isActive() {
        return active;
    }

    public Vec3d camera() {
        return camera;
    }

    public float cameraYaw() {
        return cameraYaw;
    }

    public float cameraPitch() {
        return cameraPitch;
    }

    public void updateLook(float yaw, float pitch) {
        cameraYaw = yaw;
        cameraPitch = nexus.util.MathUtil.clamp(pitch, -90.0f, 90.0f);
    }
}
