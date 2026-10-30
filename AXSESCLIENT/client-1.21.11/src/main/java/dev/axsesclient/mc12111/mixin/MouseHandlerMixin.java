package dev.axsesclient.mc12111.mixin;

import dev.axsesclient.common.AxsesCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void axses$onMouseButton(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || window != mc.getWindow().handle()) return;
        if (action == GLFW.GLFW_PRESS && mc.screen == null && buttonInfo != null) {
            AxsesCore core = AxsesCore.getInstance();
            if (core != null) {
                core.onInGameMouseClick(buttonInfo.button());
            }
        }
    }
}
