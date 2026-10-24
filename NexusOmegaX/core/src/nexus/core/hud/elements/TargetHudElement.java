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

/**
 * Combat panel for the current target.
 * <p>
 * The panel is only drawn while a target exists (crosshair first, then the best
 * entity in range), which is why it can stay enabled all the time: it does not
 * cover the screen when nothing is being fought.
 */
public final class TargetHudElement extends HudElement {

    private final NumberSetting range;
    private final NumberSetting panelWidth;
    private final BooleanSetting showHeldItem;
    private final BooleanSetting showDistance;
    private final BooleanSetting showPing;
    private final BooleanSetting showHealthText;
    private final BooleanSetting healthBar;

    public TargetHudElement() {
        super("targethud", "TargetHUD");
        this.range = new NumberSetting("Range", "Target search range", 24.0, 4.0, 128.0, 1.0);
        this.panelWidth = new NumberSetting("Width", "Panel width", 118.0, 70.0, 240.0, 1.0);
        this.showHeldItem = new BooleanSetting("Show Item", "Show the held item of the target", true);
        this.showDistance = new BooleanSetting("Show Distance", "Show the distance", true);
        this.showPing = new BooleanSetting("Show Ping", "Show the ping of the target", true);
        this.showHealthText = new BooleanSetting("Health Text", "Show 12.5/20 next to the bar", true);
        this.healthBar = new BooleanSetting("Health Bar", "Draw the health bar", true);
    }

    private IEntity target() {
        Nexus nexus = Nexus.instance();
        if (nexus == null || nexus.client() == null || !nexus.client().isInGame()) {
            return null;
        }
        IEntity crosshair = nexus.client().crosshairTarget();
        if (crosshair != null && crosshair.isAlive() && !crosshair.isLocalPlayer()) {
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
        return 32.0 * scale();
    }

    @Override
    protected void draw(Render2DEvent event, double x, double y) {
        IEntity target = target();
        double s = scale();
        if (target == null) {
            // outside the editor the panel disappears when there is no target
            if (isEditing()) {
                event.renderer.drawText("TargetHUD", x, y, ColorUtil.withAlpha(color(0L), 160), shadow(), s);
            }
            return;
        }
        double w = width();
        int accent = color(System.currentTimeMillis());
        int textColor = Nexus.instance().themes().text();
        int dim = Nexus.instance().themes().textDim();

        double health = Math.max(0.0f, target.health());
        double maxHealth = Math.max(1.0f, target.maxHealth());
        double ratio = MathUtil.clamp(health / maxHealth, 0.0, 1.0);
        String name = Nexus.instance().friends().protect(target.name());

        // header: name on the left, distance / ping on the right
        event.renderer.drawText(name, x, y, accent, shadow(), s);
        StringBuilder right = new StringBuilder();
        if (showDistance.isEnabled()) {
            right.append(String.format("%.1fm", target.distanceToPlayer()));
        }
        if (showPing.isEnabled() && target.ping() > 0) {
            if (right.length() > 0) {
                right.append("  ");
            }
            right.append(target.ping()).append("ms");
        }
        if (right.length() > 0) {
            double rightWidth = textWidth(right.toString(), s);
            event.renderer.drawText(right.toString(), x + w - rightWidth, y, dim, shadow(), s);
        }

        double rowHeight = fontHeight(s);
        double barY = y + rowHeight + 2.0;
        if (healthBar.isEnabled()) {
            double barHeight = Math.max(3.0, 4.0 * s);
            event.renderer.fillRect(x, barY, w, barHeight, ColorUtil.withAlpha(0xFF000000, 140));
            int barColor = ColorUtil.healthColor(target.health(), target.maxHealth());
            event.renderer.fillRect(x, barY, w * ratio, barHeight, barColor);
        }

        if (showHealthText.isEnabled()) {
            String healthText = String.format("%.1f/%.0f", health, maxHealth);
            event.renderer.drawText(healthText, x, barY + 6.0 * s, dim, shadow(), s);
        }
        if (showHeldItem.isEnabled() && target.heldItem() != null && !target.heldItem().isEmpty()) {
            event.renderer.drawItem(target.heldItem(), x + w - 15.0 * s, barY + 5.0 * s, s);
        }
    }

    @Override
    public java.util.List<nexus.core.setting.Setting<?>> settings() {
        java.util.List<nexus.core.setting.Setting<?>> list = super.settings();
        list.add(range);
        list.add(panelWidth);
        list.add(healthBar);
        list.add(showHealthText);
        list.add(showDistance);
        list.add(showPing);
        list.add(showHeldItem);
        return list;
    }

    @Override
    public String previewText() {
        return "Player123  12.4m\nHP 14/20";
    }
}
