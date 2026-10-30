package dev.axsesclient.mc1214.platform;

import com.mojang.blaze3d.platform.InputConstants;
import dev.axsesclient.common.platform.InputAdapter;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class Mc1214InputAdapter implements InputAdapter {
    @Override
    public boolean isKeyDown(int keyCode) {
        if (keyCode <= 0) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), keyCode);
    }

    @Override
    public boolean isMouseDown(int button) {
        if (button < 0) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        return GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), button) == GLFW.GLFW_PRESS;
    }

    @Override
    public double getMouseX() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.mouseHandler == null || mc.getWindow() == null) return 0;
        return mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / Math.max(1, mc.getWindow().getScreenWidth());
    }

    @Override
    public double getMouseY() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.mouseHandler == null || mc.getWindow() == null) return 0;
        return mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / Math.max(1, mc.getWindow().getScreenHeight());
    }
}
