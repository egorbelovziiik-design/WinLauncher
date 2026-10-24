package nexus.core.module.misc;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;
import nexus.util.TimeUtil;

/** Shows the current server, version and session duration. */
public final class ServerInfoModule extends Module {

    private final NumberSetting x;
    private final NumberSetting y;
    private final BooleanSetting showVersion;
    private final BooleanSetting showSession;

    public ServerInfoModule() {
        super("serverinfo", "ServerInfo", "Shows the current server and session time.",
                ModuleCategory.MISC);
        this.x = number("X", "Horizontal position", 6.0, 0.0, 1920.0, 1.0);
        this.y = number("Y", "Vertical position", 26.0, 0.0, 1080.0, 1.0);
        this.showVersion = bool("Version", "Show the Minecraft version", true);
        this.showSession = bool("Session", "Show the session duration", true);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        String server = nexus.core.Nexus.instance().session().currentWorld();
        if (server == null) {
            server = "unknown";
        }
        StringBuilder sb = new StringBuilder(server);
        if (showVersion.isEnabled()) {
            sb.append("  ").append(mc().minecraftVersion()).append(" ").append(mc().loaderName());
        }
        if (showSession.isEnabled()) {
            sb.append("  ").append(TimeUtil.format(nexus.core.Nexus.instance().session().worldMs()));
        }
        event.renderer.fillRoundRect(x.value() - 3.0, y.value() - 3.0,
                event.renderer.textWidth(sb.toString(), 1.0) + 6.0, 16.0, 3.0,
                ColorUtil.withAlpha(0xFF08080D, 165));
        event.renderer.drawText(sb.toString(), x.value(), y.value(),
                nexus.core.Nexus.instance().themes().accent(System.currentTimeMillis()), true, 1.0);
    }
}
