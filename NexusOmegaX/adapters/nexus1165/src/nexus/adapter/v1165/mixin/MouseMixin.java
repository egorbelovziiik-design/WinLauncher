package nexus.adapter.v1165.mixin;

import nexus.adapter.v1165.Nexus1165Hooks;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forwards mouse clicks and scrolling to the Nexus interfaces. */
@Mixin(Mouse.class)
public final class MouseMixin {

    @Inject(method = "onMouseButton(JIII)V", at = @At("HEAD"), cancellable = true)
    private void nexus$press(long window, int button, int action, int modifiers, CallbackInfo info) {
        try {
            if (Nexus1165Hooks.Hooks.HOOKS.onMouse(button, action, 0.0, 0.0, 0.0)) {
                info.cancel();
            }
        } catch (Throwable throwable) {
            nexus.util.Log.error("Nexus input hook failed", throwable);
        }
    }

    @Inject(method = "onMouseScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void nexus$scroll(long window, double horizontal, double vertical, CallbackInfo info) {
        try {
            if (Nexus1165Hooks.Hooks.HOOKS.onMouse(-1, 2, vertical, 0.0, 0.0)) {
                info.cancel();
            }
        } catch (Throwable throwable) {
            nexus.util.Log.error("Nexus input hook failed", throwable);
        }
    }
}
