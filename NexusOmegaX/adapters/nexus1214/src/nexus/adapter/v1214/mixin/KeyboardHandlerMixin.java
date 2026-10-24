package nexus.adapter.v1214.mixin;

import nexus.adapter.v1214.Nexus1214Hooks;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forwards every key event to the client (modules, GUI and keybinds).
 * <p>
 * {@code keyPress(long, int, int, int, int)} returns {@code void} in 1.21.4, so
 * the callback is a plain {@link CallbackInfo} - declaring a
 * {@code CallbackInfoReturnable} here makes Mixin reject the whole class.
 */
@Mixin(KeyboardHandler.class)
public final class KeyboardHandlerMixin {

    @Inject(method = "keyPress(JIIII)V", at = @At("HEAD"), cancellable = true)
    private void nexus$key(long window, int key, int scanCode, int action, int modifiers,
                           CallbackInfo info) {
        try {
            if (Nexus1214Hooks.Hooks.HOOKS.onKey(key, scanCode, modifiers, action)) {
                info.cancel();
            }
        } catch (Throwable throwable) {
            nexus.util.Log.error("Nexus input hook failed", throwable);
        }
    }
}
