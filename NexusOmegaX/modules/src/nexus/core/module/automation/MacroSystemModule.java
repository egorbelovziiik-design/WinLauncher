package nexus.core.module.automation;

import nexus.core.automation.MacroManager;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.util.List;

/** Enables the macro system and shows how many macros are armed. */
public final class MacroSystemModule extends Module {

    private final BooleanSetting blockVanillaKeys;
    private final BooleanSetting showOverlay;

    public MacroSystemModule() {
        super("macro", "Macro", "Runs your recorded key and chat macros.", ModuleCategory.AUTOMATION);
        this.blockVanillaKeys = bool("Block Vanilla", "Block the vanilla key action of key macros", false);
        this.showOverlay = bool("Show Overlay", "Show active macros on screen", true);
    }

    public boolean isBlockingVanilla() {
        return isEnabled() && blockVanillaKeys.isEnabled();
    }

    public boolean overlayEnabled() {
        return isEnabled() && showOverlay.isEnabled();
    }

    public List<MacroManager.MacroProfile> macros() {
        return nexus.core.Nexus.instance().macros().profiles();
    }

    @Override
    public String hudInfo() {
        return macros().size() + " macros";
    }
}
