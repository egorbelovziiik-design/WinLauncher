package nexus.core.module.movement;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Compact movement readout: speed, height, fall distance and jump count. */
public final class MovementHudModule extends Module {

    private final BooleanSetting showSpeed;
    private final BooleanSetting showHeight;
    private final BooleanSetting showFall;
    private final ColorSetting color;

    public MovementHudModule() {
        super("movementhud", "MovementHUD", "Compact movement information overlay.", ModuleCategory.MOVEMENT);
        this.showSpeed = bool("Speed", "Show the movement speed", true);
        this.showHeight = bool("Height", "Show the current height", true);
        this.showFall = bool("Fall Distance", "Show the fall distance", true);
        this.color = color("Color", "Text color", 0xFFC084FC);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        double y = event.height * 0.42;
        if (showSpeed.isEnabled()) {
            event.renderer.drawText(String.format("speed %.2f bps", player().speedBps()), 6.0, y, value, true, 0.85);
            y += 11.0;
        }
        if (showHeight.isEnabled()) {
            event.renderer.drawText(String.format("y %.1f", player().position().y), 6.0, y, value, true, 0.85);
            y += 11.0;
        }
        if (showFall.isEnabled()) {
            event.renderer.drawText(String.format("fall %.1f", player().fallDistance()), 6.0, y,
                    player().fallDistance() > 3.0f ? ColorUtil.withAlpha(0xFFF87171, 230)
                            : ColorUtil.withAlpha(value, 200), true, 0.85);
        }
    }
}
