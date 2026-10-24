package nexus.api;

/** Snapshot of a block state at a given position. */
public interface IBlockRef {

    String id();

    boolean isAir();

    boolean isSolid();

    boolean isOpaque();

    boolean isLiquid();

    int lightLevel();

    /** Light level required for mobs to spawn here (0 means mobs cannot spawn). */
    int blockLight();

    float hardness();

    boolean isOre();

    boolean isChest();

    boolean isSpawner();

    boolean isLeaf();

    boolean isLog();

    /** True when a liquid block is a full source block (not flowing). */
    boolean isSource();
}
