package nexus.api;

/** Mouse / keyboard state required by the GUI, HUD editor and keybind system. */
public interface IInput {

    /** Virtual key for the left mouse button (attack). */
    int MOUSE_LEFT = -100;
    /** Virtual key for the right mouse button (use). */
    int MOUSE_RIGHT = -101;
    /** Virtual key for the middle mouse button (pick block). */
    int MOUSE_MIDDLE = -102;

    int mouseX();

    int mouseY();

    double scrollDelta();

    boolean isKeyDown(int keyCode);

    boolean wasKeyPressed(int keyCode);

    boolean isMouseDown(int button);

    /**
     * Sets the pressed state of a movement / action key.
     * Implemented through the vanilla key binding state, exactly like pressing
     * the physical key, so movement and automation modules stay input level.
     */
    void setKeyState(int keyCode, boolean pressed);

    /** Releases every key set through {@link #setKeyState}. */
    void releaseAllKeys();

    /** Releases mouse capture back to Minecraft (used when closing the GUI). */
    void releaseMouse();

    /** Requests mouse capture (used when opening full screen interfaces). */
    void grabMouse();

    boolean isMouseGrabbed();
}
