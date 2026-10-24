package nexus.core.module.client;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Small info panel with the client name, version and module count. */
public final class ClientInfoModule extends Module {

    private final NumberSetting x;
    private final NumberSetting y;
    private final BooleanSetting showModules;
    private final BooleanSetting showFps;

    public ClientInfoModule() {
        super("clientinfo", "ClientInfo", "Shows the client name, version and module count.",
                ModuleCategory.CLIENT);
        this.x = number("X", "Horizontal position", 6.0, 0.0, 1920.0, 1.0);
        this.y = number("Y", "Vertical position", 6.0, 0.0, 1080.0, 1.0);
        this.showModules = bool("Modules", "Show the number of active modules", true);
        this.showFps = bool("FPS", "Show the current FPS", true);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        StringBuilder sb = new StringBuilder(nexus.core.NexusInfo.PRODUCT).append(" v")
                .append(nexus.core.NexusInfo.VERSION);
        if (showModules.isEnabled()) {
            sb.append("  ").append(nexus.core.Nexus.instance().modules().enabledCount()).append(" active");
        }
        if (showFps.isEnabled()) {
            sb.append("  ").append(mc().options().currentFps()).append(" fps");
        }
        String text = sb.toString();
        event.renderer.fillRoundRect(x.value() - 4.0, y.value() - 4.0,
                event.renderer.textWidth(text, 1.0) + 8.0, 18.0, 4.0, ColorUtil.withAlpha(0xFF08080D, 170));
        event.renderer.drawText(text, x.value(), y.value(),
                nexus.core.Nexus.instance().themes().accent(System.currentTimeMillis()), true, 1.0);
    }
}
