package nexus.core.module.misc;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Saves screenshots into a client folder and keeps an index of them. */
public final class ScreenshotManagerModule extends Module {

    private final BooleanSetting confirm;
    private final BooleanSetting openFolderAfter;
    private final List<String> recent = new ArrayList<String>();

    public ScreenshotManagerModule() {
        super("screenshotmanager", "ScreenshotManager", "Keeps an index of your screenshots.",
                ModuleCategory.MISC);
        this.confirm = bool("Confirm", "Show a notification after a screenshot", true);
        this.openFolderAfter = bool("Open Folder", "Open the screenshot folder afterwards", false);
    }

    public File folder() {
        return new File(nexus.core.NexusInfo.directory(), "screenshots");
    }

    public void request() {
        mc().resources().takeScreenshot(folder());
        if (confirm.isEnabled()) {
            nexus.core.Nexus.instance().notifications().success("Screenshot", "Saved to " + folder().getName());
        }
        if (openFolderAfter.isEnabled()) {
            mc().resources().openFolder(folder());
        }
        markActivity();
    }

    public List<File> list() {
        return nexus.util.FileUtil.listFiles(folder(), ".png");
    }

    public void refresh() {
        recent.clear();
        for (File file : list()) {
            recent.add(file.getName());
        }
    }

    public List<String> recent() {
        return recent;
    }

    @Override
    public String hudInfo() {
        return recent.size() + " shots";
    }
}
