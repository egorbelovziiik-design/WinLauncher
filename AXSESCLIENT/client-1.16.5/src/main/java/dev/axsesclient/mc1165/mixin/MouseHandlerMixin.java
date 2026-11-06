package dev.axsesclient.mc1165.mixin;

import dev.axsesclient.common.AxsesCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void axses$onMousePress(long window, int button, int action, int mods, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || window != mc.getWindow().getWindow()) return;
        AxsesCore core = AxsesCore.getInstance();
        if (core == null) return;

        if (mc.screen instanceof ChatScreen) {
            double mx = mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth() / Math.max(1.0, (double) mc.getWindow().getScreenWidth());
            double my = mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / Math.max(1.0, (double) mc.getWindow().getScreenHeight());
            if (action == GLFW.GLFW_PRESS) {
                if (core.onChatHudMousePress(mx, my, button)) {
                    ci.cancel();
                }
            } else if (action == GLFW.GLFW_RELEASE) {
                core.onChatHudMouseRelease(mx, my, button);
            }
            return;
        }

        if (action == GLFW.GLFW_PRESS && mc.screen == null) {
            core.onInGameMouseClick(button);
        }
    }
}
