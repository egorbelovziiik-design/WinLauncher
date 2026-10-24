package nexus.core.module.render;

import nexus.api.IEntity;
import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Draws the skeleton (bones) of entities. */
public final class SkeletonEspModule extends Module {

    private final ColorSetting color;
    private final NumberSetting range;
    private final NumberSetting thickness;
    private final BooleanSetting playersOnly;

    public SkeletonEspModule() {
        super("skeletonesp", "SkeletonESP", "Draws the skeleton of entities.", ModuleCategory.RENDER);
        this.color = color("Color", "Bone color", 0xFFFFFFFF);
        this.range = number("Range", "Maximum distance", 48.0, 8.0, 128.0, 1.0);
        this.thickness = number("Thickness", "Line thickness", 1.5, 0.5, 5.0, 0.5);
        this.playersOnly = bool("Players Only", "Only draw player skeletons", true);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        for (IEntity entity : mc().world().entities()) {
            if (entity.isLocalPlayer() || !entity.isAlive()) {
                continue;
            }
            if (playersOnly.isEnabled() && !entity.isPlayer()) {
                continue;
            }
            if (entity.distanceToPlayer() > range.value()) {
                continue;
            }
            event.renderer.drawSkeleton(entity, value, thickness.value());
        }
    }
}
