package nexus.core.hud.elements;

import nexus.api.IItemStack;
import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;
import nexus.core.setting.NumberSetting;
import nexus.util.ColorUtil;
import nexus.util.MathUtil;

/** Durability bar of the held item. */
public final class DurabilityElement extends HudElement {

    private final NumberSetting barWidth;

    public DurabilityElement() {
        super("durability", "Durability", false);
        this.barWidth = new NumberSetting("Width", "Bar width", 80.0, 40.0, 200.0, 1.0);
    }

    @Override
    public double width() {
        return barWidth.value() * scale();
    }

    @Override
    public double height() {
        return fontHeight(scale()) + 6.0 * scale();
    }

    @Override
    protected void draw(Render2DEvent event, double x, double y) {
        if (!Nexus.instance().client().isInGame()) {
            return;
        }
        IItemStack stack = Nexus.instance().client().player().inventory().heldItem();
        double s = scale();
        double w = width();
        if (stack == null || stack.isEmpty() || !stack.isDamageable()) {
            event.renderer.drawText("No durability", x, y, ColorUtil.withAlpha(color(System.currentTimeMillis()), 140), shadow(), s);
            return;
        }
        double ratio = MathUtil.clamp(stack.durabilityRatio(), 0.0, 1.0);
        int barColor = ratio > 0.5 ? 0xFF4ADE80 : (ratio > 0.25 ? 0xFFFBBF24 : 0xFFF87171);
        event.renderer.drawText(stack.displayName() + " " + (stack.maxDamage() - stack.damage()), x, y,
                color(System.currentTimeMillis()), shadow(), s);
        double barY = y + fontHeight(s) + 2.0;
        event.renderer.fillRect(x, barY, w, 4.0 * s, ColorUtil.withAlpha(0xFF000000, 120));
        event.renderer.fillRect(x, barY, w * ratio, 4.0 * s, barColor);
    }

    @Override
    public java.util.List<nexus.core.setting.Setting<?>> settings() {
        java.util.List<nexus.core.setting.Setting<?>> list = super.settings();
        list.add(barWidth);
        return list;
    }

    @Override
    public String previewText() {
        return "Diamond Sword 1200";
    }
}
