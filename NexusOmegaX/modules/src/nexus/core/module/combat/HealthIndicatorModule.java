package nexus.core.module.combat;

import nexus.api.IEntity;
import nexus.core.event.Render2DEvent;
import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;
import nexus.util.MathUtil;

/** Draws health bars / health values above nearby entities. */
public final class HealthIndicatorModule extends Module {

    private final ModeSetting mode;
    private final NumberSetting range;
    private final BooleanSetting playersOnly;
    private final NumberSetting scale;

    public HealthIndicatorModule() {
        super("healthindicator", "HealthIndicator", "Displays the health of nearby entities.",
                ModuleCategory.COMBAT);
        this.mode = mode("Mode", "Display mode", "Bar", "Bar", "Value", "Both");
        this.range = number("Range", "Maximum distance", 32.0, 4.0, 128.0, 1.0);
        this.playersOnly = bool("Players Only", "Only show players", true);
        this.scale = number("Scale", "Text scale", 1.0, 0.5, 2.0, 0.05);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        for (IEntity entity : mc().world().entities()) {
            if (entity.isLocalPlayer() || !entity.isAlive() || !entity.isAttackable()) {
                continue;
            }
            if (playersOnly.isEnabled() && !entity.isPlayer()) {
                continue;
            }
            double distance = entity.distanceToPlayer();
            if (distance > range.value()) {
                continue;
            }
            String label = mode.is("Bar") ? bar(entity) : String.format("%.0f", entity.health());
            if (mode.is("Both")) {
                label = bar(entity) + " " + String.format("%.0f", entity.health());
            }
            nexus.api.math.Vec3d position = entity.position().add(0.0, entity.height() + 0.35, 0.0);
            event.renderer.drawNametag(position, label,
                    ColorUtil.healthColor(entity.health(), entity.maxHealth()), scale.value(), true,
                    ColorUtil.withAlpha(0xFF08080D, 150));
        }
    }

    private String bar(IEntity entity) {
        double ratio = MathUtil.clamp(entity.health() / Math.max(1.0f, entity.maxHealth()), 0.0, 1.0);
        int filled = (int) Math.round(ratio * 10.0);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 10; i++) {
            sb.append(i < filled ? '|' : '.');
        }
        return sb.append(']').toString();
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        // no additional 2D pass needed, the labels are drawn in world space
    }
}
