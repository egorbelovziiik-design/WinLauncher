package nexus.core.hud.elements;

import nexus.api.IPlayer;
import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;
import nexus.core.setting.BooleanSetting;

/** XYZ coordinates and facing axis. */
public class CoordinatesElement extends HudElement {

    private final BooleanSetting showFacing;

    public CoordinatesElement() {
        this("coordinates", "Coordinates", true);
    }

    protected CoordinatesElement(String id, String name, boolean enabledByDefault) {
        super(id, name, enabledByDefault);
        this.showFacing = new BooleanSetting("Show Facing", "Append the facing axis", true);
    }

    protected String text() {
        if (!Nexus.instance().client().isInGame()) {
            return "XYZ 0 0 0";
        }
        IPlayer player = Nexus.instance().client().player();
        String base = String.format("XYZ %.0f %.0f %.0f", player.position().x, player.position().y, player.position().z);
        return showFacing.isEnabled() ? base + " [" + player.facing() + "]" : base;
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
        list.add(showFacing);
        return list;
    }

    @Override
    public String previewText() {
        return "XYZ 128 64 -256 [N]";
    }

    /** Nether / overworld coordinate conversion variant. */
    public static final class Nether extends CoordinatesElement {

        public Nether() {
            super("coordinates_nether", "Coordinates (Nether)", false);
        }

        @Override
        protected String text() {
            if (!Nexus.instance().client().isInGame()) {
                return "OW 0 0 0";
            }
            IPlayer player = Nexus.instance().client().player();
            double scale = Nexus.instance().client().world().dimensionPath().contains("nether") ? 8.0 : 0.125;
            return String.format("OW %.0f %.0f %.0f", player.position().x * scale,
                    player.position().y, player.position().z * scale);
        }

        @Override
        public String previewText() {
            return "OW 1024 64 -2048";
        }
    }
}
