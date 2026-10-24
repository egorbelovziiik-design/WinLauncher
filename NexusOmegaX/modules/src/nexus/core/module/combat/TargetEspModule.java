package nexus.core.module.combat;

import nexus.api.IEntity;
import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Highlights the current combat target in the world. */
public final class TargetEspModule extends Module {

    private final ColorSetting color;
    private final ModeSetting style;
    private final NumberSetting thickness;
    private final BooleanSetting tracer;

    public TargetEspModule() {
        super("targetesp", "TargetESP", "Outlines the entity you are currently fighting.",
                ModuleCategory.COMBAT);
        this.color = color("Color", "Outline color", 0xFFF87171);
        this.style = mode("Style", "Outline style", "Box", "Box", "Corner", "Glow", "Filled");
        this.thickness = number("Thickness", "Line thickness", 2.0, 0.5, 6.0, 0.5);
        this.tracer = bool("Tracer", "Draw a tracer to the target", true);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        IEntity target = mc().crosshairTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        if (style.is("Glow")) {
            event.renderer.drawGlow(target, value, 1.0);
        } else if (style.is("Corner")) {
            event.renderer.drawCornerBox(target.boundingBox(), value, 0.25, thickness.value());
        } else if (style.is("Filled")) {
            event.renderer.drawEntityBox(target, ColorUtil.withAlpha(value, 60), value, true);
        } else {
            event.renderer.drawEntityBox(target, ColorUtil.withAlpha(value, 30), value, false);
        }
        if (tracer.isEnabled()) {
            event.renderer.drawTracer(target.position().add(0.0, target.height() / 2.0, 0.0), value, thickness.value() * 0.6);
        }
    }
}
