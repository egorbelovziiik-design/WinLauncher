package nexus.core.module.misc;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Shows a large preview of the map item you hold. */
public final class MapViewerModule extends Module {

    private final NumberSetting size;
    private final BooleanSetting blackBorder;

    public MapViewerModule() {
        super("mapviewer", "MapViewer", "Shows a large preview of your held map.",
                ModuleCategory.MISC);
        this.size = number("Size", "Preview size in pixels", 256.0, 96.0, 768.0, 8.0);
        this.blackBorder = bool("Border", "Draw a border around the map", true);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!mc().isInGame() || !player().inventory().heldItem().id().contains("map")) {
            return;
        }
        double size = this.size.value();
        double x = event.width / 2.0 - size / 2.0;
        double y = event.height / 2.0 - size / 2.0;
        int[] pixels = mc().world().mapPixels(player().inventory().heldItem(), 128);
        if (pixels == null || pixels.length == 0) {
            return;
        }
        if (blackBorder.isEnabled()) {
            event.renderer.fillRoundRect(x - 6.0, y - 6.0, size + 12.0, size + 12.0, 8.0,
                    ColorUtil.withAlpha(0xFF08080D, 220));
        }
        double scale = size / 128.0;
        for (int py = 0; py < 128; py++) {
            for (int px = 0; px < 128; px++) {
                int color = pixels[py * 128 + px];
                if (color == 0) {
                    continue;
                }
                event.renderer.fillRect(x + px * scale, y + py * scale, scale, scale, color);
            }
        }
    }
}
