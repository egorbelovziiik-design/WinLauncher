package nexus.core.module.render;

import nexus.api.IEntity;
import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;
import nexus.util.MathUtil;

/** Health bars in world space above entities. */
public final class HealthBarsModule extends Module {

    private final NumberSetting range;
    private final NumberSetting width;
    private final BooleanSetting showAbsorption;
    private final BooleanSetting background;

    public HealthBarsModule() {
        super("healthbars", "HealthBars", "Draws health bars above entities.", ModuleCategory.RENDER);
        this.range = number("Range", "Maximum distance", 32.0, 4.0, 128.0, 1.0);
        this.width = number("Width", "Bar width", 0.8, 0.3, 2.0, 0.05);
        this.showAbsorption = bool("Absorption", "Show absorption health", true);
        this.background = bool("Background", "Draw a bar background", true);
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
            if (entity.distanceToPlayer() > range.value()) {
                continue;
            }
            double ratio = MathUtil.clamp(entity.health() / Math.max(1.0f, entity.maxHealth()), 0.0, 1.0);
            double barWidth = width.value();
            double x = entity.position().x;
            double z = entity.position().z;
            double y = entity.position().y + entity.height() + 0.25;
            if (background.isEnabled()) {
                event.renderer.drawWorldQuad(new nexus.api.math.Vec3d(x - barWidth / 2.0, y, z),
                        new nexus.api.math.Vec3d(x + barWidth / 2.0, y, z),
                        new nexus.api.math.Vec3d(x + barWidth / 2.0, y + 0.09, z),
                        new nexus.api.math.Vec3d(x - barWidth / 2.0, y + 0.09, z),
                        ColorUtil.withAlpha(0xFF000000, 140));
            }
            int color = ColorUtil.healthColor(entity.health(), entity.maxHealth());
            event.renderer.drawWorldQuad(new nexus.api.math.Vec3d(x - barWidth / 2.0, y, z),
                    new nexus.api.math.Vec3d(x - barWidth / 2.0 + barWidth * ratio, y, z),
                    new nexus.api.math.Vec3d(x - barWidth / 2.0 + barWidth * ratio, y + 0.09, z),
                    new nexus.api.math.Vec3d(x - barWidth / 2.0, y + 0.09, z), color);
            if (showAbsorption.isEnabled() && entity.health() > entity.maxHealth()) {
                event.renderer.drawWorldQuad(new nexus.api.math.Vec3d(x - barWidth / 2.0, y + 0.1, z),
                        new nexus.api.math.Vec3d(x + barWidth / 2.0, y + 0.1, z),
                        new nexus.api.math.Vec3d(x + barWidth / 2.0, y + 0.14, z),
                        new nexus.api.math.Vec3d(x - barWidth / 2.0, y + 0.14, z), 0xFFFBBF24);
            }
        }
    }
}
