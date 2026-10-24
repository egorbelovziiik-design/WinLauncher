package nexus.core.module.client;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Resets the client back to the factory state. */
public final class ResetClientModule extends Module {

    private final BooleanSetting resetModules;
    private final BooleanSetting resetHud;
    private final BooleanSetting resetThemes;
    private final BooleanSetting resetBinds;
    private final BooleanSetting confirmed;

    public ResetClientModule() {
        super("resetclient", "ResetClient", "Resets the client back to the factory state.",
                ModuleCategory.CLIENT);
        this.resetModules = bool("Modules", "Reset every module", true);
        this.resetHud = bool("HUD", "Reset the HUD layout", true);
        this.resetThemes = bool("Themes", "Reset the themes", true);
        this.resetBinds = bool("Binds", "Reset the keybinds", true);
        this.confirmed = bool("Confirmed", "Safety switch, must be enabled to run", false);
    }

    /** Runs the reset. Returns a short report for the chat. */
    public String run() {
        if (!confirmed.isEnabled()) {
            return "Enable the Confirmed switch first";
        }
        int count = 0;
        if (resetModules.isEnabled()) {
            for (nexus.core.module.Module module : nexus.core.Nexus.instance().modules().all()) {
                module.setEnabled(false, false);
                for (nexus.core.setting.Setting<?> setting : module.settings()) {
                    setting.reset();
                }
                count++;
            }
        }
        if (resetHud.isEnabled()) {
            nexus.core.Nexus.instance().hud().resetLayout();
        }
        if (resetThemes.isEnabled()) {
            nexus.core.Nexus.instance().themes().setActive("omega-purple");
        }
        if (resetBinds.isEnabled()) {
            nexus.core.Nexus.instance().keybinds().syncFromModules(nexus.core.Nexus.instance().modules().all());
        }
        nexus.core.Nexus.instance().configManager().markDirty();
        markActivity();
        return "Reset complete: " + count + " modules";
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        // the reset is triggered by the .reset command or the settings screen
    }

    @Override
    public String hudInfo() {
        return confirmed.isEnabled() ? "armed" : "safe";
    }
}
