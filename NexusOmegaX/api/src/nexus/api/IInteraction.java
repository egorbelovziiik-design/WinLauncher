package nexus.api;
import nexus.api.math.Vec3d;

/**
 * Block breaking / placing and container interaction bridge.
 * <p>
 * Everything here goes through the normal game packets: no protected memory is
 * touched and no operating system feature is bypassed.
 */
public interface IInteraction {

    /** Starts / continues breaking a block (sends the vanilla dig packets). */
    boolean breakBlock(int x, int y, int z);

    /** Stops breaking the current block (sent on module disable). */
    void stopBreaking();

    /** Places the held block against the given block face. */
    boolean placeBlock(int x, int y, int z, int faceIndex);

    /** Uses (right clicks) the block at the given position. */
    boolean useBlock(int x, int y, int z, int faceIndex);

    /** Attacks (left clicks) an entity through the normal attack path. */
    void attackEntity(IEntity entity);

    /** Interacts (right clicks) with an entity. */
    void interactEntity(IEntity entity);

    /** Clicks a container slot: see {@link nexus.api.IInventory.ClickType}. */
    void clickSlot(int syncId, int slot, int button, int actionType);

    /** Closes the currently open container. */
    void closeContainer();

    /** Currently open container sync id, -1 when no container is open. */
    int openSyncId();

    /** Rotates the player to face the center of a block. */
    void lookAtBlock(int x, int y, int z);

    /** Exact position the crosshair is pointing at (null when nothing is hit). */
    Vec3d hitPosition();

    /** Block coordinates the crosshair is pointing at (null when no block is hit). */
    int[] hitBlock();

    /** Entity the crosshair is pointing at (null when no entity is hit). */
    IEntity hitEntity();
}
