package nexus.api;

import nexus.api.math.Box3;
import nexus.api.math.Vec3d;

/** Read-only entity view shared by every Nexus subsystem. */
public interface IEntity {

    int id();

    String name();

    /** Stable entity type id, e.g. {@code minecraft:zombie}. */
    String typeId();

    Vec3d position();

    Vec3d eyePosition();

    Vec3d velocity();

    Box3 boundingBox();

    float yaw();

    float pitch();

    float health();

    float maxHealth();

    float distanceTo(IEntity other);

    double distanceToPlayer();

    boolean isAlive();

    boolean isPlayer();

    boolean isLocalPlayer();

    boolean isHostile();

    boolean isPassive();

    boolean isNeutral();

    boolean isItem();

    boolean isMob();

    boolean isVehicle();

    boolean isBaby();

    boolean isInvisible();

    boolean isSneaking();

    boolean isOnGround();

    boolean isInWater();

    boolean isGlowing();

    /** True when the entity can be attacked by the local player. */
    boolean isAttackable();

    IItemStack heldItem();

    /** Limb positions for skeleton ESP rendering: {leftArm, rightArm, leftLeg, rightLeg, body}. */
    float[] limbAngles();

    /** Body yaw used by skeleton rendering. */
    float bodyYaw();

    float height();

    float width();

    int airSupply();

    boolean isSpectator();

    int ping();

    int blockX();

    int blockY();

    int blockZ();

    boolean isBlocking();

    boolean isFallFlying();
}
