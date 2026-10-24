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
}
