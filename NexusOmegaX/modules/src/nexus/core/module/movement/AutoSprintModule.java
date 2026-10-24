package nexus.core.module.movement;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Sprints automatically whenever you move. */
public final class AutoSprintModule extends Module {

    private final BooleanSetting onlyForward;
    private final BooleanSetting stopWhenHungry;

    public AutoSprintModule() {
        super("autosprint", "AutoSprint", "Sprints automatically whenever the player moves.",
                ModuleCategory.MOVEMENT);
        this.onlyForward = bool("Only Forward", "Only sprint while walking forward", false);
        this.stopWhenHungry = bool("Stop When Hungry", "Do not sprint with an empty hunger bar", true);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        boolean moving = player().horizontalSpeed() > 0.02 || mc().input().isKeyDown(87)
                || mc().input().isKeyDown(65) || mc().input().isKeyDown(83) || mc().input().isKeyDown(68);
        boolean forward = !onlyForward.isEnabled() || mc().input().isKeyDown(87);
        boolean hungry = stopWhenHungry.isEnabled() && player().hunger() <= 6.0f;
        player().setSprinting(moving && forward && !hungry);
    }

    @Override
    public void onDisable() {
        player().setSprinting(false);
    }
}
