package nexus.core.hud.elements;

import nexus.api.IConnection;
import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;
import nexus.util.ColorUtil;

/** Server latency indicator with a colored bar. */
public final class PingElement extends HudElement {

    public PingElement() {
        super("ping", "Ping");
    }

    private int ping() {
        if (!Nexus.instance().client().isInGame()) {
            return 0;
        }
        IConnection connection = Nexus.instance().client().connection();
        return connection.isSingleplayer() ? 0 : connection.ping();
    }

    private String text() {
        int ping = ping();
        return Nexus.instance().client().connection().isSingleplayer() ? "SP" : ping + " ms";
    }

    @Override
    public double width() {
        return textWidth(text(), scale()) + 30.0 * scale();
    }

    @Override
    public double height() {
        return fontHeight(scale());
    }

    @Override
    protected void draw(Render2DEvent event, double x, double y) {
        double s = scale();
        int ping = ping();
        int bar = ping < 60 ? 0xFF4ADE80 : (ping < 150 ? 0xFFFBBF24 : 0xFFF87171);
        event.renderer.drawText(text(), x, y, color(System.currentTimeMillis()), shadow(), s);
        double barX = x + textWidth(text(), s) + 4.0 * s;
        double barY = y + fontHeight(s) / 2.0 - 2.0 * s;
        for (int i = 0; i < 5; i++) {
            boolean filled = i < Math.max(1, Math.min(5, 5 - ping / 60));
            event.renderer.fillRect(barX + i * 5.0 * s, barY + (4 - i) * 0.0, 3.0 * s, 4.0 * s,
                    filled ? bar : ColorUtil.withAlpha(bar, 50));
        }
    }

    @Override
    public String previewText() {
        return "32 ms";
    }
}
