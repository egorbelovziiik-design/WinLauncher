package nexus.core.module.client;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Disables every module at once and restores the vanilla behaviour. */
public final class PanicModule extends Module {

    private final BooleanSetting closeGui;
    private final BooleanSetting clearParticles;
    private final BooleanSetting resetKeys;

    public PanicModule() {
        super("panic", "Panic", "Disables every module at once.", ModuleCategory.CLIENT);
        this.closeGui = bool("Close GUI", "Close the ClickGUI as well", true);
        this.clearParticles = bool("Clean Effects", "Clear cosmetic effects", true);
        this.resetKeys = bool("Reset Keys", "Release every pressed key", true);
    }

    @Override
    public void onEnable() {
        int disabled = nexus.core.Nexus.instance().panic();
        if (closeGui.isEnabled()) {
            nexus.core.Nexus.instance().gui().closeAll(true);
        }
        if (resetKeys.isEnabled()) {
            mc().input().releaseAllKeys();
            mc().input().releaseMouse();
        }
        nexus.core.Nexus.instance().notifications().warn("Panic",
                disabled + " modules disabled");
        setEnabled(false, false);
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        // panic is a one shot action triggered by enabling it
    }

    @Override
    public String hudInfo() {
        return "ready";
    }
}
