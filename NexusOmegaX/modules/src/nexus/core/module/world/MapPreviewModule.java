package nexus.core.module.world;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Draws the map you are holding as a big preview on the screen. */
public final class MapPreviewModule extends Module {

    private final NumberSetting size;
    private final NumberSetting x;
    private final NumberSetting y;
    private final BooleanSetting background;

    public MapPreviewModule() {
        super("mappreview", "MapPreview", "Draws your held map as a large preview.", ModuleCategory.WORLD);
        this.size = number("Size", "Preview size in pixels", 128.0, 48.0, 512.0, 1.0);
        this.x = number("X", "Horizontal position", 16.0, 0.0, 1920.0, 1.0);
        this.y = number("Y", "Vertical position", 220.0, 0.0, 1080.0, 1.0);
        this.background = bool("Background", "Draw a background plate", true);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!mc().isInGame() || !player().inventory().heldItem().id().contains("map")) {
            return;
        }
        double size = this.size.value();
        double posX = Math.min(x.value(), event.width - size);
        double posY = Math.min(y.value(), event.height - size);
        if (background.isEnabled()) {
            event.renderer.fillRoundRect(posX - 3.0, posY - 3.0, size + 6.0, size + 6.0, 6.0,
                    ColorUtil.withAlpha(0xFF08080D, 200));
        }
        int[] pixels = mc().world().mapPixels(player().inventory().heldItem(), 64);
        if (pixels == null || pixels.length == 0) {
            event.renderer.drawText("No map data", posX + 6.0, posY + 6.0, 0xFFC084FC, true, 1.0);
            return;
        }
        double scale = size / 64.0;
        for (int py = 0; py < 64; py++) {
            for (int px = 0; px < 64; px++) {
                int color = pixels[py * 64 + px];
                if (color == 0) {
                    continue;
                }
                event.renderer.fillRect(posX + px * scale, posY + py * scale, scale, scale, color);
            }
        }
    }
}
