package nexus.core.module.render;

import nexus.api.IEntity;
import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Depth ignoring glow around entities. */
public final class GlowEspModule extends Module {

    private final ColorSetting color;
    private final NumberSetting range;
    private final NumberSetting scale;
    private final BooleanSetting includeMobs;

    public GlowEspModule() {
        super("glowesp", "GlowESP", "Glowing outline that is visible through walls.", ModuleCategory.RENDER);
        this.color = color("Color", "Glow color", 0xFFA855F7);
        this.range = number("Range", "Maximum distance", 64.0, 8.0, 256.0, 1.0);
        this.scale = number("Scale", "Glow scale", 1.05, 1.0, 1.5, 0.01);
        this.includeMobs = bool("Include Mobs", "Also glow mobs", true);
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
            if (!entity.isPlayer() && !includeMobs.isEnabled()) {
                continue;
            }
            if (entity.distanceToPlayer() > range.value()) {
                continue;
            }
            event.renderer.drawGlow(entity, value, scale.value());
        }
    }
}
