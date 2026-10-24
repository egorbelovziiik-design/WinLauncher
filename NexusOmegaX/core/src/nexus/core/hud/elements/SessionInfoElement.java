package nexus.core.hud.elements;

import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;
import nexus.core.setting.BooleanSetting;
import nexus.util.TimeUtil;

/** Session duration, kills, deaths and K/D. */
public final class SessionInfoElement extends HudElement {

    private final BooleanSetting showKd;
    private final BooleanSetting showWorldTime;

    public SessionInfoElement() {
        super("sessioninfo", "SessionInfo");
        this.showKd = new BooleanSetting("Show K/D", "Show the kill/death ratio", true);
        this.showWorldTime = new BooleanSetting("Show World Time", "Show the time spent in the current world", true);
    }

    private String[] lines() {
        nexus.core.session.SessionTracker session = Nexus.instance().session();
        java.util.List<String> lines = new java.util.ArrayList<String>();
        lines.add("Session " + TimeUtil.format(session.sessionMs()));
        if (showWorldTime.isEnabled()) {
            lines.add("World " + TimeUtil.format(session.worldMs()));
        }
        lines.add("Kills " + session.kills() + "  Deaths " + session.deaths());
        if (showKd.isEnabled()) {
            lines.add(String.format("K/D %.2f", session.kdRatio()));
        }
        return lines.toArray(new String[0]);
    }

    @Override
    public double width() {
        double max = 0.0;
        for (String line : lines()) {
            max = Math.max(max, textWidth(line, scale()));
        }
        return max;
    }

    @Override
    public double height() {
        return lines().length * lineHeight();
    }

    @Override
    protected void draw(Render2DEvent event, double x, double y) {
        double s = scale();
        String[] lines = lines();
        for (int i = 0; i < lines.length; i++) {
            event.renderer.drawText(lines[i], x, y + i * lineHeight(), color(System.currentTimeMillis()), shadow(), s);
        }
    }

    @Override
    public java.util.List<nexus.core.setting.Setting<?>> settings() {
        java.util.List<nexus.core.setting.Setting<?>> list = super.settings();
        list.add(showKd);
        list.add(showWorldTime);
        return list;
    }

    @Override
    public String previewText() {
        return "Session 01:23:45";
    }
}
