package nexus.adapter.v12111;

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
        Nexus12111Renderer renderer = new Nexus12111Renderer(graphics);
        screen.render(renderer, mouseX, mouseY, delta);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        screen.tick();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        return screen.mouseClicked(event.x(), event.y(), event.button())
                || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        return screen.mouseReleased(event.x(), event.y(), event.button()) || super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double deltaX, double deltaY) {
        return screen.mouseDragged(event.x(), event.y(), event.button(), deltaX, deltaY)
                || super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return screen.mouseScrolled(mouseX, mouseY, vertical)
                || super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        return screen.keyPressed(event.key(), event.scancode(), event.modifiers())
                || super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        return screen.charTyped((char) event.codepoint()) || super.charTyped(event);
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
