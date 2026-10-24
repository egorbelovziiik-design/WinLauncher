package nexus.adapter.v1214;

import net.minecraft.client.Minecraft;
import nexus.core.Nexus;
import nexus.core.event.*;

/**
 * Collects the events posted by the mixins of this adapter and forwards them to
 * the client core. Keeping this in a separate class means the mixins stay tiny:
 * they only call one method here.
 * <p>
 * Every entry point is wrapped: an exception must never escape into the
 * Minecraft render/tick/input loop, otherwise the game crashes.
 */
public final class Nexus1214Hooks {

    private boolean installed;
    private int failures;
    /** Set once a hook failed too often: Nexus stops touching the game. */
    private boolean safeMode;

    private long lastTickNanos = System.nanoTime();

    public void install() {
        installed = true;
    }

    public void uninstall() {
        installed = false;
    }

    public boolean installed() {
        return installed;
    }

    /** Called from {@code MinecraftClientMixin} at the top of the tick. */
    public void onStartTick() {
        if (!installed || !Nexus.isInitialized()) {
            return;
        }
        try {
            Nexus.instance().tick();
        } catch (Throwable throwable) {
            hookFailed("tick", throwable);
        }
    }

    /** Called from {@code MinecraftClientMixin} before the HUD is drawn. */
    public void onRender2D(Object guiGraphics, float deltaMs, int width, int height) {
        if (!installed || !Nexus.isInitialized()) {
            return;
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            int screenWidth = Math.max(1, minecraft.getWindow().getScreenWidth());
            int screenHeight = Math.max(1, minecraft.getWindow().getScreenHeight());
            int mouseX = (int) (minecraft.mouseHandler.xpos() * width / screenWidth);
            int mouseY = (int) (minecraft.mouseHandler.ypos() * height / screenHeight);
            Nexus1214Renderer renderer = new Nexus1214Renderer(
                    (net.minecraft.client.gui.GuiGraphics) guiGraphics);
            Nexus.instance().render2D(new Render2DEvent(renderer, width, height, deltaMs, mouseX, mouseY));
        } catch (Throwable throwable) {
            hookFailed("render2D", throwable);
        }
    }

    /** Called from {@code WorldRenderMixin} at the end of the world render pass. */
    public void onRender3D(Object poseStack, float partialTicks) {
        if (!installed || !Nexus.isInitialized()) {
            return;
        }
        try {
            Nexus1214Renderer renderer = new Nexus1214Renderer(
                    (com.mojang.blaze3d.vertex.PoseStack) poseStack, partialTicks);
            long now = System.nanoTime();
            float deltaMs = (now - lastTickNanos) / 1000000.0f;
            lastTickNanos = now;
            Nexus.instance().render3D(new Render3DEvent(renderer, deltaMs, partialTicks));
        } catch (Throwable throwable) {
            hookFailed("render3D", throwable);
        }
    }

    /** Called from {@code KeyboardMixin}. Returns true when the key was consumed. */
    public boolean onKey(int key, int scanCode, int modifiers, int action) {
        if (!installed || !Nexus.isInitialized()) {
            return false;
        }
        try {
            KeyEvent.Action nexusAction = action == 0 ? KeyEvent.Action.RELEASE
                    : (action == 2 ? KeyEvent.Action.REPEAT : KeyEvent.Action.PRESS);
            boolean inGui = Minecraft.getInstance().screen instanceof NexusScreenBridge;
            return Nexus.instance().onKey(key, scanCode, modifiers, nexusAction, inGui);
        } catch (Throwable throwable) {
            hookFailed("key", throwable);
            return false;
        }
    }

    /** Called from {@code MouseMixin}. Returns true when the event was consumed. */
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

    /** Called when a chat message is about to be sent. */
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

    /** Called for every received chat message. Returns the (possibly rewritten) text. */
    public String onChatReceive(String message, String sender) {
        if (!installed || !Nexus.isInitialized()) {
            return message;
        }
        try {
            return Nexus.instance().onChatReceive(message, sender) ? null : message;
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
