package nexus.core.event;

/** Keyboard input. {@code inGui} is true while a Nexus screen has focus. */
public final class KeyEvent extends Event {

    public final int key;
    public final int scanCode;
    public final int modifiers;
    public final Action action;
    public final boolean inGui;

    public KeyEvent(int key, int scanCode, int modifiers, Action action, boolean inGui) {
        this.key = key;
        this.scanCode = scanCode;
        this.modifiers = modifiers;
        this.action = action;
        this.inGui = inGui;
    }

    public boolean isPress() {
        return action == Action.PRESS;
    }

    public boolean isRelease() {
        return action == Action.RELEASE;
    }

    public enum Action {
        PRESS,
        RELEASE,
        REPEAT
    }
}
