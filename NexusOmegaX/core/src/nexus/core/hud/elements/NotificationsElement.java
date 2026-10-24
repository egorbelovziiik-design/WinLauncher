package nexus.core.hud.elements;

import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;

/** Anchor element that renders the notification stack at a HUD editable position. */
public final class NotificationsElement extends HudElement {

    public NotificationsElement() {
        super("notifications", "Notifications", true);
    }

    @Override
    public double width() {
        return Nexus.instance().notifications().stackWidth();
    }

    @Override
    public double height() {
        return Nexus.instance().notifications().stackHeight();
    }

    @Override
    protected void draw(Render2DEvent event, double x, double y) {
        Nexus.instance().notifications().renderAt(event, x, y, width());
    }

    @Override
    public String previewText() {
        return "(notifications)";
    }
}
