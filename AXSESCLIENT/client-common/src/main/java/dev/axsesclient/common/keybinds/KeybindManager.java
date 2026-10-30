package dev.axsesclient.common.keybinds;

import dev.axsesclient.common.modules.Module;
import dev.axsesclient.common.modules.ModuleManager;
import dev.axsesclient.common.platform.InputAdapter;
import dev.axsesclient.common.platform.KeyCodes;
import dev.axsesclient.common.platform.MinecraftAdapter;
import dev.axsesclient.common.settings.KeybindSetting;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KeybindManager {
    private final ModuleManager moduleManager;
    private final KeybindSetting openGuiBind = new KeybindSetting("open_clickgui", "Open ClickGUI", "Opens or closes AXSES ClickGUI", KeyCodes.KEY_RIGHT_SHIFT);
    private final KeybindSetting openHudEditorBind = new KeybindSetting("open_hud_editor", "Open HUD Editor", "Opens AXSES HUD Editor directly", KeyCodes.KEY_H);

    public KeybindManager(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public KeybindSetting getOpenGuiBind() {
        return openGuiBind;
    }

    public KeybindSetting getOpenHudEditorBind() {
        return openHudEditorBind;
    }

    /**
     * Handles an in-game (no GUI open) key press event.
     * Returns true if consumed by a global AXSES shortcut.
     */
    public boolean handleInGameKeyPress(int keyCode, MinecraftAdapter mc) {
        if (keyCode <= 0) {
            return false;
        }
        if (keyCode == openGuiBind.getKeyCode()) {
            mc.openClickGui();
            return true;
        }
        if (keyCode == openHudEditorBind.getKeyCode()) {
            mc.openHudEditor();
            return true;
        }
        boolean triggered = false;
        for (Module m : moduleManager.getAll()) {
            if (m.getKeybind().getKeyCode() == keyCode) {
                moduleManager.toggleModuleWithFeedback(m, mc);
                triggered = true;
            }
        }
        return triggered;
    }

    /**
     * Detects all conflicts across AXSES shortcuts, modules, and vanilla Minecraft keybindings.
     */
    public Map<Integer, List<String>> detectConflicts(InputAdapter inputAdapter) {
        Map<Integer, List<String>> usage = new LinkedHashMap<Integer, List<String>>();
        recordBind(usage, openGuiBind.getKeyCode(), "System: Open ClickGUI");
        recordBind(usage, openHudEditorBind.getKeyCode(), "System: Open HUD Editor");

        for (Module m : moduleManager.getAll()) {
            int code = m.getKeybind().getKeyCode();
            recordBind(usage, code, "Module: " + m.getName());
        }

        if (inputAdapter != null) {
            Map<String, Integer> vanilla = inputAdapter.getVanillaKeyBindings();
            if (vanilla != null) {
                for (Map.Entry<String, Integer> entry : vanilla.entrySet()) {
                    Integer code = entry.getValue();
                    if (code != null && code.intValue() > 0 && usage.containsKey(code)) {
                        recordBind(usage, code.intValue(), "Minecraft: " + entry.getKey());
                    }
                }
            }
        }

        Map<Integer, List<String>> conflicts = new LinkedHashMap<Integer, List<String>>();
        for (Map.Entry<Integer, List<String>> e : usage.entrySet()) {
            if (e.getValue().size() > 1) {
                conflicts.put(e.getKey(), e.getValue());
            }
        }
        return conflicts;
    }

    private static void recordBind(Map<Integer, List<String>> map, int keyCode, String label) {
        if (keyCode <= 0) {
            return;
        }
        Integer key = Integer.valueOf(keyCode);
        List<String> list = map.get(key);
        if (list == null) {
            list = new ArrayList<String>();
            map.put(key, list);
        }
        list.add(label);
    }

    public void resetShortcuts() {
        openGuiBind.reset();
        openHudEditorBind.reset();
    }
}
