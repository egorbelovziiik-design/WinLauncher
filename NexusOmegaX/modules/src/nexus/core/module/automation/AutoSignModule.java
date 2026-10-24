package nexus.core.module.automation;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Writes a saved text into signs you place. */
public final class AutoSignModule extends Module {

    private final StringSetting line1;
    private final StringSetting line2;
    private final StringSetting line3;
    private final StringSetting line4;

    public AutoSignModule() {
        super("autosign", "AutoSign", "Fills signs with a saved text.", ModuleCategory.AUTOMATION);
        this.line1 = text("Line 1", "First line", "");
        this.line2 = text("Line 2", "Second line", "");
        this.line3 = text("Line 3", "Third line", "");
        this.line4 = text("Line 4", "Fourth line", "");
    }

    public String[] lines() {
        return new String[]{line1.rawValue(), line2.rawValue(), line3.rawValue(), line4.rawValue()};
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        String screen = mc().screens().currentVanillaScreen();
        if (screen == null || !screen.contains("sign")) {
            return;
        }
        mc().screens().fillSignText(lines());
        markActivity();
    }

    @Override
    public String hudInfo() {
        int filled = 0;
        for (String line : lines()) {
            if (!line.trim().isEmpty()) {
                filled++;
            }
        }
        return filled + " lines";
    }
}
