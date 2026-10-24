package nexus.core.hud.elements;

import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;
import nexus.core.setting.BooleanSetting;

/** Facing direction with yaw and pitch. */
public final class DirectionElement extends HudElement {

    private final BooleanSetting showAngles;

    public DirectionElement() {
        super("direction", "Direction");
        this.showAngles = new BooleanSetting("Show Angles", "Show yaw and pitch", true);
    }

    private String text() {
        if (!Nexus.instance().client().isInGame()) {
            return "Facing N";
        }
        nexus.api.IPlayer player = Nexus.instance().client().player();
        if (!showAngles.isEnabled()) {
            return "Facing " + player.facing();
        }
        double yaw = ((player.yaw() % 360.0) + 360.0) % 360.0;
        return String.format("%s (%.0f, %.0f)", player.facing(), yaw, player.pitch());
    }

    @Override
    public double width() {
        return textWidth(text(), scale());
    }

    @Override
    public double height() {
        return fontHeight(scale());
    }

    @Override
    protected void draw(Render2DEvent event, double x, double y) {
        event.renderer.drawText(text(), x, y, color(System.currentTimeMillis()), shadow(), scale());
    }

    @Override
    public java.util.List<nexus.core.setting.Setting<?>> settings() {
        java.util.List<nexus.core.setting.Setting<?>> list = super.settings();
        list.add(showAngles);
        return list;
    }

    @Override
    public String previewText() {
        return "N (0.0, 12.4)";
    }
}
