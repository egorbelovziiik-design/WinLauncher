package nexus.core.module.render;

import nexus.api.IEntity;
import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Draws a clean outline around entities (vanilla outline style). */
public final class EntityOutlineModule extends Module {

    private final ColorSetting color;
    private final NumberSetting range;
    private final NumberSetting thickness;
    private final BooleanSetting includeMobs;

    public EntityOutlineModule() {
        super("entityoutline", "EntityOutline", "Draws outlines around entities.", ModuleCategory.RENDER);
        this.color = color("Color", "Outline color", 0xFFFFFFFF);
        this.range = number("Range", "Maximum distance", 48.0, 8.0, 128.0, 1.0);
        this.thickness = number("Thickness", "Outline width", 2.0, 0.5, 6.0, 0.5);
        this.includeMobs = bool("Include Mobs", "Also outline mobs", true);
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
            event.renderer.drawGlow(entity, value, 1.0 + thickness.value() * 0.02);
        }
    }
}
