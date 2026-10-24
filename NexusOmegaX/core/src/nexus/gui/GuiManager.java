package nexus.gui;

import nexus.api.IRenderer;
import nexus.api.KeyCodes;
import nexus.api.screen.NexusScreen;
import nexus.core.Nexus;
import nexus.core.event.KeyEvent;
import nexus.core.keybind.KeybindManager;
import nexus.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the lifecycle of every Nexus interface.
 * <p>
 * Rules implemented exactly as required by the specification:
 * <ul>
 *   <li><b>RIGHT SHIFT</b> opens the ClickGUI when it is closed and closes the
 *       whole interface (including nested windows) when it is open.</li>
 *   <li><b>ESC</b> closes the topmost window first; only when no nested window
 *       is left does it close the ClickGUI itself.</li>
 *   <li>The ClickGUI can never be opened twice.</li>
 *   <li>Closing always restores mouse capture, stops GUI animations, saves the
 *       configuration and leaves no invisible window behind.</li>
 * </ul>
 */
public final class GuiManager {

    private final Nexus nexus;
    private final List<NexusScreen> stack = new ArrayList<NexusScreen>();
    private ClickGuiScreen clickGui;
    private HudEditorScreen hudEditor;
    private ConfigScreen configScreen;
    private SettingsScreen settingsScreen;
    private long lastToggleMs;

    public GuiManager(Nexus nexus) {
        this.nexus = nexus;
    }

    /** Registers the client level keybinds (ClickGUI, HUD editor, panic, ...). */
    public void registerClientBinds() {
        KeybindManager keybinds = nexus.keybinds();
        keybinds.register("client.gui", "ClickGUI", "Client", KeyCodes.RIGHT_SHIFT,
                KeybindManager.Mode.TOGGLE, new Runnable() {
                    @Override
                    public void run() {
                        toggleClickGui();
                    }
                });
        keybinds.register("client.hudeditor", "HUD Editor", "Client", KeyCodes.NONE,
                KeybindManager.Mode.TOGGLE, new Runnable() {
                    @Override
                    public void run() {
                        toggle(HudEditorScreen.class);
                    }
                });
        keybinds.register("client.configs", "Config Manager", "Client", KeyCodes.NONE,
                KeybindManager.Mode.TOGGLE, new Runnable() {
                    @Override
                    public void run() {
                        toggle(ConfigScreen.class);
                    }
                });
        keybinds.register("client.settings", "Client Settings", "Client", KeyCodes.NONE,
                KeybindManager.Mode.TOGGLE, new Runnable() {
                    @Override
                    public void run() {
                        toggle(SettingsScreen.class);
                    }
                });
        keybinds.register("client.panic", "Panic", "Client", KeyCodes.NONE,
                KeybindManager.Mode.TOGGLE, new Runnable() {
                    @Override
                    public void run() {
                        nexus.panic();
                    }
                });
    }

    // ------------------------------------------------------------------ helpers

    private ClickGuiScreen clickGui() {
        if (clickGui == null) {
            clickGui = new ClickGuiScreen(nexus);
        }
        return clickGui;
    }

    private HudEditorScreen hudEditor() {
        if (hudEditor == null) {
            hudEditor = new HudEditorScreen(nexus);
        }
        return hudEditor;
    }

    private ConfigScreen configScreen() {
        if (configScreen == null) {
            configScreen = new ConfigScreen(nexus);
        }
        return configScreen;
    }

    private SettingsScreen settingsScreen() {
        if (settingsScreen == null) {
            settingsScreen = new SettingsScreen(nexus);
        }
        return settingsScreen;
    }

    // -------------------------------------------------------------------- open

    public boolean isOpen() {
        return !stack.isEmpty();
    }

    public int stackSize() {
        return stack.size();
    }

    public NexusScreen current() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    public boolean isClickGuiOpen() {
        return stack.contains(clickGui);
    }

    public void openClickGui() {
        open(clickGui(), true);
    }

    public void openHudEditor() {
        open(hudEditor(), false);
    }

    public void openConfigManager() {
        open(configScreen(), false);
    }

    public void openSettings() {
        open(settingsScreen(), false);
    }

    /** Opens a screen, guaranteeing that it is never added to the stack twice. */
    public void open(NexusScreen screen) {
        open(screen, false);
    }

    private void open(NexusScreen screen, boolean closeOthers) {
        if (screen == null) {
            return;
        }
        if (stack.contains(screen)) {
            // already open -> bring it to the front instead of duplicating it
            stack.remove(screen);
            stack.add(screen);
            return;
        }
        if (closeOthers) {
            closeAll(false);
        }
        int width = 320;
        int height = 240;
        if (nexus.client() != null && nexus.client().window() != null) {
            width = nexus.client().window().guiWidth();
            height = nexus.client().window().guiHeight();
        }
        screen.init(width, height);
        stack.add(screen);
        nexus.client().screens().open(screen);
        if (screen.grabsMouse()) {
            nexus.client().input().releaseMouse();
        }
        Log.info("Opened interface: " + screen.title() + " (stack " + stack.size() + ")");
    }

    private void toggle(Class<?> type) {
        for (NexusScreen screen : new ArrayList<NexusScreen>(stack)) {
            if (type.isInstance(screen)) {
                close(screen);
                return;
            }
        }
        if (type == HudEditorScreen.class) {
            openHudEditor();
        } else if (type == ConfigScreen.class) {
            openConfigManager();
        } else if (type == SettingsScreen.class) {
            openSettings();
        }
    }

    public void toggleClickGui() {
        if (isClickGuiOpen()) {
            closeAll(true);
        } else {
            openClickGui();
        }
    }

    // ------------------------------------------------------------------- close

    /** Closes one screen. When the stack becomes empty the game takes over again. */
    public void close(NexusScreen screen) {
        if (!stack.remove(screen)) {
            return;
        }
        try {
            screen.onClosed();
        } catch (Throwable throwable) {
            Log.error("Screen " + screen.title() + " failed to close cleanly", throwable);
        }
        nexus.client().screens().close(screen);
        if (stack.isEmpty()) {
            onStackEmptied();
        }
    }

    /** Closes the topmost screen (ESC on nested windows). */
    public void closeTop() {
        if (stack.isEmpty()) {
            return;
        }
        close(stack.get(stack.size() - 1));
    }

    /**
     * Closes every Nexus interface (RIGHT SHIFT while the GUI is open, PANIC,
     * shutdown).
     */
    public void closeAll(boolean restoreMouse) {
        List<NexusScreen> copy = new ArrayList<NexusScreen>(stack);
        stack.clear();
        for (NexusScreen screen : copy) {
            try {
                screen.onClosed();
            } catch (Throwable throwable) {
                Log.error("Screen " + screen.title() + " failed to close cleanly", throwable);
            }
            nexus.client().screens().close(screen);
        }
        if (!copy.isEmpty()) {
            onStackEmptied(restoreMouse);
        }
    }

    /** Everything that must happen when the last Nexus screen closes. */
    private void onStackEmptied() {
        onStackEmptied(true);
    }

    private void onStackEmptied(boolean restoreMouse) {
        if (clickGui != null) {
            clickGui.resetAnimations();
        }
        if (hudEditor != null) {
            hudEditor.onClosed();
        }
        nexus.hud().setEditorOpen(false);
        if (restoreMouse && nexus.client() != null) {
            nexus.client().input().releaseAllKeys();
            nexus.client().screens().restoreMouseToGame();
        }
        if (nexus.configManager().isDirty()) {
            nexus.configManager().saveAll(true);
        }
        Log.info("All Nexus interfaces closed - mouse control returned to the game");
    }

    // -------------------------------------------------------------------- input

    /**
     * Handles GUI level keys.
     *
     * @return true when the event was consumed by the interface
     */
    public boolean onKey(KeyEvent event) {
        if (event.action != KeyEvent.Action.PRESS) {
            // while an interface is open we still swallow the release so the game
            // never sees a half processed key press
            return isOpen() && event.key == KeyCodes.RIGHT_SHIFT;
        }
        boolean open = isOpen();

        if (event.key == KeyCodes.RIGHT_SHIFT) {
            long now = System.currentTimeMillis();
            if (now - lastToggleMs < 120L) {
                return true; // debounce double events from some adapters
            }
            lastToggleMs = now;
            if (open) {
                closeAll(true);
            } else {
                openClickGui();
            }
            return true;
        }

        if (!open) {
            return false;
        }

        NexusScreen screen = current();
        if (event.key == KeyCodes.ESCAPE) {
            // 1) let the screen close its own nested window first
            if (screen != null && screen.keyPressed(event.key, event.scanCode, event.modifiers)) {
                return true;
            }
            // 2) otherwise close the topmost window (nested -> main -> closed)
            closeTop();
            return true;
        }

        // every other key belongs to the focused interface
        if (screen != null) {
            screen.keyPressed(event.key, event.scanCode, event.modifiers);
        }
        return true;
    }

    public void onTick() {
        for (NexusScreen screen : new ArrayList<NexusScreen>(stack)) {
            try {
                screen.tick();
            } catch (Throwable throwable) {
                Log.error("Screen " + screen.title() + " tick failed", throwable);
            }
        }
    }

    /** Called by the adapter when the window size changes. */
    public void onResize(int width, int height) {
        for (NexusScreen screen : stack) {
            screen.init(width, height);
        }
    }

    /** Renders the current interface stack (bottom to top). */
    public void render(IRenderer renderer, int mouseX, int mouseY, float deltaMs) {
        for (NexusScreen screen : stack) {
            screen.render(renderer, mouseX, mouseY, deltaMs);
        }
    }
}
