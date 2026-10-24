package nexus.core.module.world;

import nexus.api.IEntity;
import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Finds villages by looking for villager entities and beds. */
public final class VillageFinderModule extends Module {

    private final ColorSetting color;
    private final NumberSetting range;
    private final BooleanSetting tracers;
    private final BooleanSetting countVillagers;

    public VillageFinderModule() {
        super("villagefinder", "VillageFinder", "Finds villages by their villagers.", ModuleCategory.WORLD);
        this.color = color("Color", "Highlight color", 0xFF4ADE80);
        this.range = number("Range", "Maximum distance", 96.0, 16.0, 256.0, 1.0);
        this.tracers = bool("Tracers", "Draw tracers to villagers", true);
        this.countVillagers = bool("Count", "Show the villager count", true);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        int villagers = 0;
        for (IEntity entity : mc().world().entities()) {
            String type = entity.typeId();
            if (!type.contains("villager") || !entity.isAlive()) {
                continue;
            }
            if (entity.distanceToPlayer() > range.value()) {
                continue;
            }
            villagers++;
            event.renderer.drawGlow(entity, value, 1.05);
            if (tracers.isEnabled()) {
                event.renderer.drawTracer(entity.position(), value, 1.0);
            }
        }
        if (countVillagers.isEnabled() && villagers > 0) {
            event.renderer.drawNametag(player().eyePosition().add(0.0, 1.2, 0.0),
                    "villagers nearby: " + villagers, value, 0.9, true, ColorUtil.withAlpha(0xFF08080D, 160));
        }
    }

    @Override
    public String hudInfo() {
        return tracers.isEnabled() ? "tracers" : "glow";
    }
}
