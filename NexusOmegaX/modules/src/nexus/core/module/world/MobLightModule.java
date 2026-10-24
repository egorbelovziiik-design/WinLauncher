package nexus.core.module.world;

import nexus.api.IEntity;
import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Highlights mobs that are standing in the dark and could spawn attacks. */
public final class MobLightModule extends Module {

    private final ColorSetting color;
    private final NumberSetting range;
    private final NumberSetting threshold;

    public MobLightModule() {
        super("moblight", "MobLight", "Highlights mobs standing in the dark.", ModuleCategory.WORLD);
        this.color = color("Color", "Highlight color", 0xFFFBBF24);
        this.range = number("Range", "Maximum distance", 32.0, 4.0, 128.0, 1.0);
        this.threshold = number("Threshold", "Light level threshold", 7.0, 0.0, 15.0, 1.0);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        for (IEntity entity : mc().world().entities()) {
            if (!entity.isHostile() || !entity.isAlive()) {
                continue;
            }
            if (entity.distanceToPlayer() > range.value()) {
                continue;
            }
            int light = mc().world().lightLevel(entity.blockX(), entity.blockY(), entity.blockZ());
            if (light > threshold.value()) {
                continue;
            }
            event.renderer.drawGlow(entity, value, 1.06);
        }
    }
}
