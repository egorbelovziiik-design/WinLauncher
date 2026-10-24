package nexus.adapter.v1214;

import nexus.core.Nexus;
import nexus.core.event.*;

/**
 * Collects the events posted by the mixins of this adapter and forwards them to
 * the client core. Keeping this in a separate class means the mixins stay tiny:
 * they only call one method here.
 */
public final class Nexus1214Hooks {

    private boolean installed;
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
        Nexus.instance().tick();
    }

    /** Called from {@code MinecraftClientMixin} before the HUD is drawn. */
    public void onRender2D(Object guiGraphics, float deltaMs, int width, int height) {
        if (!installed || !Nexus.isInitialized()) {
            return;
        }
        Nexus1214Renderer renderer = new Nexus1214Renderer((net.minecraft.client.gui.GuiGraphics) guiGraphics);
        Nexus.instance().render2D(new Render2DEvent(renderer, width, height, deltaMs,
                (int) (Minecraft.getInstance().mouseHandler.xpos() * width
                        / Minecraft.getInstance().getWindow().getScreenWidth()),
                (int) (Minecraft.getInstance().mouseHandler.ypos() * height
                        / Minecraft.getInstance().getWindow().getScreenHeight())));
    }

    /** Called from {@code WorldRenderMixin} at the end of the world render pass. */
    public void onRender3D(Object poseStack, float partialTicks) {
        if (!installed || !Nexus.isInitialized()) {
            return;
        }
        Nexus1214Renderer renderer = new Nexus1214Renderer(
                (com.mojang.blaze3d.vertex.PoseStack) poseStack, partialTicks);
        long now = System.nanoTime();
        float deltaMs = (now - lastTickNanos) / 1000000.0f;
        lastTickNanos = now;
        Nexus.instance().render3D(new Render3DEvent(renderer, deltaMs, partialTicks));
    }

    /** Called from {@code KeyboardMixin}. Returns true when the key was consumed. */
    public boolean onKey(int key, int scanCode, int modifiers, int action) {
        if (!installed || !Nexus.isInitialized()) {
            return false;
        }
        KeyEvent.Action nexusAction = action == 0 ? KeyEvent.Action.RELEASE
                : (action == 2 ? KeyEvent.Action.REPEAT : KeyEvent.Action.PRESS);
        boolean inGui = Minecraft.getInstance().screen instanceof NexusScreenBridge;
        return Nexus.instance().onKey(key, scanCode, modifiers, nexusAction, inGui);
    }

    /** Called from {@code MouseMixin}. Returns true when the event was consumed. */
    public boolean onMouse(int button, int action, double scroll, double x, double y) {
        if (!installed || !Nexus.isInitialized()) {
            return false;
        }
        MouseEvent.Action nexusAction = action == 0 ? MouseEvent.Action.PRESS
                : (action == 1 ? MouseEvent.Action.RELEASE : MouseEvent.Action.SCROLL);
        return Nexus.instance().onMouse(button, nexusAction, scroll, x, y);
    }

    /** Called when a chat message is about to be sent. */
    public String onChatSend(String message) {
        if (!installed || !Nexus.isInitialized()) {
            return message;
        }
        ChatSendEvent event = new ChatSendEvent(message);
        Nexus.eventBus().post(event);
        return event.message;
    }

    /** Called for every received chat message. Returns the (possibly rewritten) text. */
    public String onChatReceive(String message, String sender) {
        if (!installed || !Nexus.isInitialized()) {
            return message;
        }
        return Nexus.instance().onChatReceive(message, sender) ? null : message;
    }

    public void onWorldJoin(String address, boolean singleplayer) {
        if (installed && Nexus.isInitialized()) {
            Nexus.instance().onWorldJoin(address, singleplayer);
        }
    }

    public void onWorldLeave(String address) {
        if (installed && Nexus.isInitialized()) {
            Nexus.instance().onWorldLeave(address);
        }
    }

    public void onDeath(String cause, int score) {
        if (installed && Nexus.isInitialized()) {
            Nexus.instance().onDeath(cause, score);
        }
    }
}
