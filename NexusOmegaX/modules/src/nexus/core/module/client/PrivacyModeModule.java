package nexus.core.module.client;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Blocks every outgoing data: no telemetry, no statistics, no crash upload. */
public final class PrivacyModeModule extends Module {

    private final BooleanSetting blockTelemetry;
    private final BooleanSetting hideServer;
    private final BooleanSetting hideAccount;

    public PrivacyModeModule() {
        super("privacymode", "PrivacyMode", "Blocks every outgoing data from the client.",
                ModuleCategory.CLIENT);
        this.blockTelemetry = bool("No Telemetry", "Never send usage statistics", true);
        this.hideServer = bool("Hide Server", "Never store the server address", false);
        this.hideAccount = bool("Hide Account", "Never store your player name", false);
    }

    public boolean telemetryBlocked() {
        return isEnabled() && blockTelemetry.isEnabled();
    }

    public boolean hideServerAddress() {
        return isEnabled() && hideServer.isEnabled();
    }

    public boolean hideAccountName() {
        return isEnabled() && hideAccount.isEnabled();
    }

    public String maskServer(String address) {
        if (!hideServerAddress() || address == null) {
            return address;
        }
        int index = address.indexOf('.');
        return index > 0 ? "***" + address.substring(index) : "***";
    }
}
