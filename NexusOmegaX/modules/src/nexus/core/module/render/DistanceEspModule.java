package nexus.core.module.render;

import nexus.api.IEntity;
import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Shows the exact distance to every entity. */
public final class DistanceEspModule extends Module {

    private final NumberSetting range;
    private final ColorSetting color;
    private final BooleanSetting playersOnly;

    public DistanceEspModule() {
        super("distanceesp", "DistanceESP", "Shows the distance to entities in blocks.", ModuleCategory.RENDER);
        this.range = number("Range", "Maximum distance", 64.0, 8.0, 256.0, 1.0);
        this.color = color("Color", "Text color", 0xFFC084FC);
        this.playersOnly = bool("Players Only", "Only show distances to players", true);
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
            double distance = entity.distanceToPlayer();
            if (distance > range.value()) {
                continue;
            }
            event.renderer.drawNametag(entity.position().add(0.0, entity.height() + 0.15, 0.0),
                    String.format("%.1f", distance), value, 0.85, true, 0x9908080D);
        }
    }
}
