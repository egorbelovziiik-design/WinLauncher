package nexus.adapter.v1165;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import nexus.core.Nexus;
import nexus.core.event.*;

/** Collects the events posted by the 1.16.5 mixins and forwards them to the core. */
public final class Nexus1165Hooks {

    private boolean installed;
    private int failures;
    /** Set once a hook failed too often: Nexus stops touching the game. */
    private boolean safeMode;

    /** Static holder, referenced by the mixins. */
    public static final class Hooks {
        public static final Nexus1165Hooks HOOKS = new Nexus1165Hooks();

        static {
            new Nexus1165Platform().installHooks();
        }

        private Hooks() {
        }
    }

    public void install() {
        installed = true;
    }

    public void uninstall() {
        installed = false;
    }

    public boolean installed() {
        return installed;
    }

    public void onStartTick() {
        if (installed && Nexus.isInitialized()) {
            try {
                Nexus.instance().tick();
            } catch (Throwable throwable) {
                hookFailed("tick", throwable);
            }
        }
    }

    public void onRender2D(Object matrixStack, float deltaMs, int width, int height) {
        if (!installed || !Nexus.isInitialized()) {
            return;
        }
        try {
            MinecraftClient minecraft = MinecraftClient.getInstance();
            double scale = minecraft.getWindow().getScaleFactor();
            if (scale <= 0.0) {
                scale = 1.0;
            }
            int mouseX = (int) (minecraft.mouse.getX() / scale);
            int mouseY = (int) (minecraft.mouse.getY() / scale);
            Nexus1165Renderer renderer = new Nexus1165Renderer(matrixStack);
            Nexus.instance().render2D(new Render2DEvent(renderer, width, height, deltaMs, mouseX, mouseY));
        } catch (Throwable throwable) {
            hookFailed("render2D", throwable);
        }
    }

    public void onRender3D(Object matrixStack, float partialTicks) {
        if (!installed || !Nexus.isInitialized()) {
            return;
        }
        try {
            Nexus.instance().render3D(new Render3DEvent(new Nexus1165Renderer(matrixStack), 50.0f, partialTicks));
        } catch (Throwable throwable) {
            hookFailed("render3D", throwable);
        }
    }

    public boolean onKey(int key, int scanCode, int modifiers, int action) {
        if (!installed || !Nexus.isInitialized()) {
            return false;
        }
        try {
            KeyEvent.Action nexusAction = action == 0 ? KeyEvent.Action.RELEASE
                    : (action == 2 ? KeyEvent.Action.REPEAT : KeyEvent.Action.PRESS);
            boolean inGui = MinecraftClient.getInstance().currentScreen instanceof NexusScreenBridge;
            return Nexus.instance().onKey(key, scanCode, modifiers, nexusAction, inGui);
        } catch (Throwable throwable) {
            hookFailed("key", throwable);
            return false;
        }
    }

    public boolean onMouse(int button, int action, double scroll, double x, double y) {
        if (!installed || !Nexus.isInitialized()) {
            return false;
        }
        try {
            MouseEvent.Action nexusAction = action == 0 ? MouseEvent.Action.PRESS
                    : (action == 1 ? MouseEvent.Action.RELEASE : MouseEvent.Action.SCROLL);
            return Nexus.instance().onMouse(button, nexusAction, scroll, x, y);
        } catch (Throwable throwable) {
            hookFailed("mouse", throwable);
            return false;
        }
    }

    public String onChatSend(String message) {
        if (!installed || !Nexus.isInitialized()) {
            return message;
        }
        try {
            ChatSendEvent event = new ChatSendEvent(message);
            Nexus.eventBus().post(event);
            return event.message;
        } catch (Throwable throwable) {
            hookFailed("chat", throwable);
            return message;
        }
    }

    public void onWorldJoin(String address, boolean singleplayer) {
        if (installed && Nexus.isInitialized()) {
            try {
                Nexus.instance().onWorldJoin(address, singleplayer);
            } catch (Throwable throwable) {
                hookFailed("worldJoin", throwable);
            }
        }
    }

    public void onWorldLeave(String address) {
        if (installed && Nexus.isInitialized()) {
            try {
                Nexus.instance().onWorldLeave(address);
            } catch (Throwable throwable) {
                hookFailed("worldLeave", throwable);
            }
        }
    }

    public void onDeath(String cause, int score) {
        if (installed && Nexus.isInitialized()) {
            try {
                Nexus.instance().onDeath(cause, score);
            } catch (Throwable throwable) {
                hookFailed("death", throwable);
            }
        }
    }

    /** True when a Nexus interface is on screen (used by the mouse mixin). */
    public boolean ownsScreen(Screen screen) {
        return screen instanceof NexusScreenBridge;
    }

    // ------------------------------------------------------------------ safety

    /**
     * A hook must never be able to crash Minecraft. Anything escaping from our
     * code is logged (throttled) and swallowed; after a few failures the client
     * enters safe mode and stops touching the vanilla input/render paths, so
     * the game itself always stays playable.
     */
    private void hookFailed(String hook, Throwable throwable) {
        if (safeMode) {
            return;
        }
        failures++;
        if (failures == 1 || failures % 20 == 0) {
            nexus.util.Log.error("Nexus hook '" + hook + "' failed (" + failures
                    + " times): " + throwable, failures == 1 ? throwable : null);
        }
        if (failures >= 8) {
            safeMode = true;
            installed = false;
            nexus.util.Log.warn("Nexus safe mode: hooks disabled after " + failures
                    + " failures - the game keeps running, restart to retry");
            try {
                if (Nexus.isInitialized()) {
                    Nexus.instance().notifications().warn("Nexus safe mode",
                            "A hook failed " + failures + " times - client paused for this session");
                }
            } catch (Throwable ignored) {
                // notifications are optional
            }
        }
    }

    public boolean safeMode() {
        return safeMode;
    }
}
