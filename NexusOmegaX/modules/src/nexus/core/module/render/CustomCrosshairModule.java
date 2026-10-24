package nexus.core.module.render;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Fully customizable crosshair. */
public final class CustomCrosshairModule extends Module {

    private final ModeSetting style;
    private final ColorSetting color;
    private final NumberSetting size;
    private final NumberSetting thickness;
    private final NumberSetting gap;
    private final BooleanSetting dot;
    private final BooleanSetting outline;

    public CustomCrosshairModule() {
        super("customcrosshair", "CustomCrosshair", "Replaces the vanilla crosshair.", ModuleCategory.RENDER);
        this.style = mode("Style", "Crosshair style", "Cross", "Cross", "Dot", "Circle", "Arrow", "Brackets");
        this.color = color("Color", "Crosshair color", 0xFFC084FC);
        this.size = number("Length", "Arm length in pixels", 5.0, 1.0, 20.0, 1.0);
        this.thickness = number("Thickness", "Line thickness", 1.0, 0.5, 5.0, 0.5);
        this.gap = number("Gap", "Center gap", 2.0, 0.0, 20.0, 1.0);
        this.dot = bool("Dot", "Draw a center dot", true);
        this.outline = bool("Outline", "Draw a black outline", true);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        double centerX = event.width / 2.0;
        double centerY = event.height / 2.0;
        int value = color.effectiveColor(System.currentTimeMillis());
        if (outline.isEnabled()) {
            drawShape(event, centerX, centerY, thickness.value() + 1.0, 0xCC000000);
        }
        drawShape(event, centerX, centerY, thickness.value(), value);
    }

    private void drawShape(Render2DEvent event, double cx, double cy, double thickness, int color) {
        if (style.is("Dot") || (dot.isEnabled() && style.is("Cross"))) {
            event.renderer.fillRect(cx - thickness / 2.0, cy - thickness / 2.0, thickness, thickness, color);
        }
        if (style.is("Cross")) {
            event.renderer.fillRect(cx - thickness / 2.0, cy - gap.value() - size.value(), thickness, size.value(), color);
            event.renderer.fillRect(cx - thickness / 2.0, cy + gap.value(), thickness, size.value(), color);
            event.renderer.fillRect(cx - gap.value() - size.value(), cy - thickness / 2.0, size.value(), thickness, color);
            event.renderer.fillRect(cx + gap.value(), cy - thickness / 2.0, size.value(), thickness, color);
        } else if (style.is("Circle")) {
            event.renderer.circleOutline(cx, cy, size.value(), thickness, color);
        } else if (style.is("Arrow")) {
            event.renderer.triangle(cx, cy - size.value(), cx - size.value(), cy + size.value(),
                    cx + size.value(), cy + size.value(), color);
        } else if (style.is("Brackets")) {
            event.renderer.fillRect(cx - size.value() - gap.value(), cy - thickness / 2.0, size.value(), thickness, color);
            event.renderer.fillRect(cx + gap.value(), cy - thickness / 2.0, size.value(), thickness, color);
            event.renderer.fillRect(cx - thickness / 2.0, cy - size.value() - gap.value(), thickness, size.value(), color);
            event.renderer.fillRect(cx - thickness / 2.0, cy + gap.value(), thickness, size.value(), color);
        }
    }

    public boolean hideVanilla() {
        return isEnabled();
    }

    @Override
    public String hudInfo() {
        return style.get();
    }
}
