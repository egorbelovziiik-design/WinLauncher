package nexus.core.event;

/** Mouse button and scroll input. */
public final class MouseEvent extends Event {

    public final int button;
    public final Action action;
    public final double scroll;
    public final double mouseX;
    public final double mouseY;

    public MouseEvent(int button, Action action, double scroll, double mouseX, double mouseY) {
        this.button = button;
        this.action = action;
        this.scroll = scroll;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    public enum Action {
        PRESS,
        RELEASE,
        SCROLL
    }
}
