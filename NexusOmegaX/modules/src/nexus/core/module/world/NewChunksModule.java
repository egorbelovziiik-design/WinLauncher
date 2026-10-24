package nexus.core.module.world;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

import java.util.HashSet;
import java.util.Set;

/** Marks chunks that were generated after you joined the world. */
public final class NewChunksModule extends Module {

    private final ColorSetting color;
    private final BooleanSetting beacons;
    private final Set<Long> known = new HashSet<Long>();
    private boolean seeded;

    public NewChunksModule() {
        super("newchunks", "NewChunks", "Marks chunks generated while you are online.",
                ModuleCategory.WORLD);
        this.color = color("Color", "New chunk color", 0xFFF87171);
        this.beacons = bool("Beacons", "Draw light beacons on new chunks", true);
    }

    @Override
    public void onEnable() {
        known.clear();
        seeded = false;
    }

    @Override
    public void onDisable() {
        known.clear();
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int chunkX = player().blockX() >> 4;
        int chunkZ = player().blockZ() >> 4;
        int radius = 8;
        int value = color.effectiveColor(System.currentTimeMillis());
        for (int cx = chunkX - radius; cx <= chunkX + radius; cx++) {
            for (int cz = chunkZ - radius; cz <= chunkZ + radius; cz++) {
                long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
                if (!mc().world().isChunkLoaded(cx, cz)) {
                    continue;
                }
                if (known.add(Long.valueOf(key))) {
                    if (seeded) {
                        mark(event, cx, cz, value);
                    }
                } else if (seeded && isNew(key)) {
                    mark(event, cx, cz, value);
                }
            }
        }
        seeded = true;
    }

    private void mark(Render3DEvent event, int cx, int cz, int color) {
        int startX = cx << 4;
        int startZ = cz << 4;
        double y = player().blockY() + 1.0;
        event.renderer.drawLine3D(new nexus.api.math.Vec3d(startX, y, startZ),
                new nexus.api.math.Vec3d(startX + 16, y, startZ), color, 1.5);
        event.renderer.drawLine3D(new nexus.api.math.Vec3d(startX, y, startZ + 16),
                new nexus.api.math.Vec3d(startX + 16, y, startZ + 16), color, 1.5);
        event.renderer.drawLine3D(new nexus.api.math.Vec3d(startX, y, startZ),
                new nexus.api.math.Vec3d(startX, y, startZ + 16), color, 1.5);
        event.renderer.drawLine3D(new nexus.api.math.Vec3d(startX + 16, y, startZ),
                new nexus.api.math.Vec3d(startX + 16, y, startZ + 16), color, 1.5);
        if (beacons.isEnabled()) {
            event.renderer.drawLine3D(new nexus.api.math.Vec3d(startX + 8, mc().world().minHeight(), startZ + 8),
                    new nexus.api.math.Vec3d(startX + 8, mc().world().maxHeight(), startZ + 8),
                    ColorUtil.withAlpha(color, 60), 1.0);
        }
    }

    private boolean isNew(long key) {
        return known.contains(Long.valueOf(key));
    }
}
