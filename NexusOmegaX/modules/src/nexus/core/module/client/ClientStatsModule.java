package nexus.core.module.client;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** Counts how often each module was used and shows the favourites. */
public final class ClientStatsModule extends Module {

    private final BooleanSetting trackUsage;
    private final NumberSetting topCount;
    private final Map<String, Integer> usage = new LinkedHashMap<String, Integer>();

    public ClientStatsModule() {
        super("clientstats", "ClientStats", "Counts how often each module was used.",
                ModuleCategory.CLIENT);
        this.trackUsage = bool("Track Usage", "Count every enable", true);
        this.topCount = number("Top", "How many entries to show", 5.0, 1.0, 20.0, 1.0);
    }

    public void note(String moduleName) {
        if (!trackUsage.isEnabled()) {
            return;
        }
        Integer previous = usage.get(moduleName);
        usage.put(moduleName, Integer.valueOf(previous == null ? 1 : previous.intValue() + 1));
    }

    public Map<String, Integer> usage() {
        return usage;
    }

    public String topLine() {
        String best = "-";
        int count = 0;
        for (Map.Entry<String, Integer> entry : usage.entrySet()) {
            if (entry.getValue().intValue() > count) {
                count = entry.getValue().intValue();
                best = entry.getKey();
            }
        }
        return best + " (" + count + ")";
    }

    @Override
    public String hudInfo() {
        return topLine();
    }
}
