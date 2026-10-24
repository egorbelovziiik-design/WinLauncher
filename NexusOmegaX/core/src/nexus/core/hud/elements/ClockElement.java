package nexus.core.hud.elements;

import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;
import nexus.core.setting.BooleanSetting;
import nexus.util.TimeUtil;

import java.text.SimpleDateFormat;
import java.util.Date;

/** Real world clock and in game day time. */
public final class ClockElement extends HudElement {

    private final BooleanSetting showGameTime;
    private final BooleanSetting use24Hours;

    public ClockElement() {
        super("clock", "Clock");
        this.showGameTime = new BooleanSetting("Show Game Time", "Show the in game day time", true);
        this.use24Hours = new BooleanSetting("24 Hours", "Use the 24 hour format", true);
    }

    private String realTime() {
        String pattern = use24Hours.isEnabled() ? "HH:mm:ss" : "hh:mm a";
        return new SimpleDateFormat(pattern).format(new Date());
    }

    private String gameTime() {
        if (!Nexus.instance().client().isInGame()) {
            return "--:--";
        }
        long time = Nexus.instance().client().world().timeOfDay() % 24000L;
        long hours = ((time / 1000L) + 6L) % 24L;
        long minutes = (time % 1000L) * 60L / 1000L;
        return String.format("%02d:%02d", hours, minutes);
    }

    @Override
    public double width() {
        return Math.max(textWidth(realTime(), scale()), showGameTime.isEnabled() ? textWidth(gameTime(), scale() * 0.9) : 0.0);
    }

    @Override
    public double height() {
        return showGameTime.isEnabled() ? fontHeight(scale()) + lineHeight() * 0.8 : fontHeight(scale());
    }

    @Override
    protected void draw(Render2DEvent event, double x, double y) {
        double s = scale();
        int color = color(System.currentTimeMillis());
        event.renderer.drawText(realTime(), x, y, color, shadow(), s);
        if (showGameTime.isEnabled()) {
            event.renderer.drawText("MC " + gameTime(), x, y + lineHeight(), color, shadow(), s * 0.9);
        }
    }

    @Override
    public java.util.List<nexus.core.setting.Setting<?>> settings() {
        java.util.List<nexus.core.setting.Setting<?>> list = super.settings();
        list.add(showGameTime);
        list.add(use24Hours);
        return list;
    }

    @Override
    public String previewText() {
        return "21:37:04\nMC 12:20";
    }
}
