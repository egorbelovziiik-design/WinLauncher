package nexus.core.module.world;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

import java.util.HashSet;
import java.util.Set;

/** Leaves a permanent highlight on every chunk you have visited. */
public final class ChunkTrailModule extends Module {

    private final ColorSetting color;
    private final BooleanSetting onlyNewChunks;
    private final Set<Long> visited = new HashSet<Long>();

    public ChunkTrailModule() {
        super("chunktrail", "ChunkTrail", "Highlights every chunk you have visited.", ModuleCategory.WORLD);
        this.color = color("Color", "Trail color", 0xFF60A5FA);
        this.onlyNewChunks = bool("Only New", "Only highlight chunks you entered while enabled", true);
    }

    @Override
    public void onEnable() {
        visited.clear();
    }

    @Override
    public void onDisable() {
        visited.clear();
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int chunkX = player().blockX() >> 4;
        int chunkZ = player().blockZ() >> 4;
        visited.add(Long.valueOf(((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL)));
        int value = color.effectiveColor(System.currentTimeMillis());
        double y = player().blockY() + 0.5;
        for (Long key : visited) {
            int cx = (int) (key.longValue() >> 32);
            int cz = (int) (key.longValue() & 0xFFFFFFFFL);
            int startX = cx << 4;
            int startZ = cz << 4;
            event.renderer.drawLine3D(new nexus.api.math.Vec3d(startX, y, startZ),
                    new nexus.api.math.Vec3d(startX + 16, y, startZ),
                    ColorUtil.withAlpha(value, 120), 1.0);
            event.renderer.drawLine3D(new nexus.api.math.Vec3d(startX, y, startZ),
                    new nexus.api.math.Vec3d(startX, y, startZ + 16),
                    ColorUtil.withAlpha(value, 120), 1.0);
        }
    }

    public int visitedCount() {
        return visited.size();
    }

    @Override
    public String hudInfo() {
        return visited.size() + " chunks";
    }
}
