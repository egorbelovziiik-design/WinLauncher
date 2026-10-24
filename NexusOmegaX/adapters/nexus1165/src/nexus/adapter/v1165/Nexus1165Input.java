package nexus.adapter.v1165;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import nexus.api.IInput;

/** {@link IInput} implementation for Minecraft 1.16.5. */
public final class Nexus1165Input implements IInput {

    private final Nexus1165Client client;
    private double scroll;

    Nexus1165Input(Nexus1165Client client) {
        this.client = client;
    }

    private MinecraftClient handle() {
        return client.handle();
    }

    void noteScroll(double amount) {
        this.scroll += amount;
    }

    @Override
    public int mouseX() {
        return (int) (handle().mouse.getX() / handle().getWindow().getScaleFactor());
    }

    @Override
    public int mouseY() {
        return (int) (handle().mouse.getY() / handle().getWindow().getScaleFactor());
    }

    @Override
    public double scrollDelta() {
        double value = scroll;
        scroll = 0.0;
        return value;
    }

    @Override
    public boolean isKeyDown(int keyCode) {
        long window = handle().getWindow().getHandle();
        return keyCode >= 0 && keyCode < 32
                ? InputUtil.isKeyPressed(window, keyCode)
                : InputUtil.isKeyPressed(window, keyCode);
    }

    @Override
    public boolean wasKeyPressed(int keyCode) {
        return isKeyDown(keyCode);
    }

    @Override
    public boolean isMouseDown(int button) {
        return InputUtil.isKeyPressed(handle().getWindow().getHandle(), button - 100);
    }

    @Override
    public void setKeyState(int keyCode, boolean pressed) {
        net.minecraft.client.option.KeyBinding.setKeyPressed(
                InputUtil.Type.KEYSYM.createFromCode(keyCode), pressed);
    }

    @Override
    public void releaseAllKeys() {
        net.minecraft.client.option.KeyBinding.unpressAll();
    }

    @Override
    public void releaseMouse() {
        handle().mouse.unlockCursor();
    }

    @Override
    public void grabMouse() {
        handle().mouse.lockCursor();
    }

    @Override
    public boolean isMouseGrabbed() {
        return handle().mouse.isCursorLocked();
    }
}
