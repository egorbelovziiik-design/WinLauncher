package nexus.core.hud.elements;

import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;
import nexus.core.setting.BooleanSetting;
import nexus.core.util.ClickTracker;

/** Clicks per second (left / right / combined). */
public final class CpsElement extends HudElement {

    private final BooleanSetting splitButtons;
    private final BooleanSetting showTotal;

    public CpsElement() {
        super("cps", "CPS");
        this.splitButtons = new BooleanSetting("Split Buttons", "Show left and right separately", true);
        this.showTotal = new BooleanSetting("Show Total", "Show the total click count", false);
    }

    private String text() {
        if (splitButtons.isEnabled()) {
            return "CPS " + ClickTracker.leftCps() + " | " + ClickTracker.rightCps();
        }
        return "CPS " + ClickTracker.cps();
    }

    @Override
    public double width() {
        return textWidth(text(), scale());
    }

    @Override
    public double height() {
        return showTotal.isEnabled() ? fontHeight(scale()) * 2.0 + 2.0 : fontHeight(scale());
    }

    @Override
    protected void draw(Render2DEvent event, double x, double y) {
        double s = scale();
        event.renderer.drawText(text(), x, y, color(System.currentTimeMillis()), shadow(), s);
        if (showTotal.isEnabled()) {
            event.renderer.drawText("Clicks " + ClickTracker.totalClicks(), x, y + fontHeight(s) + 2.0,
                    color(System.currentTimeMillis()), shadow(), s * 0.9);
        }
    }

    @Override
    public java.util.List<nexus.core.setting.Setting<?>> settings() {
        java.util.List<nexus.core.setting.Setting<?>> list = super.settings();
        list.add(splitButtons);
        list.add(showTotal);
        return list;
    }

    @Override
    public String previewText() {
        return "CPS 9 | 4";
    }
}
