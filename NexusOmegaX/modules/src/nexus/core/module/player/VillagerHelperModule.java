package nexus.core.module.player;

import nexus.api.IEntity;
import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Highlights villagers and shows their profession. */
public final class VillagerHelperModule extends Module {

    private final NumberSetting range;
    private final BooleanSetting showProfession;
    private final ColorSetting color;

    public VillagerHelperModule() {
        super("villagerhelper", "VillagerHelper", "Highlights villagers and shows their profession.",
                ModuleCategory.PLAYER);
        this.range = number("Range", "Maximum distance", 32.0, 4.0, 96.0, 1.0);
        this.showProfession = bool("Show Profession", "Draw the profession above villagers", true);
        this.color = color("Color", "Highlight color", 0xFF4ADE80);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        for (IEntity entity : mc().world().entities()) {
            if (!entity.isAlive() || !entity.typeId().contains("villager")) {
                continue;
            }
            if (entity.distanceToPlayer() > range.value()) {
                continue;
            }
            event.renderer.drawEntityBox(entity, ColorUtil.withAlpha(value, 30), value, false);
            if (showProfession.isEnabled()) {
                String label = entity.name() + " " + String.format("%.0f", entity.health());
                event.renderer.drawNametag(entity.position().add(0.0, entity.height() + 0.3, 0.0),
                        label, value, 0.9, true, ColorUtil.withAlpha(0xFF08080D, 150));
            }
        }
    }
}
