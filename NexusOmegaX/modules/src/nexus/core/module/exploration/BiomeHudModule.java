package nexus.core.module.exploration;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Shows the biome you are standing in and the biomes around you. */
public final class BiomeHudModule extends Module {

    private final NumberSetting x;
    private final NumberSetting y;
    private final BooleanSetting showNeighbours;

    public BiomeHudModule() {
        super("biomehud", "BiomeHUD", "Shows the biome you are standing in.",
                ModuleCategory.EXPLORATION);
        this.x = number("X", "Horizontal position", 8.0, 0.0, 1920.0, 1.0);
        this.y = number("Y", "Vertical position", 96.0, 0.0, 1080.0, 1.0);
        this.showNeighbours = bool("Neighbours", "List the surrounding biomes", true);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        String biome = mc().world().biomeAt(player().blockX(), player().blockZ());
        if (biome == null) {
            return;
        }
        int accent = nexus.core.Nexus.instance().themes().accent(System.currentTimeMillis());
        event.renderer.fillRoundRect(x.value() - 4.0, y.value() - 4.0, 150.0,
                showNeighbours.isEnabled() ? 44.0 : 20.0, 5.0, ColorUtil.withAlpha(0xFF08080D, 170));
        event.renderer.drawText("Biome: " + biome, x.value(), y.value(), 0xFFF5F3FF, true, 1.0);
        if (showNeighbours.isEnabled()) {
            int step = 64;
            String north = mc().world().biomeAt(player().blockX(), player().blockZ() - step);
            String south = mc().world().biomeAt(player().blockX(), player().blockZ() + step);
            String west = mc().world().biomeAt(player().blockX() - step, player().blockZ());
            String east = mc().world().biomeAt(player().blockX() + step, player().blockZ());
            event.renderer.drawText("N " + north + "  S " + south, x.value(), y.value() + 14.0,
                    accent, true, 0.9);
            event.renderer.drawText("W " + west + "  E " + east, x.value(), y.value() + 26.0,
                    accent, true, 0.9);
        }
    }
}
