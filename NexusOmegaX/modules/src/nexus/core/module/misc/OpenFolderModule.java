package nexus.core.module.misc;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.io.File;

/** Adds a quick way to open the client folders from the in game chat. */
public final class OpenFolderModule extends Module {

    private final BooleanSetting notify;

    public OpenFolderModule() {
        super("openfolder", "OpenFolder", "Opens the client folders from the chat.",
                ModuleCategory.MISC);
        this.notify = bool("Notify", "Show the path when opening", true);
    }

    public File folder(String name) {
        if (name == null || name.isEmpty()) {
            return nexus.core.NexusInfo.directory();
        }
        return new File(nexus.core.NexusInfo.directory(), nexus.util.FileUtil.sanitize(name));
    }

    public boolean open(String name) {
        File target = folder(name);
        if (!target.exists()) {
            return false;
        }
        nexus.core.Nexus.instance().client().resources().openFolder(target);
        if (notify.isEnabled()) {
            nexus.core.Nexus.instance().notifications().info("OpenFolder", target.getAbsolutePath());
        }
        markActivity();
        return true;
    }

    @Override
    public String hudInfo() {
        return "ready";
    }
}
