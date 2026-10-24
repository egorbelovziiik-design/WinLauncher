package nexus.core.module.exploration;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Exploration HUD with coordinates, dimension, biome and heading. */
public final class ExplorerHudModule extends Module {

    private final BooleanSetting showBiome;
    private final BooleanSetting showHeading;
    private final BooleanSetting showSeed;
    private final NumberSetting x;
    private final NumberSetting y;

    public ExplorerHudModule() {
        super("explorerhud", "ExplorerHUD", "Exploration HUD with coordinates and biome.",
                ModuleCategory.EXPLORATION);
        this.showBiome = bool("Biome", "Show the current biome", true);
        this.showHeading = bool("Heading", "Show the facing direction", true);
        this.showSeed = bool("Seed", "Show the world seed when known", false);
        this.x = number("X", "Horizontal position", 8.0, 0.0, 1920.0, 1.0);
        this.y = number("Y", "Vertical position", 8.0, 0.0, 1080.0, 1.0);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int accent = nexus.core.Nexus.instance().themes().accent(System.currentTimeMillis());
        double posX = x.value();
        double posY = y.value();
        event.renderer.fillRoundRect(posX - 4.0, posY - 4.0, 148.0, 46.0, 5.0,
                ColorUtil.withAlpha(0xFF08080D, 170));
        event.renderer.drawText("XYZ " + player().blockX() + " " + player().blockY() + " "
                + player().blockZ(), posX, posY, 0xFFF5F3FF, true, 1.0);
        if (showBiome.isEnabled()) {
            String biome = mc().world().biomeAt(player().blockX(), player().blockZ());
            event.renderer.drawText("Biome " + (biome == null ? "?" : biome), posX, posY + 12.0,
                    accent, true, 1.0);
        }
        if (showHeading.isEnabled()) {
            event.renderer.drawText("Facing " + player().facing(), posX, posY + 24.0,
                    0xFFC084FC, true, 1.0);
        }
        if (showSeed.isEnabled()) {
            nexus.core.module.Module helper = nexus.core.Nexus.instance().modules().byName("seedhelper");
            if (helper instanceof nexus.core.module.world.SeedHelperModule) {
                nexus.core.module.world.SeedHelperModule module = (nexus.core.module.world.SeedHelperModule) helper;
                event.renderer.drawText("Seed " + (module.known() ? module.seed() : "?"), posX, posY + 36.0,
                        0xFFF5F3FF, true, 1.0);
            }
        }
    }
}
