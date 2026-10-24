package nexus.core.module.movement;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Shows the current fall distance and whether the fall would be fatal. */
public final class FallDistanceModule extends Module {

    private final NumberSetting safeHeight;
    private final BooleanSetting warn;

    public FallDistanceModule() {
        super("falldistance", "FallDistance", "Shows your fall distance and warns about deadly falls.",
                ModuleCategory.MOVEMENT);
        this.safeHeight = number("Safe Height", "Height considered safe in blocks", 3.0, 1.0, 40.0, 1.0);
        this.warn = bool("Warn", "Show a warning when the fall is dangerous", true);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        float fall = player().fallDistance();
        if (fall <= 0.0f) {
            return;
        }
        boolean dangerous = fall > safeHeight.value();
        int color = dangerous ? 0xFFF87171 : 0xFFC084FC;
        String text = "fall " + String.format("%.1f", fall) + (dangerous ? " !" : "");
        event.renderer.drawCenteredText(text, event.width / 2.0, event.height * 0.62,
                ColorUtil.withAlpha(color, 230), true, 1.0);
        if (dangerous && warn.isEnabled()) {
            nexus.core.Nexus.instance().notifications().warn("Fall", "Dangerous fall height");
        }
    }
}
