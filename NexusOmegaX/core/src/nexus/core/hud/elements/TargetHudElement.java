package nexus.core.hud.elements;

import nexus.api.IEntity;
import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;
import nexus.core.setting.BooleanSetting;
import nexus.core.setting.NumberSetting;
import nexus.core.util.TargetUtil;
import nexus.util.ColorUtil;
import nexus.util.MathUtil;

/** Information panel about the current combat target. */
public final class TargetHudElement extends HudElement {

    private final NumberSetting range;
    private final BooleanSetting showHeldItem;
    private final BooleanSetting showDistance;
    private final BooleanSetting showArmor;
    private final NumberSetting panelWidth;

    public TargetHudElement() {
        super("targethud", "TargetHUD");
        this.range = new NumberSetting("Range", "Target search range", 24.0, 4.0, 128.0, 1.0);
        this.showHeldItem = new BooleanSetting("Show Item", "Show the held item of the target", true);
        this.showDistance = new BooleanSetting("Show Distance", "Show the distance", true);
        this.showArmor = new BooleanSetting("Show Armor", "Show the armor bar", true);
        this.panelWidth = new NumberSetting("Width", "Panel width", 110.0, 70.0, 240.0, 1.0);
    }

    private IEntity target() {
        if (!Nexus.instance().client().isInGame()) {
            return null;
        }
        IEntity crosshair = Nexus.instance().client().crosshairTarget();
        if (crosshair != null && crosshair.isAlive()) {
            return crosshair;
        }
        return TargetUtil.bestTarget(range.value(), true, true, true, true, 180.0);
    }

    @Override
    public double width() {
        return panelWidth.value() * scale();
    }

    @Override
    public double height() {
        return 34.0 * scale();
    }

    @Override
    protected void draw(Render2DEvent event, double x, double y) {
        IEntity target = target();
        double s = scale();
        double w = width();
        int accent = color(System.currentTimeMillis());
        if (target == null) {
            event.renderer.drawText("No target", x, y, ColorUtil.withAlpha(accent, 140), shadow(), s);
            return;
        }
        String name = Nexus.instance().friends().protect(target.name());
        double healthRatio = MathUtil.clamp(target.health() / Math.max(1.0f, target.maxHealth()), 0.0, 1.0);

        event.renderer.drawText(name, x, y, accent, shadow(), s);
        if (showDistance.isEnabled()) {
            String distance = String.format("%.1fm", target.distanceToPlayer());
            event.renderer.drawText(distance, x + w - textWidth(distance, s * 0.9), y + 1.0,
                    Nexus.instance().themes().textDim(), shadow(), s * 0.9);
        }

        // health bar
        double barY = y + fontHeight(s) + 3.0;
        event.renderer.fillRect(x, barY, w, 6.0 * s, ColorUtil.withAlpha(0xFF000000, 120));
        event.renderer.fillRect(x, barY, w * healthRatio, 6.0 * s, ColorUtil.healthColor(target.health(), target.maxHealth()));
        event.renderer.drawText(String.format("%.0f", target.health()), x + 2.0, barY + 7.0 * s,
                Nexus.instance().themes().textDim(), shadow(), s * 0.85);

        if (showArmor.isEnabled()) {
            double armorValue = Math.min(20.0, armorValue(target)) / 20.0;
            event.renderer.fillRect(x + w * 0.55, barY + 7.0 * s, w * 0.45, 3.0 * s, ColorUtil.withAlpha(0xFF000000, 120));
            event.renderer.fillRect(x + w * 0.55, barY + 7.0 * s, w * 0.45 * armorValue, 3.0 * s, 0xFF60A5FA);
        }
        if (showHeldItem.isEnabled() && target.heldItem() != null && !target.heldItem().isEmpty()) {
            event.renderer.drawItem(target.heldItem(), x + w - 16.0 * s, barY + 8.0 * s, s);
        }
    }

    private double armorValue(IEntity entity) {
        // approximate armor from the entity equipment when exposed by the adapter
        return entity.isPlayer() ? 20.0 * (entity.health() / Math.max(1.0f, entity.maxHealth())) : 10.0;
    }

    @Override
    public java.util.List<nexus.core.setting.Setting<?>> settings() {
        java.util.List<nexus.core.setting.Setting<?>> list = super.settings();
        list.add(range);
        list.add(showHeldItem);
        list.add(showDistance);
        list.add(showArmor);
        list.add(panelWidth);
        return list;
    }

    @Override
    public String previewText() {
        return "Player123\n[======    ] 12.4";
    }
}
