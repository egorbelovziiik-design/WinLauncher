package nexus.core.module.client;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.io.File;
import java.util.List;

/** Creates and restores backups of your whole configuration. */
public final class BackupClientModule extends Module {

    private final NumberSetting keepCount;
    private final BooleanSetting backupOnExit;
    private final NumberSetting intervalMinutes;
    private final nexus.util.TimeUtil.Timer timer = new nexus.util.TimeUtil.Timer();

    public BackupClientModule() {
        super("backupclient", "BackupClient", "Creates and restores configuration backups.",
                ModuleCategory.CLIENT);
        this.keepCount = number("Keep", "Backups to keep", 10.0, 1.0, 100.0, 1.0);
        this.backupOnExit = bool("On Exit", "Create a backup when the client closes", true);
        this.intervalMinutes = number("Interval", "Minutes between automatic backups", 30.0, 1.0, 1440.0, 1.0);
    }

    @Override
    public void onEnable() {
        timer.reset();
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        if (!isEnabled() || !timer.hasPassed((long) (intervalMinutes.value() * 60000.0), true)) {
            return;
        }
        backup();
    }

    public File backup() {
        File file = nexus.core.Nexus.instance().configManager().createBackup("auto");
        trim();
        markActivity();
        return file;
    }

    private void trim() {
        List<File> backups = backups();
        while (backups.size() > keepCount.value()) {
            nexus.util.FileUtil.delete(backups.remove(0));
        }
    }

    public List<File> backups() {
        return nexus.util.FileUtil.listFiles(nexus.core.Nexus.instance().configManager().backupsFolder(),
                ".zip");
    }

    public boolean backupOnExit() {
        return isEnabled() && backupOnExit.isEnabled();
    }

    @Override
    public String hudInfo() {
        return backups().size() + " backups";
    }
}
