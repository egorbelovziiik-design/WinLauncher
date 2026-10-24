package nexus.core.module.render;

import nexus.api.IEntity;
import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Rich nametags with health, distance and item information. */
public final class NametagsModule extends Module {

    private final NumberSetting range;
    private final NumberSetting scale;
    private final BooleanSetting health;
    private final BooleanSetting distance;
    private final BooleanSetting heldItem;
    private final BooleanSetting background;
    private final ColorSetting backgroundColor;

    public NametagsModule() {
        super("nametags", "Nametags", "Draws detailed nametags above entities.", ModuleCategory.RENDER);
        this.range = number("Range", "Maximum distance", 64.0, 8.0, 256.0, 1.0);
        this.scale = number("Scale", "Text scale", 1.0, 0.5, 2.5, 0.05);
        this.health = bool("Health", "Show the health", true);
        this.distance = bool("Distance", "Show the distance", true);
        this.heldItem = bool("Held Item", "Show the held item", false);
        this.background = bool("Background", "Draw a background plate", true);
        this.backgroundColor = color("Background", "Plate color", 0x9908080D);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        for (IEntity entity : mc().world().entities()) {
            if (entity.isLocalPlayer() || !entity.isAlive()) {
                continue;
            }
            double distanceToPlayer = entity.distanceToPlayer();
            if (distanceToPlayer > range.value()) {
                continue;
            }
            StringBuilder sb = new StringBuilder(nexus.core.Nexus.instance().friends().protect(entity.name()));
            if (health.isEnabled()) {
                sb.append(" ").append(String.format("%.0f", entity.health()));
            }
            if (distance.isEnabled()) {
                sb.append(" ").append(String.format("%.0fm", distanceToPlayer));
            }
            if (heldItem.isEnabled() && entity.heldItem() != null && !entity.heldItem().isEmpty()) {
                sb.append(" [").append(entity.heldItem().displayName()).append(']');
            }
            int color = nexus.core.Nexus.instance().friends().isFriend(entity.name())
                    ? 0xFF4ADE80
                    : (nexus.core.Nexus.instance().friends().isEnemy(entity.name()) ? 0xFFF87171 : 0xFFF5F3FF);
            event.renderer.drawNametag(entity.position().add(0.0, entity.height() + 0.3, 0.0),
                    sb.toString(), color, scale.value(), true,
                    background.isEnabled() ? ColorUtil.withAlpha(backgroundColor.color(), 170) : 0);
        }
    }
}
