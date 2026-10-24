package nexus.core.module.player;

import nexus.api.IPotionEffect;
import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Detailed potion effect list with timers and colors. */
public final class PotionManagerModule extends Module {

    private final BooleanSetting showDuration;
    private final BooleanSetting background;
    private final NumberSetting scale;

    public PotionManagerModule() {
        super("potionmanager", "PotionManager", "Shows your active effects with timers and colors.",
                ModuleCategory.PLAYER);
        this.showDuration = bool("Duration", "Show the remaining duration", true);
        this.background = bool("Background", "Draw a background plate", true);
        this.scale = number("Scale", "Text scale", 1.0, 0.5, 2.0, 0.05);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        java.util.List<IPotionEffect> effects = player().potionEffects();
        if (effects.isEmpty()) {
            return;
        }
        double x = event.width - 120.0;
        double y = 30.0;
        for (IPotionEffect effect : effects) {
            int color = effect.color() == 0 ? 0xFFC084FC : effect.color();
            String label = effect.name();
            if (showDuration.isEnabled()) {
                int seconds = effect.duration() / 20;
                label += " " + String.format("%02d:%02d", seconds / 60, seconds % 60);
            }
            if (effect.amplifier() > 0) {
                label += " " + (effect.amplifier() + 1);
            }
            if (background.isEnabled()) {
                event.renderer.fillRoundRect(x - 4.0, y - 2.0, 116.0, 12.0, 3.0,
                        ColorUtil.withAlpha(0xFF101018, 160));
            }
            event.renderer.drawText(label, x, y, color, true, 0.85 * scale.value());
            y += 14.0 * scale.value();
        }
    }
}
