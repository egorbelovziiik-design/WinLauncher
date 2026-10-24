package nexus.adapter.v1214.mixin;

import net.minecraft.client.KeyboardHandler;
import nexus.adapter.v1214.mixin.MinecraftMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forwards every key event to the client (modules, GUI and keybinds). */
@Mixin(KeyboardHandler.class)
public final class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void nexus$key(long window, int key, int scanCode, int action, int modifiers,
                           CallbackInfoReturnable<Boolean> info) {
        if (MinecraftMixin.Hooks.HOOKS.onKey(key, scanCode, modifiers, action)) {
            info.setReturnValue(Boolean.TRUE);
        }
    }
}
