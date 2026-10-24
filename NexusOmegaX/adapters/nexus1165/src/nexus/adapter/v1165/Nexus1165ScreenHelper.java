package nexus.adapter.v1165;

/**
 * Called by {@code KeyboardMixin} when a key press was consumed by the client.
 * Keeps the vanilla key map in sync so no input is stuck after the GUI closes.
 */
public final class Nexus1165ScreenHelper {

    private Nexus1165ScreenHelper() {
    }

    public static void onKeyConsumed(int key, int scanCode, int modifiers, int action) {
        if (action == 0) {
            net.minecraft.client.options.KeyBinding.setKeyState(
                    net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(key), false);
        }
    }
}
