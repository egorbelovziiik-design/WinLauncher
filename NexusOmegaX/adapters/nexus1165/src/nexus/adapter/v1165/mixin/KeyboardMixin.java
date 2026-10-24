package nexus.adapter.v1165.mixin;

import net.minecraft.client.Keyboard;
import nexus.adapter.v1165.Nexus1165ScreenHelper;
import nexus.adapter.v1165.mixin.MinecraftClientMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forwards every key event to the client (modules, GUI and keybinds). */
@Mixin(Keyboard.class)
public final class KeyboardMixin {

    @Inject(method = "onKey(JIIII)V", at = @At("HEAD"), cancellable = true)
    private void nexus$key(long window, int key, int scanCode, int action, int modifiers,
                           CallbackInfo info) {
        if (MinecraftClientMixin.Hooks.HOOKS.onKey(key, scanCode, modifiers, action)) {
            info.cancel();
            Nexus1165ScreenHelper.onKeyConsumed(key, scanCode, modifiers, action);
        }
    }
}
