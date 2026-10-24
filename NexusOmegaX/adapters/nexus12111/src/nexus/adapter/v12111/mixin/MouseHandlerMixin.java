package nexus.adapter.v12111.mixin;

import nexus.adapter.v12111.Nexus12111Hooks;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import nexus.adapter.v12111.Nexus12111Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forwards mouse clicks and scrolling to the Nexus interfaces.
 * <p>
 * 1.21.11 renamed {@code onPress} to {@code onButton(long, MouseButtonInfo, int)}.
 */
@Mixin(MouseHandler.class)
public final class MouseHandlerMixin {

    @Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V", at = @At("HEAD"), cancellable = true)
    private void nexus$press(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo info) {
        try {
            if (Nexus12111Hooks.Hooks.HOOKS.onMouse(buttonInfo.button(), action, 0.0, 0.0, 0.0)) {
                info.cancel();
            }
        } catch (Throwable throwable) {
            nexus.util.Log.error("Nexus input hook failed", throwable);
        }
    }

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void nexus$scroll(long window, double horizontal, double vertical, CallbackInfo info) {
        Nexus12111Input.noteScroll(vertical);
        try {
            if (Nexus12111Hooks.Hooks.HOOKS.onMouse(-1, 2, vertical, 0.0, 0.0)) {
                info.cancel();
            }
        } catch (Throwable throwable) {
            nexus.util.Log.error("Nexus input hook failed", throwable);
        }
    }
}
