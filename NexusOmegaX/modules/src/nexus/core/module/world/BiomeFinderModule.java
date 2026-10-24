package nexus.core.module.world;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Scans the surroundings for a biome you are looking for. */
public final class BiomeFinderModule extends Module {

    private final StringSetting target;
    private final NumberSetting radius;
    private int foundX;
    private int foundZ;
    private boolean found;

    public BiomeFinderModule() {
        super("biomefinder", "BiomeFinder", "Scans the surroundings for a biome.", ModuleCategory.WORLD);
        this.target = text("Biome", "Biome id part to search for", "mangrove_swamp");
        this.radius = number("Radius", "Search radius in blocks", 256.0, 32.0, 2048.0, 32.0);
    }

    @Override
    public void onEnable() {
        found = false;
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        String wanted = target.rawValue().trim().toLowerCase();
        if (wanted.isEmpty()) {
            return;
        }
        int playerX = player().blockX();
        int playerZ = player().blockZ();
        int step = 16;
        int radiusInt = (int) radius.value();
        for (int x = -radiusInt; x <= radiusInt && !found; x += step) {
            for (int z = -radiusInt; z <= radiusInt; z += step) {
                String biome = mc().world().biomeAt(playerX + x, playerZ + z);
                if (biome != null && biome.toLowerCase().contains(wanted)) {
                    foundX = playerX + x;
                    foundZ = playerZ + z;
                    found = true;
                    nexus.core.Nexus.instance().notifications().success("BiomeFinder",
                            "Found " + biome + " at " + foundX + ", " + foundZ);
                    break;
                }
            }
        }
    }

    @Override
    public String hudInfo() {
        return found ? foundX + ", " + foundZ : "searching";
    }
}
