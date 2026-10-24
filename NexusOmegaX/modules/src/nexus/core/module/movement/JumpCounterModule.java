package nexus.core.module.movement;

import nexus.core.event.Render2DEvent;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Counts how many jumps you made. */
public final class JumpCounterModule extends Module {

    private final BooleanSetting resetOnLand;
    private final ColorSetting color;
    private int jumps;
    private boolean wasGrounded = true;

    public JumpCounterModule() {
        super("jumpcounter", "JumpCounter", "Counts your jumps.", ModuleCategory.MOVEMENT);
        this.resetOnLand = bool("Reset On Land", "Reset the counter when you land", true);
        this.color = color("Color", "Text color", 0xFFC084FC);
    }

    @Override
    public void onEnable() {
        jumps = 0;
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        boolean grounded = player().isOnGround();
        if (wasGrounded && !grounded) {
            jumps++;
        }
        if (grounded && !wasGrounded && resetOnLand.isEnabled()) {
            jumps = 0;
        }
        wasGrounded = grounded;
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        event.renderer.drawText("jumps " + jumps, 6.0, event.height * 0.46,
                color.effectiveColor(System.currentTimeMillis()), true, 0.85);
    }
}
