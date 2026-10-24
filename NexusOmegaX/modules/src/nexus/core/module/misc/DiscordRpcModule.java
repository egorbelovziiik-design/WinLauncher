package nexus.core.module.misc;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Shows what you are doing in Discord (rich presence state). */
public final class DiscordRpcModule extends Module {

    private final StringSetting details;
    private final BooleanSetting showServer;
    private final BooleanSetting showElapsed;

    public DiscordRpcModule() {
        super("discordrpc", "DiscordRPC", "Shows your activity in Discord.", ModuleCategory.MISC);
        this.details = text("Details", "Custom detail text", "Playing Minecraft");
        this.showServer = bool("Show Server", "Show the server name", false);
        this.showElapsed = bool("Show Elapsed", "Show the elapsed session time", true);
    }

    public String details() {
        String text = details.rawValue();
        if (showServer.isEnabled() && mc().isInGame()) {
            String world = nexus.core.Nexus.instance().session().currentWorld();
            if (world != null && !world.isEmpty()) {
                text = text + " - " + world;
            }
        }
        return text;
    }

    public boolean showElapsed() {
        return showElapsed.isEnabled();
    }

    public boolean active() {
        return isEnabled();
    }

    @Override
    public String hudInfo() {
        return active() ? "connected" : "off";
    }
}
