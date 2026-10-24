package nexus.core.module.render;

import nexus.api.IEntity;
import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Highlights arrows and other projectiles in flight. */
public final class ArrowsModule extends Module {

    private final ColorSetting color;
    private final NumberSetting range;
    private final BooleanSetting tracers;

    public ArrowsModule() {
        super("arrows", "Arrows", "Highlights arrows and projectiles in flight.", ModuleCategory.RENDER);
        this.color = color("Color", "Projectile color", 0xFFFBBF24);
        this.range = number("Range", "Maximum distance", 48.0, 8.0, 128.0, 1.0);
        this.tracers = bool("Tracers", "Draw tracers to projectiles", false);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        for (IEntity entity : mc().world().entities()) {
            String type = entity.typeId();
            if (!type.contains("arrow") && !type.contains("trident") && !type.contains("fireball")
                    && !type.contains("pearl") && !type.contains("snowball")) {
                continue;
            }
            if (entity.distanceToPlayer() > range.value()) {
                continue;
            }
            event.renderer.drawEntityBox(entity, ColorUtil.withAlpha(value, 40), value, false);
            if (tracers.isEnabled()) {
                event.renderer.drawTracer(entity.position(), value, 1.0);
            }
        }
    }
}
