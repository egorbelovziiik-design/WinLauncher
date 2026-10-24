package nexus.adapter.v1214;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import nexus.api.IInput;

import java.util.HashMap;
import java.util.Map;

/** {@link IInput} implementation for Minecraft 1.21.4. */
public final class Nexus1214Input implements IInput {

    private final Nexus1214Client client;
    private final Map<Integer, Boolean> overrides = new HashMap<Integer, Boolean>();

    Nexus1214Input(Nexus1214Client client) {
        this.client = client;
    }

    private Minecraft handle() {
        return client.handle();
    }

    @Override
    public int mouseX() {
        return (int) handle().mouseHandler.xpos();
    }

    @Override
    public int mouseY() {
        return (int) handle().mouseHandler.ypos();
    }

    @Override
    public double scrollDelta() {
        return handle().mouseHandler.getScrollXAmount() + handle().mouseHandler.getScrollYAmount();
    }

    @Override
    public boolean isKeyDown(int keyCode) {
        Boolean override = overrides.get(Integer.valueOf(keyCode));
        if (override != null) {
            return override.booleanValue();
        }
        return net.minecraft.client.InputConstants.isKeyDown(handle().getWindow().getWindow(), keyCode);
    }

    @Override
    public boolean wasKeyPressed(int keyCode) {
        return isKeyDown(keyCode);
    }

    @Override
    public boolean isMouseDown(int button) {
        return handle().mouseHandler.isMouseDown();
    }

    @Override
    public void setKeyState(int keyCode, boolean pressed) {
        overrides.put(Integer.valueOf(keyCode), Boolean.valueOf(pressed));
        if (keyCode == KeyMappingKeys.FORWARD || keyCode == KeyMappingKeys.BACK
                || keyCode == KeyMappingKeys.LEFT || keyCode == KeyMappingKeys.RIGHT
                || keyCode == KeyMappingKeys.JUMP || keyCode == KeyMappingKeys.SNEAK
                || keyCode == KeyMappingKeys.SPRINT || keyCode == KeyMappingKeys.USE) {
            setVanillaKey(keyCode, pressed);
        }
    }

    private void setVanillaKey(int keyCode, boolean pressed) {
        KeyMapping mapping = null;
        if (keyCode == KeyMappingKeys.FORWARD) {
            mapping = handle().options.keyUp;
        } else if (keyCode == KeyMappingKeys.BACK) {
            mapping = handle().options.keyDown;
        } else if (keyCode == KeyMappingKeys.LEFT) {
            mapping = handle().options.keyLeft;
        } else if (keyCode == KeyMappingKeys.RIGHT) {
            mapping = handle().options.keyRight;
        } else if (keyCode == KeyMappingKeys.JUMP) {
            mapping = handle().options.keyJump;
        } else if (keyCode == KeyMappingKeys.SNEAK) {
            mapping = handle().options.keyShift;
        } else if (keyCode == KeyMappingKeys.SPRINT) {
            mapping = handle().options.keySprint;
        } else if (keyCode == KeyMappingKeys.USE) {
            mapping = handle().options.keyUse;
        }
        if (mapping != null) {
            mapping.setDown(pressed);
        }
    }

    @Override
    public void releaseAllKeys() {
        overrides.clear();
        KeyMapping.releaseAll();
        handle().keyboardHandler.releaseAll();
    }

    @Override
    public void releaseMouse() {
        if (handle().screen == null) {
            handle().mouseHandler.releaseMouse();
        }
    }

    @Override
    public void grabMouse() {
        if (handle().screen == null) {
            handle().mouseHandler.grabMouse();
        }
    }

    @Override
    public boolean isMouseGrabbed() {
        return handle().mouseHandler.isMouseGrabbed();
    }

    /** Mapping ids used by {@link nexus.core.automation.MacroManager.KeyMappingKeys}. */
    public static final class KeyMappingKeys {
        public static final int FORWARD = -200;
        public static final int BACK = -201;
        public static final int LEFT = -202;
        public static final int RIGHT = -203;
        public static final int JUMP = -204;
        public static final int SNEAK = -205;
        public static final int SPRINT = -206;
        public static final int ATTACK = -207;
        public static final int USE = -208;
    }
}
