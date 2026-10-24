package nexus.core.module.configs;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.io.File;
import java.util.List;

/** Exports, imports and restores configuration backups. */
public final class ConfigBackupModule extends Module {

    private final NumberSetting keep;
    private final BooleanSetting zipExport;
    private final StringSetting exportName;

    public ConfigBackupModule() {
        super("configbackup", "ConfigBackup", "Exports, imports and restores backups.",
                ModuleCategory.CONFIGS);
        this.keep = number("Keep", "Backups to keep", 10.0, 1.0, 200.0, 1.0);
        this.zipExport = bool("Compress", "Store backups compressed", true);
        this.exportName = text("Export Name", "File name for exports", "nexus-backup.json");
    }

    public File backup() {
        File file = nexus.core.Nexus.instance().configManager().createBackup("manual");
        trim();
        markActivity();
        return file;
    }

    private void trim() {
        List<File> files = nexus.util.FileUtil.listFiles(
                nexus.core.Nexus.instance().configManager().backupsFolder(), ".json");
        while (files.size() > keep.value()) {
            nexus.util.FileUtil.delete(files.remove(0));
        }
    }

    public boolean exportTo(File target) {
        boolean ok = nexus.core.Nexus.instance().configManager().exportProfile(
                nexus.core.Nexus.instance().configManager().activeProfile(), target);
        markActivity();
        return ok;
    }

    public boolean importFrom(File source, String name) {
        boolean ok = nexus.core.Nexus.instance().configManager().importProfile(source, name);
        markActivity();
        return ok;
    }

    public List<File> backups() {
        return nexus.util.FileUtil.listFiles(nexus.core.Nexus.instance().configManager().backupsFolder(),
                ".json");
    }

    public boolean compress() {
        return zipExport.isEnabled();
    }

    @Override
    public String hudInfo() {
        return backups().size() + " backups";
    }
}
