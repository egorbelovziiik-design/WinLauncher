package nexus.adapter.v12111.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import nexus.adapter.v12111.mixin.MinecraftMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forwards every key event to the client (modules, GUI and keybinds).
 * <p>
 * 1.21.11 changed the handler to {@code keyPress(long, int action, KeyEvent)},
 * so the key code and the modifiers are read from the event record.
 */
@Mixin(KeyboardHandler.class)
public final class KeyboardHandlerMixin {

    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"), cancellable = true)
    private void nexus$key(long window, int action, KeyEvent event, CallbackInfo info) {
        try {
            if (MinecraftMixin.Hooks.HOOKS.onKey(event.key(), event.scancode(), event.modifiers(), action)) {
            info.cancel();
            }
        } catch (Throwable throwable) {
            nexus.util.Log.error("Nexus input hook failed", throwable);
        }
    }
}
