package nexus.core.hud.elements;

import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;
import nexus.core.setting.ModeSetting;

/** Movement speed readout in blocks per second / km per hour. */
public final class SpeedometerElement extends HudElement {

    private final ModeSetting unit;

    public SpeedometerElement() {
        super("speedometer", "Speedometer");
        this.unit = new ModeSetting("Unit", "Displayed unit", "BPS", "BPS", "M/S", "KM/H");
    }

    private String text() {
        if (!Nexus.instance().client().isInGame()) {
            return "0.00 bps";
        }
        double bps = Nexus.instance().client().player().speedBps();
        String mode = unit.get();
        if (mode.equals("M/S")) {
            return String.format("%.2f m/s", bps);
        }
        if (mode.equals("KM/H")) {
            return String.format("%.1f km/h", bps * 3.6);
        }
        return String.format("%.2f bps", bps);
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
        list.add(unit);
        return list;
    }

    @Override
    public String previewText() {
        return "5.61 bps";
    }
}
