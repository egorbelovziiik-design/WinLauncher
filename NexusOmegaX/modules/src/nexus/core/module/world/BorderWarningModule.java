package nexus.core.module.world;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;
import nexus.util.MathUtil;

/** Warns you when you approach the world border. */
public final class BorderWarningModule extends Module {

    private final NumberSetting distance;
    private final BooleanSetting tintScreen;
    private final BooleanSetting notifyChat;

    public BorderWarningModule() {
        super("borderwarning", "BorderWarning", "Warns you when you approach the world border.",
                ModuleCategory.WORLD);
        this.distance = number("Distance", "Warning distance in blocks", 128.0, 8.0, 1024.0, 8.0);
        this.tintScreen = bool("Tint Screen", "Tint the screen while close", true);
        this.notifyChat = bool("Notify", "Show a notification", true);
    }

    public double distanceToBorder() {
        int x = player().blockX();
        int z = player().blockZ();
        int radius = mc().world().borderRadius();
        return radius <= 0 ? Double.MAX_VALUE
                : Math.max(0.0, radius - Math.max(Math.abs(x), Math.abs(z)));
    }

    public boolean nearBorder() {
        return distanceToBorder() <= distance.value();
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!mc().isInGame() || !nearBorder()) {
            return;
        }
        if (tintScreen.isEnabled()) {
            double intensity = MathUtil.clamp(1.0 - distanceToBorder() / distance.value(), 0.0, 1.0);
            event.renderer.fillRect(0.0, 0.0, event.width, event.height,
                    ColorUtil.withAlpha(0xFFEF4444, (int) (90 * intensity)));
        }
        if (notifyChat.isEnabled()) {
            event.renderer.drawCenteredText("World border: " + (int) distanceToBorder() + " blocks",
                    event.width / 2.0, event.height * 0.2, 0xFFF87171, true, 1.2);
        }
    }
}
