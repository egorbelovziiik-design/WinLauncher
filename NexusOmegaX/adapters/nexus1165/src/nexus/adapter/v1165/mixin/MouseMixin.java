package nexus.adapter.v1165.mixin;

import net.minecraft.client.Mouse;
import nexus.adapter.v1165.mixin.MinecraftClientMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forwards mouse clicks and scrolling to the Nexus interfaces. */
@Mixin(Mouse.class)
public final class MouseMixin {

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void nexus$press(long window, int button, int action, int modifiers, CallbackInfo info) {
        if (MinecraftClientMixin.Hooks.HOOKS.onMouse(button, action, 0.0, 0.0, 0.0)) {
            info.cancel();
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void nexus$scroll(long window, double horizontal, double vertical, CallbackInfo info) {
        if (MinecraftClientMixin.Hooks.HOOKS.onMouse(-1, 2, vertical, 0.0, 0.0)) {
            info.cancel();
        }
    }
}
