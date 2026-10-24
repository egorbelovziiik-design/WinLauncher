package nexus.core.module.misc;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Refuses to send chat report signatures (privacy protection). */
public final class NoChatReportsModule extends Module {

    private final BooleanSetting hideSignatures;
    private final BooleanSetting warnOnReport;

    public NoChatReportsModule() {
        super("nochatreports", "NoChatReports", "Refuses to send chat report signatures.",
                ModuleCategory.MISC);
        this.hideSignatures = bool("Hide Signatures", "Never attach a signature to your messages", true);
        this.warnOnReport = bool("Warn", "Notify when a reportable message is received", false);
    }

    public boolean hideSignatures() {
        return isEnabled() && hideSignatures.isEnabled();
    }

    public boolean warningsEnabled() {
        return isEnabled() && warnOnReport.isEnabled();
    }
}
