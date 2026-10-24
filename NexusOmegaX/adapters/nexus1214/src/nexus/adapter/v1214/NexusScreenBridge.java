package nexus.adapter.v1214;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import nexus.api.IScreenHost;
import nexus.api.screen.NexusScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a {@link NexusScreen} into a vanilla Minecraft screen so the Nexus
 * interfaces behave exactly like a native screen: they receive the keyboard and
 * the mouse, they stop the camera and they are closed by ESC.
 */
public final class NexusScreenBridge extends Screen {

    private final NexusScreen screen;
    private boolean closed;
    private final List<net.minecraft.client.gui.components.Renderable> renderables =
            new ArrayList<net.minecraft.client.gui.components.Renderable>();

    private final IScreenHost host;

    public NexusScreenBridge(IScreenHost host, NexusScreen screen) {
        super(Component.literal(screen.title()));
        this.host = host;
        this.screen = screen;
    }

    public NexusScreen nexusScreen() {
        return screen;
    }

    @Override
    protected void init() {
        super.init();
        screen.init(width, height);
        renderables.clear();
        renderables.add(new net.minecraft.client.gui.components.Renderable() {
            @Override
            public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            }
        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        Nexus1214Renderer renderer = new Nexus1214Renderer(graphics);
        screen.render(renderer, mouseX, mouseY, delta);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        screen.tick();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return screen.mouseClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return screen.mouseReleased(mouseX, mouseY, button) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return screen.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
                || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return screen.mouseScrolled(mouseX, mouseY, vertical) || super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return screen.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return screen.charTyped(character) || super.charTyped(character, modifiers);
    }

    @Override
    public void onClose() {
        // Route the vanilla ESC through the Nexus stack: the host pops the
        // screen, restores the vanilla screen underneath and only then drops
        // this bridge. Calling super.onClose() here would desync the stack.
        if (closed) {
            return;
        }
        closed = true;
        host.close(screen);
    }

    /** Vanilla asks whether ESC may close this screen. */
    public boolean shouldCloseOnEsc() {
        return screen.closesOnEscape();
    }

    @Override
    public boolean isPauseScreen() {
        return screen.pausesGame();
    }

}
