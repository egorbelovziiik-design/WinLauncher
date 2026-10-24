package nexus.core.module.world;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Highlights generated structures in loaded chunks. */
public final class StructureFinderModule extends Module {

    private final MultiSelectSetting structures;
    private final ColorSetting color;
    private final NumberSetting radius;
    private final BooleanSetting tracers;

    public StructureFinderModule() {
        super("structurefinder", "StructureFinder", "Highlights generated structures.",
                ModuleCategory.WORLD);
        this.structures = multi("Structures", "Structures to search for", new String[]{"Village",
                "Fortress", "Temple", "Stronghold", "Monument", "Outpost", "Mansion", "Bastion", "City"},
                "Village", "Fortress", "Stronghold");
        this.color = color("Color", "Highlight color", 0xFFFBBF24);
        this.radius = number("Radius", "Search radius in chunks", 8.0, 1.0, 32.0, 1.0);
        this.tracers = bool("Tracers", "Draw tracers to structures", true);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        int chunkX = player().blockX() >> 4;
        int chunkZ = player().blockZ() >> 4;
        int radiusInt = (int) radius.value();
        for (int cx = chunkX - radiusInt; cx <= chunkX + radiusInt; cx++) {
            for (int cz = chunkZ - radiusInt; cz <= chunkZ + radiusInt; cz++) {
                if (!mc().world().isChunkLoaded(cx, cz)) {
                    continue;
                }
                String structure = mc().world().structureAt(cx, cz);
                if (structure == null || !matches(structure)) {
                    continue;
                }
                nexus.api.math.Vec3d center = new nexus.api.math.Vec3d((cx << 4) + 8.0,
                        player().blockY() + 4.0, (cz << 4) + 8.0);
                event.renderer.drawNametag(center, structure, value, 0.9, true,
                        ColorUtil.withAlpha(0xFF08080D, 150));
                if (tracers.isEnabled()) {
                    event.renderer.drawTracer(center, value, 1.0);
                }
            }
        }
    }

    private boolean matches(String structure) {
        for (String selected : structures.selected()) {
            if (structure.toLowerCase().contains(selected.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String hudInfo() {
        return structures.selected().size() + " types";
    }
}
