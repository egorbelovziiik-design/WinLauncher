package nexus.adapter.v1165;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import nexus.api.screen.NexusScreen;

/**
 * Wraps a {@link NexusScreen} into a vanilla Minecraft 1.16.5 screen so the Nexus
 * interfaces behave exactly like a native screen: they receive the keyboard and
 * the mouse, they stop the camera and they are closed by ESC.
 * <p>
 * The method names below are the 1.16.5 yarn names (no {@code @Override} on the
 * two legacy hooks so the adapter keeps compiling across small yarn revisions).
 */
public final class NexusScreenBridge extends Screen {

    private final NexusScreen screen;

    public NexusScreenBridge(NexusScreen screen) {
        super(new LiteralText(screen.title()));
        this.screen = screen;
    }

    public NexusScreen screen() {
        return screen;
    }

    protected void init() {
        super.init();
        screen.init(width, height);
    }

    public void onClose() {
        screen.onClosed();
        super.onClose();
    }

    public void removed() {
        screen.onClosed();
    }

    public boolean shouldPause() {
        return screen.pausesGame();
    }

    @Override
    public void tick() {
        screen.tick();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        screen.render(new Nexus1165Renderer(matrices), mouseX, mouseY, delta);
        super.render(matrices, mouseX, mouseY, delta);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return screen.mouseScrolled(mouseX, mouseY, amount) || super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return screen.keyPressed(keyCode, scanCode, modifiers)
                || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return screen.charTyped(character) || super.charTyped(character, modifiers);
    }

    /** Opens a Nexus screen through the vanilla screen system. */
    public static void open(NexusScreen screen) {
        MinecraftClient.getInstance().openScreen(new NexusScreenBridge(screen));
    }
}
