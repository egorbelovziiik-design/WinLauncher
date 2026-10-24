package nexus.core.module.client;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.io.File;

/** Compares the installed build with the version file the installer writes. */
public final class UpdateCheckerModule extends Module {

    private final BooleanSetting notifyOnly;
    private final ModeSetting channel;

    public UpdateCheckerModule() {
        super("updatechecker", "UpdateChecker", "Checks the installed version against the manifest.",
                ModuleCategory.CLIENT);
        this.notifyOnly = bool("Notify Only", "Only show a notification", true);
        this.channel = mode("Channel", "Update channel", "Stable", "Stable", "Beta");
    }

    public File manifest() {
        return new File(nexus.core.NexusInfo.directory(), "version.txt");
    }

    /** Returns the report text of the last check. */
    public String check() {
        File file = manifest();
        String content = nexus.util.FileUtil.read(file);
        if (content == null || content.trim().isEmpty()) {
            return "No version manifest found (" + file.getName() + ")";
        }
        String installed = nexus.core.NexusInfo.VERSION;
        String available = content.trim().split("\n")[0].trim();
        if (installed.equals(available)) {
            return "Up to date (v" + installed + ")";
        }
        if (notifyOnly.isEnabled()) {
            nexus.core.Nexus.instance().notifications().info("UpdateChecker",
                    "v" + available + " available, you have v" + installed);
        }
        markActivity();
        return "Update available: v" + available + " (installed v" + installed + ")";
    }

    public String channel() {
        return channel.get();
    }

    @Override
    public String hudInfo() {
        return "v" + nexus.core.NexusInfo.VERSION;
    }
}
