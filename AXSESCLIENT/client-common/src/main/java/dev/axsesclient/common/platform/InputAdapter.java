package dev.axsesclient.common.platform;

import java.util.Map;

public interface InputAdapter {
    boolean isKeyDown(int glfwKey);
    boolean isMouseButtonDown(int button);
    String getKeyName(int keyCode);
    Map<String, Integer> getVanillaKeyBindings();
}
