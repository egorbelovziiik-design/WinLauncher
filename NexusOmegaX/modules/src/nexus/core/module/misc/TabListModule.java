package nexus.core.module.misc;

import nexus.api.IEntity;
import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;

/** Rich tab list overlay with friends, enemies and ping. */
public final class TabListModule extends Module {

    private final NumberSetting maxPlayers;
    private final BooleanSetting showPing;
    private final BooleanSetting background;

    public TabListModule() {
        super("tablist", "TabList", "Rich tab list overlay.", ModuleCategory.MISC);
        this.maxPlayers = number("Max Players", "Maximum rows shown", 20.0, 4.0, 80.0, 1.0);
        this.showPing = bool("Ping", "Show the ping of each player", true);
        this.background = bool("Background", "Draw a background plate", true);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        List<IEntity> players = new ArrayList<IEntity>(mc().world().players());
        int shown = Math.min(players.size(), (int) maxPlayers.value());
        double width = 180.0;
        double height = 14.0 * (shown + 1);
        double x = event.width / 2.0 - width / 2.0;
        double y = 20.0;
        if (background.isEnabled()) {
            event.renderer.fillRoundRect(x - 4.0, y - 4.0, width + 8.0, height + 8.0, 5.0,
                    ColorUtil.withAlpha(0xFF08080D, 190));
        }
        int accent = nexus.core.Nexus.instance().themes().accent(System.currentTimeMillis());
        event.renderer.drawCenteredText("Players " + players.size(), x + width / 2.0, y,
                accent, true, 1.0);
        for (int i = 0; i < shown; i++) {
            IEntity entity = players.get(i);
            String name = entity.name();
            int color = nexus.core.Nexus.instance().friends().isFriend(name) ? 0xFF4ADE80
                    : (nexus.core.Nexus.instance().friends().isEnemy(name) ? 0xFFF87171 : 0xFFF5F3FF);
            String line = name;
            if (showPing.isEnabled()) {
                line = name + "  " + entity.ping() + "ms";
            }
            event.renderer.drawText(line, x, y + 14.0 * (i + 1), color, true, 1.0);
        }
    }
}
