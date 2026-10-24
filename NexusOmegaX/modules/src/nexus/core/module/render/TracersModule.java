package nexus.core.module.render;

import nexus.api.IEntity;
import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Draws lines from the crosshair to nearby entities. */
public final class TracersModule extends Module {

    private final ColorSetting playerColor;
    private final ColorSetting mobColor;
    private final NumberSetting range;
    private final NumberSetting thickness;
    private final BooleanSetting players;
    private final BooleanSetting mobs;

    public TracersModule() {
        super("tracers", "Tracers", "Draws lines to nearby entities.", ModuleCategory.RENDER);
        this.playerColor = color("Players", "Player tracer color", 0xFFC084FC);
        this.mobColor = color("Mobs", "Mob tracer color", 0xFFF87171);
        this.range = number("Range", "Maximum distance", 96.0, 8.0, 256.0, 1.0);
        this.thickness = number("Thickness", "Line thickness", 1.2, 0.5, 5.0, 0.1);
        this.players = bool("Players", "Trace players", true);
        this.mobs = bool("Mobs", "Trace mobs", true);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (IEntity entity : mc().world().entities()) {
            if (entity.isLocalPlayer() || !entity.isAlive()) {
                continue;
            }
            boolean isPlayer = entity.isPlayer();
            if (isPlayer && !players.isEnabled()) {
                continue;
            }
            if (!isPlayer && !mobs.isEnabled()) {
                continue;
            }
            if (entity.distanceToPlayer() > range.value()) {
                continue;
            }
            int color = isPlayer ? playerColor.effectiveColor(now) : mobColor.effectiveColor(now);
            event.renderer.drawTracer(entity.position().add(0.0, entity.height() / 2.0, 0.0),
                    color, thickness.value());
        }
    }
}
