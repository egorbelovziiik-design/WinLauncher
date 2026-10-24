package nexus.api.screen;

import nexus.api.IRenderer;

/**
 * Base class of every Nexus interface (ClickGUI, HUD editor, config manager,
 * action editor...). Version adapters wrap it into the native Minecraft screen
 * so input handling and mouse grabbing behave exactly like a vanilla screen.
 */
public abstract class NexusScreen {

    private int width;
    private int height;

    /** Called when the screen is opened or the window is resized. */
    public void init(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public void render(IRenderer renderer, int mouseX, int mouseY, float deltaMs) {
    }

    public void tick() {
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public boolean charTyped(char character) {
        return false;
    }

    public void onClosed() {
    }

    /** Whether the screen pauses the game (false for HUD editor in game). */
    public boolean pausesGame() {
        return false;
    }

    /** Whether the screen takes mouse control away from the camera. */
    public boolean grabsMouse() {
        return true;
    }

    /** Whether the vanilla ESC handling should close it. */
    public boolean closesOnEscape() {
        return true;
    }

    /** Title used by the launcher / diagnostics overlay. */
    public String title() {
        return getClass().getSimpleName();
    }
}
