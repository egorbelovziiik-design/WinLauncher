package nexus.core.module.render;

import nexus.api.IEntity;
import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Highlights mobs around you with separate colors per type. */
public final class MobEspModule extends Module {

    private final ModeSetting style;
    private final ColorSetting hostileColor;
    private final ColorSetting passiveColor;
    private final NumberSetting range;
    private final NumberSetting thickness;
    private final BooleanSetting tracers;
    private final BooleanSetting hidePassive;

    public MobEspModule() {
        super("mobesp", "MobESP", "Highlights mobs through walls.", ModuleCategory.RENDER);
        this.style = mode("Style", "ESP style", "BOX", RenderUtil.espModes());
        this.hostileColor = color("Hostile", "Hostile mob color", 0xFFF87171);
        this.passiveColor = color("Passive", "Passive mob color", 0xFF4ADE80);
        this.range = number("Range", "Maximum distance", 64.0, 8.0, 256.0, 1.0);
        this.thickness = number("Thickness", "Line thickness", 1.5, 0.5, 5.0, 0.1);
        this.tracers = bool("Tracers", "Draw tracers to mobs", false);
        this.hidePassive = bool("Hostile Only", "Only show hostile mobs", false);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        RenderUtil.entities(event, mc().world(), new RenderUtil.EntityFilter() {
            @Override
            public boolean accept(IEntity entity) {
                if (entity.isPlayer()) {
                    return false;
                }
                return !hidePassive.isEnabled() || entity.isHostile();
            }
        }, style.get(), hostileColor, range.value(), tracers.isEnabled(), thickness.value(), false);
    }

    @Override
    public String hudInfo() {
        return hidePassive.isEnabled() ? "Hostile" : "All";
    }
}
