package nexus.core.module.combat;

import nexus.api.IEntity;
import nexus.core.event.AttackEvent;
import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Briefly tints the entity you hit (visual hit confirmation). */
public final class HitColorModule extends Module {

    private final ColorSetting color;
    private final NumberSetting duration;
    private final BooleanSetting fade;
    private long lastHit = -1L;
    private IEntity lastTarget;

    public HitColorModule() {
        super("hitcolor", "HitColor", "Tints the hit entity for a moment so hits are clearly visible.",
                ModuleCategory.COMBAT);
        this.color = color("Color", "Tint color", 0xFFF87171);
        this.duration = number("Duration", "Tint duration in ms", 350.0, 50.0, 3000.0, 50.0);
        this.fade = bool("Fade", "Fade the tint out", true);
    }

    @Override
    public void onAttack(AttackEvent event) {
        if (event.target == null) {
            return;
        }
        lastTarget = event.target;
        lastHit = System.currentTimeMillis();
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (lastTarget == null || lastHit < 0L) {
            return;
        }
        long age = System.currentTimeMillis() - lastHit;
        if (age > duration.value()) {
            lastTarget = null;
            return;
        }
        double remaining = 1.0 - age / duration.value();
        int value = color.effectiveColor(System.currentTimeMillis());
        int alpha = (int) (ColorUtil.alpha(value) * (fade.isEnabled() ? remaining : 1.0));
        event.renderer.drawEntityBox(lastTarget, ColorUtil.withAlpha(value, alpha / 2),
                ColorUtil.withAlpha(value, alpha), true);
    }
}
