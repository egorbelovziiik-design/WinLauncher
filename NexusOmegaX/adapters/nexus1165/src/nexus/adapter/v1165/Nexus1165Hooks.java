package nexus.adapter.v1165;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import nexus.core.Nexus;
import nexus.core.event.*;

/** Collects the events posted by the 1.16.5 mixins and forwards them to the core. */
public final class Nexus1165Hooks {

    private boolean installed;

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
            Nexus.instance().tick();
        }
    }

    public void onRender2D(Object matrixStack, float deltaMs, int width, int height) {
        if (!installed || !Nexus.isInitialized()) {
            return;
        }
        MinecraftClient minecraft = MinecraftClient.getInstance();
        double scale = minecraft.getWindow().getScaleFactor();
        int mouseX = (int) (minecraft.mouse.getX() / scale);
        int mouseY = (int) (minecraft.mouse.getY() / scale);
        Nexus1165Renderer renderer = new Nexus1165Renderer(matrixStack);
        Nexus.instance().render2D(new Render2DEvent(renderer, width, height, deltaMs, mouseX, mouseY));
    }

    public void onRender3D(Object matrixStack, float partialTicks) {
        if (!installed || !Nexus.isInitialized()) {
            return;
        }
        Nexus.instance().render3D(new Render3DEvent(new Nexus1165Renderer(matrixStack), 50.0f, partialTicks));
    }

    public boolean onKey(int key, int scanCode, int modifiers, int action) {
        if (!installed || !Nexus.isInitialized()) {
            return false;
        }
        KeyEvent.Action nexusAction = action == 0 ? KeyEvent.Action.RELEASE
                : (action == 2 ? KeyEvent.Action.REPEAT : KeyEvent.Action.PRESS);
        boolean inGui = MinecraftClient.getInstance().currentScreen instanceof NexusScreenBridge;
        return Nexus.instance().onKey(key, scanCode, modifiers, nexusAction, inGui);
    }

    public boolean onMouse(int button, int action, double scroll, double x, double y) {
        if (!installed || !Nexus.isInitialized()) {
            return false;
        }
        MouseEvent.Action nexusAction = action == 0 ? MouseEvent.Action.PRESS
                : (action == 1 ? MouseEvent.Action.RELEASE : MouseEvent.Action.SCROLL);
        return Nexus.instance().onMouse(button, nexusAction, scroll, x, y);
    }

    public String onChatSend(String message) {
        if (!installed || !Nexus.isInitialized()) {
            return message;
        }
        ChatSendEvent event = new ChatSendEvent(message);
        Nexus.eventBus().post(event);
        return event.message;
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

    /** True when a Nexus interface is on screen (used by the mouse mixin). */
    public boolean ownsScreen(Screen screen) {
        return screen instanceof NexusScreenBridge;
    }
}
