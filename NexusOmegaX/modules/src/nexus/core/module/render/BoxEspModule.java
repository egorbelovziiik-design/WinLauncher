package nexus.core.module.render;

import nexus.api.IEntity;
import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Classic box ESP with fill and outline. */
public final class BoxEspModule extends Module {

    private final ColorSetting color;
    private final NumberSetting range;
    private final NumberSetting fillAlpha;
    private final BooleanSetting players;

    public BoxEspModule() {
        super("boxesp", "BoxESP", "Classic box ESP with a filled body.", ModuleCategory.RENDER);
        this.color = color("Color", "Box color", 0xFFC084FC);
        this.range = number("Range", "Maximum distance", 96.0, 8.0, 256.0, 1.0);
        this.fillAlpha = number("Fill Alpha", "Fill transparency", 40.0, 0.0, 255.0, 5.0);
        this.players = bool("Players", "Include players", true);
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
            if (!entity.isPlayer() && !players.isEnabled()) {
                continue;
            }
            if (entity.distanceToPlayer() > range.value()) {
                continue;
            }
            event.renderer.drawEntityBox(entity,
                    ColorUtil.withAlpha(value, (int) fillAlpha.value()), value, true);
        }
    }
}
