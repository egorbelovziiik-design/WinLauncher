package nexus.core.module.render;

import nexus.api.IEntity;
import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Shows the armor of other players above their head. */
public final class ArmorDisplayModule extends Module {

    private final NumberSetting range;
    private final BooleanSetting showDurability;

    public ArmorDisplayModule() {
        super("armordisplay", "ArmorDisplay", "Displays the armor of other players.", ModuleCategory.RENDER);
        this.range = number("Range", "Maximum distance", 32.0, 4.0, 128.0, 1.0);
        this.showDurability = bool("Durability", "Show the durability", true);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        for (IEntity entity : mc().world().entities()) {
            if (!entity.isPlayer() || entity.isLocalPlayer() || !entity.isAlive()) {
                continue;
            }
            if (entity.distanceToPlayer() > range.value()) {
                continue;
            }
            String label = showDurability.isEnabled()
                    ? "armor " + (int) (entity.health() * 2.0) + "%"
                    : "armored";
            event.renderer.drawNametag(entity.position().add(0.0, entity.height() + 0.6, 0.0),
                    label, 0xFFC084FC, 0.75, true, 0x9908080D);
        }
    }
}
