package nexus.core.module.exploration;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.util.ArrayList;
import java.util.List;

/** Keeps a list of explored areas and exports them as waypoints. */
public final class CartographerModule extends Module {

    private final NumberSetting gridSize;
    private final BooleanSetting autoWaypoint;
    private final List<String> explored = new ArrayList<String>();

    public CartographerModule() {
        super("cartographer", "Cartographer", "Keeps a list of explored map areas.",
                ModuleCategory.EXPLORATION);
        this.gridSize = number("Grid Size", "Area size in blocks", 512.0, 64.0, 4096.0, 64.0);
        this.autoWaypoint = bool("Auto Waypoint", "Save an area when you leave it", true);
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int grid = (int) gridSize.value();
        int x = Math.round(player().blockX() / (float) grid) * grid;
        int z = Math.round(player().blockZ() / (float) grid) * grid;
        String key = x + "," + z;
        if (!explored.contains(key)) {
            explored.add(key);
            if (autoWaypoint.isEnabled()) {
                nexus.core.world.WaypointManager manager = nexus.core.Nexus.instance().waypoints();
                if (manager.byName("area " + key) == null) {
                    manager.add("area " + key, x, player().blockY(), z,
                            mc().world().dimensionId(), 0xFF4ADE80);
                }
            }
        }
    }

    public List<String> exploredAreas() {
        return explored;
    }

    public void clear() {
        explored.clear();
    }

    @Override
    public String hudInfo() {
        return explored.size() + " areas";
    }
}
