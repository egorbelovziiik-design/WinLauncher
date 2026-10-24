package nexus.core.module.misc;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Leaves the server automatically when your health drops below a threshold. */
public final class AutoDisconnectModule extends Module {

    private final NumberSetting health;
    private final BooleanSetting onTotemless;
    private final BooleanSetting onlyPvp;
    private boolean armed;

    public AutoDisconnectModule() {
        super("autodisconnect", "AutoDisconnect", "Leaves the server when you are about to die.",
                ModuleCategory.MISC);
        this.health = number("Health", "Disconnect below this health", 4.0, 1.0, 20.0, 1.0);
        this.onTotemless = bool("No Totem", "Only when you have no totem", true);
        this.onlyPvp = bool("Only Near Players", "Only when players are nearby", false);
    }

    @Override
    public void onEnable() {
        armed = true;
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        if (!mc().isInGame() || !armed) {
            return;
        }
        if (player().health() > health.value()) {
            return;
        }
        if (onTotemless.isEnabled() && player().inventory().count("totem_of_undying") > 0) {
            return;
        }
        if (onlyPvp.isEnabled() && mc().world().players().size() < 2) {
            return;
        }
        armed = false;
        nexus.core.Nexus.instance().notifications().warn("AutoDisconnect", "Health low, leaving server");
        mc().connection().disconnect("AutoDisconnect");
        markActivity();
    }

    @Override
    public void onDisable() {
        armed = true;
    }
}
