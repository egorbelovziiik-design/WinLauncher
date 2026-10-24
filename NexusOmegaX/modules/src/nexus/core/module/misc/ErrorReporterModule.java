package nexus.core.module.misc;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.io.File;
import java.util.List;

/** Collects client errors into a report file you can share. */
public final class ErrorReporterModule extends Module {

    private final BooleanSetting autoSave;
    private final NumberSetting maxReports;
    private int reports;

    public ErrorReporterModule() {
        super("errorreporter", "ErrorReporter", "Collects client errors into report files.",
                ModuleCategory.MISC);
        this.autoSave = bool("Auto Save", "Write a report automatically on errors", true);
        this.maxReports = number("Max Reports", "Maximum stored reports", 20.0, 1.0, 200.0, 1.0);
    }

    public File folder() {
        return new File(nexus.core.NexusInfo.directory(), "reports");
    }

    /** Writes a report file. Returns the file or null when it failed. */
    public File write(String title, String details) {
        File folder = folder();
        nexus.util.FileUtil.ensure(folder);
        String name = "report-" + System.currentTimeMillis() + ".txt";
        File file = new File(folder, name);
        StringBuilder sb = new StringBuilder();
        sb.append("Nexus Client Omega X ").append(nexus.core.NexusInfo.VERSION).append('\n');
        sb.append("Minecraft ").append(mc().minecraftVersion()).append(" (")
                .append(mc().loaderName()).append(")\n\n");
        sb.append(title).append('\n').append(details).append('\n');
        if (nexus.util.FileUtil.write(file, sb.toString())) {
            reports++;
            trim();
            return file;
        }
        return null;
    }

    private void trim() {
        List<File> files = nexus.util.FileUtil.listFiles(folder(), ".txt");
        while (files.size() > maxReports.value()) {
            nexus.util.FileUtil.delete(files.remove(0));
        }
    }

    public boolean autoSave() {
        return isEnabled() && autoSave.isEnabled();
    }

    public int reportCount() {
        return reports;
    }

    @Override
    public String hudInfo() {
        return reports + " reports";
    }
}
