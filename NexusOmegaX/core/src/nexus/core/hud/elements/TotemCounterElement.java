package nexus.core.hud.elements;

import nexus.api.IInventory;
import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;
import nexus.core.setting.BooleanSetting;

/** Counts totems of undying in the inventory. */
public final class TotemCounterElement extends HudElement {

    private final BooleanSetting showIcon;

    public TotemCounterElement() {
        super("totemcounter", "TotemCounter");
        this.showIcon = new BooleanSetting("Show Icon", "Draw a totem icon", true);
    }

    private int count() {
        if (!Nexus.instance().client().isInGame()) {
            return 0;
        }
        return Nexus.instance().client().player().inventory().count("totem_of_undying");
    }

    private String text() {
        return "Totems " + count();
    }

    @Override
    public double width() {
        return textWidth(text(), scale()) + (showIcon.isEnabled() ? 20.0 * scale() : 0.0);
    }

    @Override
    public double height() {
        return Math.max(fontHeight(scale()), 16.0 * scale());
    }

    @Override
    protected void draw(Render2DEvent event, double x, double y) {
        double s = scale();
        event.renderer.drawText(text(), x, y + 2.0, color(System.currentTimeMillis()), shadow(), s);
        if (showIcon.isEnabled()) {
            IInventory inventory = Nexus.instance().client().player().inventory();
            int slot = inventory.find("totem_of_undying");
            if (slot >= 0) {
                event.renderer.drawItemWithCount(inventory.stack(slot), x + width() - 17.0 * s, y, s);
            }
        }
    }

    @Override
    public java.util.List<nexus.core.setting.Setting<?>> settings() {
        java.util.List<nexus.core.setting.Setting<?>> list = super.settings();
        list.add(showIcon);
        return list;
    }

    @Override
    public String previewText() {
        return "Totems 3";
    }
}
