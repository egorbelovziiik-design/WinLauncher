package nexus.core.module.render;

import nexus.api.IEntity;
import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Highlights dropped items on the ground. */
public final class ItemEspModule extends Module {

    private final ModeSetting style;
    private final ColorSetting color;
    private final NumberSetting range;
    private final BooleanSetting tracers;
    private final BooleanSetting showNames;

    public ItemEspModule() {
        super("itemesp", "ItemESP", "Highlights dropped items.", ModuleCategory.RENDER);
        this.style = mode("Style", "ESP style", "BOX", RenderUtil.espModes());
        this.color = color("Color", "Item color", 0xFFFBBF24);
        this.range = number("Range", "Maximum distance", 48.0, 8.0, 128.0, 1.0);
        this.tracers = bool("Tracers", "Draw tracers to items", false);
        this.showNames = bool("Show Names", "Show the item name", true);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        RenderUtil.entities(event, mc().world(), new RenderUtil.EntityFilter() {
            @Override
            public boolean accept(IEntity entity) {
                return entity.isItem();
            }
        }, style.get(), color, range.value(), tracers.isEnabled(), 1.2, false);
        if (showNames.isEnabled()) {
            for (IEntity entity : mc().world().entities()) {
                if (!entity.isItem() || entity.distanceToPlayer() > range.value()) {
                    continue;
                }
                event.renderer.drawNametag(entity.position().add(0.0, 0.5, 0.0), entity.name(),
                        color.effectiveColor(System.currentTimeMillis()), 0.8, true, 0x9908080D);
            }
        }
    }
}
