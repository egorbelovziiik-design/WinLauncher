package nexus.core.module.movement;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Makes ladders safer: no fall damage and quick descend. */
public final class LadderHelperModule extends Module {

    private final BooleanSetting quickDescend;
    private final BooleanSetting snapToLadder;
    private final NumberSetting descendSpeed;

    public LadderHelperModule() {
        super("ladderhelper", "LadderHelper", "Snap onto ladders and descend quickly and safely.",
                ModuleCategory.MOVEMENT);
        this.quickDescend = bool("Quick Descend", "Slide down faster", true);
        this.snapToLadder = bool("Snap To Ladder", "Center yourself on the ladder", true);
        this.descendSpeed = number("Descend Speed", "Extra descend velocity", 0.22, 0.05, 0.8, 0.01);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !player().isClimbing()) {
            return;
        }
        if (snapToLadder.isEnabled()) {
            double x = player().position().x;
            double z = player().position().z;
            double snappedX = Math.floor(x) + 0.5;
            double snappedZ = Math.floor(z) + 0.5;
            double dx = snappedX - x;
            double dz = snappedZ - z;
            if (Math.abs(dx) > 0.02 || Math.abs(dz) > 0.02) {
                player().setVelocity(player().velocity().add(dx * 0.12, 0.0, dz * 0.12));
            }
        }
        if (quickDescend.isEnabled() && player().velocity().y < 0.0) {
            player().setVelocity(player().velocity().add(0.0, -descendSpeed.value(), 0.0));
        }
    }
}
