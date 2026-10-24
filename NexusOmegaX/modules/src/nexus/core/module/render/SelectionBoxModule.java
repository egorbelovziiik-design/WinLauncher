package nexus.core.module.render;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Highlights the block you are looking at with a custom shape. */
public final class SelectionBoxModule extends Module {

    private final ModeSetting style;
    private final ColorSetting color;
    private final NumberSetting expand;

    public SelectionBoxModule() {
        super("selectionbox", "SelectionBox", "Highlights the targeted block.", ModuleCategory.RENDER);
        this.style = mode("Style", "Highlight style", "Corner", "Corner", "Full", "Filled");
        this.color = color("Color", "Selection color", 0xFFC084FC);
        this.expand = number("Expand", "Expansion of the box", 0.002, 0.0, 0.1, 0.001);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        nexus.api.math.Vec3d hit = mc().interaction().hitPosition();
        if (hit == null) {
            return;
        }
        int x = (int) Math.floor(hit.x);
        int y = (int) Math.floor(hit.y);
        int z = (int) Math.floor(hit.z);
        nexus.api.math.Box3 box = nexus.api.math.Box3.block(x, y, z).expand(expand.value());
        int value = color.effectiveColor(System.currentTimeMillis());
        if (style.is("Corner")) {
            event.renderer.drawCornerBox(box, value, 0.25, 2.0);
        } else if (style.is("Filled")) {
            event.renderer.drawBox(box, ColorUtil.withAlpha(value, 60), value, true);
        } else {
            event.renderer.drawBox(box, 0x00000000, value, false);
        }
    }

    @Override
    public String hudInfo() {
        return style.get();
    }
}
