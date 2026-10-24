package nexus.core.module.render;

import nexus.core.event.AttackEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Custom swing animation when you attack. */
public final class SwingAnimationsModule extends Module {

    private final ModeSetting style;
    private final NumberSetting speed;
    private final BooleanSetting offhand;

    public SwingAnimationsModule() {
        super("swinganimations", "SwingAnimations", "Custom swing animation when you attack.",
                ModuleCategory.RENDER);
        this.style = mode("Style", "Swing style", "Smooth", "Smooth", "Fast", "Wide", "None");
        this.speed = number("Speed", "Swing speed", 1.0, 0.2, 3.0, 0.05);
        this.offhand = bool("Offhand", "Also animate the offhand", false);
    }

    @Override
    public void onAttack(AttackEvent event) {
        if (!mc().isInGame() || style.is("None")) {
            return;
        }
        player().swingHand();
        if (offhand.isEnabled()) {
            player().swingHand();
        }
    }

    public String swingStyle() {
        return style.get();
    }

    public double swingSpeed() {
        return speed.value();
    }
}
