package nexus.core.module.misc;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.io.File;
import java.util.List;

/** Quick access to the installed resource packs. */
public final class ResourcePacksModule extends Module {

    private final BooleanSetting sortByName;
    private final BooleanSetting showInfo;

    public ResourcePacksModule() {
        super("resourcepacks", "ResourcePacks", "Quick access to your resource packs.",
                ModuleCategory.MISC);
        this.sortByName = bool("Sort By Name", "Sort the pack list alphabetically", true);
        this.showInfo = bool("Show Info", "Print the pack list after refreshing", true);
    }

    public List<String> packs() {
        List<String> packs = mc().resources().resourcePacks();
        if (sortByName.isEnabled()) {
            java.util.Collections.sort(packs);
        }
        return packs;
    }

    public File folder() {
        return mc().resources().resourcePackFolder();
    }

    public void refresh() {
        List<String> packs = packs();
        if (showInfo.isEnabled()) {
            nexus.core.Nexus.instance().notifications().info("ResourcePacks",
                    packs.size() + " packs installed");
        }
        markActivity();
    }

    @Override
    public String hudInfo() {
        return packs().size() + " packs";
    }
}
