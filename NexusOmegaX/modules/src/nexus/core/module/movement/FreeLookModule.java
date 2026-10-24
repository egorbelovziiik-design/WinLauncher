package nexus.core.module.movement;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Detaches the camera from the player yaw without moving the player. */
public final class FreeLookModule extends Module {

    private final NumberSetting sensitivity;
    private final BooleanSetting invertPitch;
    private float yaw;
    private float pitch;
    private boolean active;

    public FreeLookModule() {
        super("freelook", "FreeLook", "Look around freely while your character keeps facing forward.",
                ModuleCategory.MOVEMENT);
        this.sensitivity = number("Sensitivity", "Camera sensitivity", 1.0, 0.1, 4.0, 0.1);
        this.invertPitch = bool("Invert Pitch", "Invert the vertical axis", false);
    }

    @Override
    public void onEnable() {
        yaw = player().yaw();
        pitch = player().pitch();
        active = true;
    }

    @Override
    public void onTick(TickEvent event) {
        if (!active || !mc().isInGame()) {
            return;
        }
        double deltaX = mc().input().mouseX() - mc().window().guiWidth() / 2.0;
        double deltaY = mc().input().mouseY() - mc().window().guiHeight() / 2.0;
        yaw += (float) (deltaX * 0.06 * sensitivity.value());
        pitch += (float) (deltaY * 0.06 * sensitivity.value() * (invertPitch.isEnabled() ? -1.0 : 1.0));
        pitch = nexus.util.MathUtil.clamp(pitch, -90.0f, 90.0f);
    }

    @Override
    public void onDisable() {
        active = false;
        if (mc().isInGame()) {
            player().setRotation(yaw, pitch);
        }
    }

    public float cameraYaw() {
        return active ? yaw : (mc().isInGame() ? player().yaw() : 0.0f);
    }

    public float cameraPitch() {
        return active ? pitch : (mc().isInGame() ? player().pitch() : 0.0f);
    }
}
