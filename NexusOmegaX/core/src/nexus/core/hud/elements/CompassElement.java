package nexus.core.hud.elements;

import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;
import nexus.core.setting.NumberSetting;
import nexus.util.ColorUtil;
import nexus.util.MathUtil;

/** Rotating compass strip. */
public final class CompassElement extends HudElement {

    private static final String[] CARDINAL = new String[]{"N", "E", "S", "W"};

    private final NumberSetting stripWidth;

    public CompassElement() {
        super("compass", "Compass");
        this.stripWidth = new NumberSetting("Width", "Compass width", 120.0, 60.0, 400.0, 1.0);
    }

    @Override
    public double width() {
        return stripWidth.value() * scale();
    }

    @Override
    public double height() {
        return 16.0 * scale();
    }

    @Override
    protected void draw(Render2DEvent event, double x, double y) {
        if (!Nexus.instance().client().isInGame()) {
            return;
        }
        double s = scale();
        double w = width();
        double center = x + w / 2.0;
        float yaw = Nexus.instance().client().player().yaw();
        int accent = color(System.currentTimeMillis());

        event.renderer.fillRect(x, y, w, height(), ColorUtil.withAlpha(0xFF000000, 90));
        for (int degree = -90; degree <= 90; degree += 15) {
            double absolute = MathUtil.wrapDegrees(yaw + degree);
            double offset = degree / 90.0;
            double px = center + offset * (w / 2.0);
            boolean cardinal = Math.abs(absolute) < 1.0 || Math.abs(Math.abs(absolute) - 90.0) < 1.0
                    || Math.abs(Math.abs(absolute) - 180.0) < 1.0;
            event.renderer.fillRect(px, y + (cardinal ? 2.0 : 4.0), 1.0,
                    height() - (cardinal ? 4.0 : 8.0), ColorUtil.withAlpha(accent, cardinal ? 220 : 110));
        }
        for (int i = 0; i < CARDINAL.length; i++) {
            double targetYaw = i * 90.0;
            double delta = MathUtil.wrapDegrees(targetYaw - yaw);
            if (Math.abs(delta) > 90.0) {
                continue;
            }
            double px = center + (delta / 90.0) * (w / 2.0);
            event.renderer.drawCenteredText(CARDINAL[i], px, y + height() / 2.0 - fontHeight(s) / 2.0,
                    i == 0 ? 0xFFF87171 : 0xFFF5F3FF, shadow(), s * 0.85);
        }
        // center marker
        event.renderer.triangle(center - 3.0 * s, y, center + 3.0 * s, y, center, y + 5.0 * s, accent);
    }

    @Override
    public java.util.List<nexus.core.setting.Setting<?>> settings() {
        java.util.List<nexus.core.setting.Setting<?>> list = super.settings();
        list.add(stripWidth);
        return list;
    }

    @Override
    public String previewText() {
        return "N  E  S  W";
    }
}
