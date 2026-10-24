package nexus.core.module.world;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Highlights slime chunks when the world seed is known. */
public final class SlimeChunksModule extends Module {

    private final ColorSetting color;
    private final NumberSetting radius;
    private final StringSetting seed;
    private final BooleanSetting beacons;

    public SlimeChunksModule() {
        super("slimechunks", "SlimeChunks", "Highlights slime chunks using the world seed.",
                ModuleCategory.WORLD);
        this.color = color("Color", "Slime chunk color", 0xFF4ADE80);
        this.radius = number("Radius", "Chunks around the player", 6.0, 1.0, 24.0, 1.0);
        this.seed = text("Seed", "World seed (leave empty to use the detected seed)", "");
        this.beacons = bool("Beacons", "Draw light beacons", true);
    }

    /** Standard Minecraft slime chunk algorithm. */
    public boolean isSlimeChunk(int chunkX, int chunkZ, long seed) {
        long value = seed
                + (long) (chunkX * chunkX * 4987142)
                + (long) (chunkX * 5947611)
                + (long) (chunkZ * chunkZ) * 4392871L
                + (long) (chunkZ * 389711) ^ 987234911L;
        java.util.Random random = new java.util.Random(value);
        return random.nextInt(10) == 0;
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        long worldSeed = resolveSeed();
        if (worldSeed == Long.MIN_VALUE) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        int chunkX = player().blockX() >> 4;
        int chunkZ = player().blockZ() >> 4;
        int radiusInt = (int) radius.value();
        for (int cx = chunkX - radiusInt; cx <= chunkX + radiusInt; cx++) {
            for (int cz = chunkZ - radiusInt; cz <= chunkZ + radiusInt; cz++) {
                if (!isSlimeChunk(cx, cz, worldSeed) || !mc().world().isChunkLoaded(cx, cz)) {
                    continue;
                }
                int startX = cx << 4;
                int startZ = cz << 4;
                double y = player().blockY() + 1.0;
                event.renderer.drawWorldQuad(new nexus.api.math.Vec3d(startX, y, startZ),
                        new nexus.api.math.Vec3d(startX + 16, y, startZ),
                        new nexus.api.math.Vec3d(startX + 16, y, startZ + 16),
                        new nexus.api.math.Vec3d(startX, y, startZ + 16),
                        ColorUtil.withAlpha(value, 40));
                if (beacons.isEnabled()) {
                    event.renderer.drawLine3D(new nexus.api.math.Vec3d(startX + 8, mc().world().minHeight(), startZ + 8),
                            new nexus.api.math.Vec3d(startX + 8, mc().world().maxHeight(), startZ + 8),
                            ColorUtil.withAlpha(value, 55), 1.0);
                }
            }
        }
    }

    private long resolveSeed() {
        String raw = seed.rawValue().trim();
        if (!raw.isEmpty()) {
            try {
                return Long.parseLong(raw);
            } catch (NumberFormatException ignored) {
                return Long.MIN_VALUE;
            }
        }
        nexus.core.module.Module helper = nexus.core.Nexus.instance().modules().byName("seedhelper");
        if (helper instanceof SeedHelperModule) {
            SeedHelperModule module = (SeedHelperModule) helper;
            if (module.known()) {
                return module.seed();
            }
        }
        return Long.MIN_VALUE;
    }
}
