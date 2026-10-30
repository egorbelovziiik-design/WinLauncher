package dev.axsesclient.mc1214.platform;

import com.mojang.blaze3d.platform.InputConstants;
import dev.axsesclient.common.platform.InputAdapter;
import dev.axsesclient.common.platform.KeyCodes;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class Mc1214InputAdapter implements InputAdapter {
    @Override
    public boolean isKeyDown(int glfwKey) {
        if (glfwKey <= 0) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || mc.getWindow().getWindow() == 0L) return false;
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), glfwKey);
    }

    @Override
    public boolean isMouseButtonDown(int button) {
        if (button < 0) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || mc.getWindow().getWindow() == 0L) return false;
        return GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), button) == GLFW.GLFW_PRESS;
    }

    @Override
    public String getKeyName(int keyCode) {
        return KeyCodes.formatKey(keyCode);
    }

    @Override
    public Map<String, Integer> getVanillaKeyBindings() {
        Map<String, Integer> map = new LinkedHashMap<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.options != null && mc.options.keyMappings != null) {
            for (KeyMapping km : mc.options.keyMappings) {
                InputConstants.Key k = InputConstants.getKey(km.saveString());
                if (k != null && k.getType() == InputConstants.Type.KEYSYM && k.getValue() > 0) {
                    map.put(km.getName(), k.getValue());
                }
            }
        }
        return map;
    }
}
