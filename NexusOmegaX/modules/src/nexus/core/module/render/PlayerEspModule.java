package nexus.core.module.render;

import nexus.api.IEntity;
import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Highlights players around you. */
public final class PlayerEspModule extends Module {

    private final ModeSetting style;
    private final ColorSetting color;
    private final ColorSetting friendColor;
    private final NumberSetting range;
    private final NumberSetting thickness;
    private final BooleanSetting tracers;

    public PlayerEspModule() {
        super("playeresp", "PlayerESP", "Highlights other players through walls.", ModuleCategory.RENDER);
        this.style = mode("Style", "ESP style", "BOX", RenderUtil.espModes());
        this.color = color("Color", "Player color", 0xFFC084FC);
        this.friendColor = color("Friend Color", "Color used for friends", 0xFF4ADE80);
        this.range = number("Range", "Maximum distance", 96.0, 8.0, 256.0, 1.0);
        this.thickness = number("Thickness", "Line thickness", 1.5, 0.5, 5.0, 0.1);
        this.tracers = bool("Tracers", "Draw tracers to players", false);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        RenderUtil.entities(event, mc().world(), new RenderUtil.EntityFilter() {
            @Override
            public boolean accept(IEntity entity) {
                return entity.isPlayer();
            }
        }, style.get(), color, range.value(), tracers.isEnabled(), thickness.value(), false);
    }

    @Override
    public String hudInfo() {
        return style.get();
    }
}
