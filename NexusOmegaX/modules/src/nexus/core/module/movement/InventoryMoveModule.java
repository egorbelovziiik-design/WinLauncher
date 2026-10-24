package nexus.core.module.movement;

import nexus.api.KeyCodes;
import nexus.core.event.KeyEvent;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import nexus.core.automation.MacroManager;

/** Keeps moving and looking around while an inventory is open. */
public final class InventoryMoveModule extends Module {

    private final BooleanSetting rotate;
    private final NumberSetting rotateSpeed;
    private final BooleanSetting sneakAtEdges;

    public InventoryMoveModule() {
        super("inventorymove", "InventoryMove", "Move and look around while a GUI is open.",
                ModuleCategory.MOVEMENT);
        this.rotate = bool("Rotate", "Allow looking around with the arrow keys", true);
        this.rotateSpeed = number("Rotate Speed", "Degrees per tick", 6.0, 1.0, 20.0, 1.0);
        this.sneakAtEdges = bool("Safe Sneak", "Sneak automatically while walking over edges", false);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        if (mc().screens().currentVanillaScreen() == null) {
            return;
        }
        mc().input().setKeyState(MacroManager.KeyMappingKeys.FORWARD, mc().input().isKeyDown(87));
        mc().input().setKeyState(MacroManager.KeyMappingKeys.BACK, mc().input().isKeyDown(83));
        mc().input().setKeyState(MacroManager.KeyMappingKeys.LEFT, mc().input().isKeyDown(65));
        mc().input().setKeyState(MacroManager.KeyMappingKeys.RIGHT, mc().input().isKeyDown(68));
        mc().input().setKeyState(MacroManager.KeyMappingKeys.JUMP, mc().input().isKeyDown(KeyCodes.SPACE));
        if (rotate.isEnabled()) {
            float yaw = player().yaw();
            float pitch = player().pitch();
            if (mc().input().isKeyDown(KeyCodes.LEFT)) {
                yaw -= rotateSpeed.floatValue();
            }
            if (mc().input().isKeyDown(KeyCodes.RIGHT)) {
                yaw += rotateSpeed.floatValue();
            }
            if (mc().input().isKeyDown(KeyCodes.UP)) {
                pitch -= rotateSpeed.floatValue();
            }
            if (mc().input().isKeyDown(KeyCodes.DOWN)) {
                pitch += rotateSpeed.floatValue();
            }
            player().setRotation(yaw, nexus.util.MathUtil.clamp(pitch, -90.0f, 90.0f));
        }
    }

    @Override
    public void onDisable() {
        mc().input().releaseAllKeys();
    }
}
