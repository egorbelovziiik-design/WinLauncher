package nexus.api;

import nexus.api.math.Box3;

import java.util.List;

/** World / level abstraction. */
public interface IWorld {

    boolean isLoaded();

    String dimensionId();

    String dimensionName();

    /** Namespaced dimension path, e.g. {@code overworld}. */
    String dimensionPath();

    List<IEntity> entities();

    List<IEntity> players();

    List<IEntity> mobs();

    List<IEntity> items();

    IEntity entityById(int id);

    /** Cheap block lookup used by world scanners on a worker-friendly path. */
    IBlockRef blockAt(int x, int y, int z);

    boolean isAir(int x, int y, int z);

    boolean isSolid(int x, int y, int z);

    int lightLevel(int x, int y, int z);

    int blockLight(int x, int y, int z);

    int skyLight(int x, int y, int z);

    boolean isChunkLoaded(int chunkX, int chunkZ);

    /** Scans a region calling the visitor for every non-air block. */
    void scanBlocks(Box3 region, BlockVisitor visitor);

    /** Visible chunk range used by cave finders and chunk borders. */
    int chunkRenderDistance();

    long timeOfDay();

    boolean isRaining();

    boolean isThundering();

    float rainLevel();

    String biomeAt(int x, int z);

    int seaLevel();

    int minHeight();

    int maxHeight();

    interface BlockVisitor {
        void visit(int x, int y, int z, String blockId);
    }

    /** Radius of the world border in blocks (0 when unknown). */
    int borderRadius();

    /** Asks the game to rebuild all chunk render sections. */
    void reloadRenderer();

    /** Name of the structure that starts in this chunk (null when none). */
    String structureAt(int chunkX, int chunkZ);

    /** Text of a sign or other text block entity (null when unavailable). */
    String blockEntityText(int x, int y, int z);

    /** Number of item slots of a container block entity (0 when not a container). */
    int blockEntitySize(int x, int y, int z);

    /** ARGB pixels of a filled map item rendered at the requested resolution. */
    int[] mapPixels(IItemStack stack, int resolution);

    /** World spawn position. */
    int spawnX();

    int spawnY();

    int spawnZ();
}
