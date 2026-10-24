package nexus.core.module.world;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Remembers what you stored in each ender chest and shows it as a tooltip. */
public final class EnderChestPreviewModule extends Module {

    private final BooleanSetting saveContents;
    private final BooleanSetting showTooltip;

    public EnderChestPreviewModule() {
        super("enderchestpreview", "EnderChestPreview", "Remembers the contents of your ender chest.",
                ModuleCategory.WORLD);
        this.saveContents = bool("Save Contents", "Store the contents when you open it", true);
        this.showTooltip = bool("Show Tooltip", "Show the contents on hover", true);
    }

    private final java.util.List<String> contents = new java.util.ArrayList<String>(27);

    public void record(java.util.List<String> items) {
        contents.clear();
        if (saveContents.isEnabled()) {
            contents.addAll(items);
        }
    }

    public java.util.List<String> contents() {
        return contents;
    }

    public boolean tooltipEnabled() {
        return isEnabled() && showTooltip.isEnabled();
    }

    @Override
    public String hudInfo() {
        return contents.size() + " items";
    }
}
