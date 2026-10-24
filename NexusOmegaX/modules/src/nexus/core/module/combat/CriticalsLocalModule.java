package nexus.core.module.combat;

import nexus.core.event.AttackEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Performs a small hop before an attack to land a local critical hit. */
public final class CriticalsLocalModule extends Module {

    private final ModeSetting mode;
    private final BooleanSetting onlyGrounded;

    public CriticalsLocalModule() {
        super("criticalslocal", "CriticalsLocal", "Hops before attacking so the hit counts as a critical hit.",
                ModuleCategory.COMBAT);
        this.mode = mode("Mode", "Hop style", "Jump", "Jump", "MiniJump", "Packet");
        this.onlyGrounded = bool("Only On Ground", "Only hop while standing on the ground", true);
    }

    @Override
    public void onAttack(AttackEvent event) {
        if (event.target == null || !mc().isInGame()) {
            return;
        }
        if (onlyGrounded.isEnabled() && !player().isOnGround()) {
            return;
        }
        if (mode.is("Packet")) {
            player().setVelocity(player().velocity().add(0.0, 0.08, 0.0));
        } else if (mode.is("MiniJump")) {
            player().setVelocity(player().velocity().add(0.0, 0.12, 0.0));
        } else {
            player().jump();
        }
        markActivity();
    }

    @Override
    public String hudInfo() {
        return mode.get();
    }
}
