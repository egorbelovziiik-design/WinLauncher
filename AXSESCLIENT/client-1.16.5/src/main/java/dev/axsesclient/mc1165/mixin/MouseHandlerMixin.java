package dev.axsesclient.mc1165.mixin;

import dev.axsesclient.common.AxsesCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onPress", at = @At("HEAD"))
    private void axses$onMousePress(long window, int button, int action, int mods, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || window != mc.getWindow().getWindow()) return;
        if (action == GLFW.GLFW_PRESS && mc.screen == null) {
            AxsesCore core = AxsesCore.getInstance();
            if (core != null) {
                core.onInGameMouseClick(button);
            }
        }
    }
}
