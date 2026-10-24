package nexus.core.module.movement;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Keeps the sneak key held. */
public final class AutoSneakModule extends Module {

    private final BooleanSetting hideNameplate;

    public AutoSneakModule() {
        super("autosneak", "AutoSneak", "Holds the sneak key.", ModuleCategory.MOVEMENT);
        this.hideNameplate = bool("Prevent Fall", "Also stop at block edges", false);
    }

    @Override
    public void onEnable() {
        mc().input().setKeyState(nexus.core.automation.MacroManager.KeyMappingKeys.SNEAK, true);
    }

    @Override
    public void onTick(TickEvent event) {
        player().setSneaking(true);
    }

    @Override
    public void onDisable() {
        mc().input().setKeyState(nexus.core.automation.MacroManager.KeyMappingKeys.SNEAK, false);
        player().setSneaking(false);
    }
}
