package nexus.core.module.misc;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.util.ArrayList;
import java.util.List;

/** Lists the plugins of the server when the server supports it. */
public final class PluginsModule extends Module {

    private final BooleanSetting autoRequest;
    private final NumberSetting requestDelay;
    private final List<String> plugins = new ArrayList<String>();

    public PluginsModule() {
        super("plugins", "Plugins", "Lists the plugins of the server.", ModuleCategory.MISC);
        this.autoRequest = bool("Auto Request", "Request the plugin list when joining", false);
        this.requestDelay = number("Delay", "Delay before requesting in seconds", 5.0, 0.0, 60.0, 1.0);
    }

    @Override
    public void onWorldJoin(nexus.core.event.WorldJoinEvent event) {
        if (!isEnabled() || !autoRequest.isEnabled() || event.singleplayer) {
            return;
        }
        mc().schedule(new Runnable() {
            @Override
            public void run() {
                request();
            }
        }, (int) requestDelay.value() * 20);
    }

    public void request() {
        player().sendChat("/plugins");
        markActivity();
    }

    public void record(List<String> names) {
        plugins.clear();
        plugins.addAll(names);
    }

    public List<String> plugins() {
        return plugins;
    }

    @Override
    public String hudInfo() {
        return plugins.size() + " plugins";
    }
}
