package nexus.core.module.combat;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Changes the swing animation of swords and other items. */
public final class SwordAnimationsModule extends Module {

    private final ModeSetting style;
    private final NumberSetting speed;
    private final BooleanSetting onlySword;

    public SwordAnimationsModule() {
        super("swordanimations", "SwordAnimations", "Custom swing animation for weapons in your hand.",
                ModuleCategory.COMBAT);
        this.style = mode("Style", "Animation style", "Omega", "Omega", "Spin", "Stab", "Slide", "None");
        this.speed = number("Speed", "Animation speed", 1.0, 0.2, 3.0, 0.1);
        this.onlySword = bool("Only Swords", "Only animate swords", true);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || style.is("None")) {
            return;
        }
        if (onlySword.isEnabled() && !player().inventory().heldItem().id().contains("sword")) {
            return;
        }
        double swing = player().attackCooldown();
        if (swing <= 0.0 || swing >= 1.0) {
            return;
        }
        // the visual offset is applied by the hand renderer through the HUD/registry
        nexus.core.Nexus.instance().render().registerOverride("sword-animation",
                new nexus.core.render.RenderManager.Override() {
                    @Override
                    public void restore() {
                    }
                });
        markActivity();
    }

    public String style() {
        return style.get();
    }

    public double speed() {
        return speed.value();
    }

    @Override
    public String hudInfo() {
        return style.get();
    }
}
