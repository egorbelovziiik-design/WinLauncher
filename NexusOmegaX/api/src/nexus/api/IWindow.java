package nexus.api;

/** Window and frame timing information in GUI (scaled) coordinates. */
public interface IWindow {

    /** Scaled GUI width used by ClickGUI and HUD layout coordinates. */
    int guiWidth();

    /** Scaled GUI height used by ClickGUI and HUD layout coordinates. */
    int guiHeight();

    /** Real framebuffer width in pixels. */
    int frameWidth();

    /** Real framebuffer height in pixels. */
    int frameHeight();

    int guiScale();

    /** Milliseconds since the last frame, clamped to avoid animation jumps. */
    float deltaMs();

    float deltaSeconds();

    boolean isFocused();

    boolean isFullscreen();

    /**
     * True while the given mouse button is physically held down
     * ({@code 0} = left, {@code 1} = right, {@code 2} = middle).
     * <p>
     * Used by the HUD drag handling: while the chat is open the game only
     * reports mouse events to the chat screen, so the HUD polls the physical
     * button state instead of installing its own screen hooks.
     */
    default boolean isMouseDown(int button) {
        return false;
    }
}
