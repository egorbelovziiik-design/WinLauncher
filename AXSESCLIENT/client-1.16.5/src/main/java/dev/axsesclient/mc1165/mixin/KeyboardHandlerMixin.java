package dev.axsesclient.mc1165.mixin;

import dev.axsesclient.common.AxsesCore;
import dev.axsesclient.common.platform.KeyCodes;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void axses$onKeyPress(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || window != mc.getWindow().getWindow()) return;
        if (action == GLFW.GLFW_PRESS) {
            AxsesCore core = AxsesCore.getInstance();
            if (core != null) {
                if (key == KeyCodes.KEY_F2) {
                    core.onScreenshotSaved("screenshot_" + System.currentTimeMillis() + ".png");
                }
                if (mc.screen == null && core.onInGameKeyPress(key)) {
                    ci.cancel();
                }
            }
        }
    }
}
