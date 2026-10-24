package nexus.core.module.misc;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;
import nexus.util.TimeUtil;

/** Shows the statistics of the current session. */
public final class SessionStatsModule extends Module {

    private final NumberSetting x;
    private final NumberSetting y;
    private final BooleanSetting showKills;
    private final BooleanSetting showDeaths;
    private final BooleanSetting showBlocks;

    public SessionStatsModule() {
        super("sessionstats", "SessionStats", "Shows the statistics of the current session.",
                ModuleCategory.MISC);
        this.x = number("X", "Horizontal position", 8.0, 0.0, 1920.0, 1.0);
        this.y = number("Y", "Vertical position", 160.0, 0.0, 1080.0, 1.0);
        this.showKills = bool("Kills", "Show the kill count", true);
        this.showDeaths = bool("Deaths", "Show the death count", true);
        this.showBlocks = bool("Blocks", "Show broken and placed blocks", true);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        nexus.core.session.SessionTracker session = nexus.core.Nexus.instance().session();
        StringBuilder sb = new StringBuilder();
        sb.append("Session ").append(TimeUtil.format(session.sessionMs()));
        if (showKills.isEnabled()) {
            sb.append("  K").append(session.kills());
        }
        if (showDeaths.isEnabled()) {
            sb.append("  D").append(session.deaths());
        }
        if (showBlocks.isEnabled()) {
            sb.append("  B").append(session.blocksBroken()).append("/").append(session.blocksPlaced());
        }
        String text = sb.toString();
        event.renderer.fillRoundRect(x.value() - 3.0, y.value() - 3.0,
                event.renderer.textWidth(text, 1.0) + 6.0, 16.0, 3.0, ColorUtil.withAlpha(0xFF08080D, 170));
        event.renderer.drawText(text, x.value(), y.value(), 0xFFC084FC, true, 1.0);
    }
}
