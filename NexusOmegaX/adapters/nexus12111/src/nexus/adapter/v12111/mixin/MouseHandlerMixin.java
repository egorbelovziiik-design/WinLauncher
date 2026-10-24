package nexus.adapter.v12111.mixin;

import net.minecraft.client.MouseHandler;
import nexus.adapter.v12111.Nexus12111Input;
import nexus.adapter.v12111.mixin.MinecraftMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forwards mouse clicks and scrolling to the Nexus interfaces. */
@Mixin(MouseHandler.class)
public final class MouseHandlerMixin {

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void nexus$press(long window, int button, int action, int modifiers, CallbackInfo info) {
        if (MinecraftMixin.Hooks.HOOKS.onMouse(button, action, 0.0, 0.0, 0.0)) {
            info.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void nexus$scroll(long window, double horizontal, double vertical, CallbackInfo info) {
        Nexus12111Input.noteScroll(vertical);
        if (MinecraftMixin.Hooks.HOOKS.onMouse(-1, 2, vertical, 0.0, 0.0)) {
            info.cancel();
        }
    }
}
