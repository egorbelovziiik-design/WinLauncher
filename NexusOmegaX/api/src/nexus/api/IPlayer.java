package nexus.api;

import nexus.api.math.Box3;
import nexus.api.math.Vec3d;

/** The local player. Mutating methods are only valid on the client thread. */
public interface IPlayer {

    boolean exists();

    String name();

    Vec3d position();

    Vec3d eyePosition();

    Vec3d velocity();

    Box3 boundingBox();

    float yaw();

    float pitch();

    void setRotation(float yaw, float pitch);

    void lookAt(Vec3d target);

    /** Smoothly rotates toward a target, used by AimAssist / FreeLook helpers. */
    void rotateTowards(Vec3d target, double maxDegreesPerTick);

    float health();

    float maxHealth();

    float absorption();

    float hunger();

    float saturation();

    int airSupply();

    int maxAirSupply();

    float experienceLevel();

    float experienceProgress();

    boolean isAlive();

    boolean isDead();

    boolean isCreative();

    boolean isSpectator();

    boolean isSprinting();

    boolean isSneaking();

    boolean isOnGround();

    boolean isInWater();

    boolean isInLava();

    boolean isClimbing();

    boolean isFallFlying();

    boolean isRiding();

    boolean isSleeping();

    boolean isUsingItem();

    boolean isBlocking();

    boolean isHurt();

    /** Ticks since the last received damage. */
    int hurtTime();

    float fallDistance();

    /** Server side cooldown of the attack cooldown (1.0 = fully charged). */
    float attackCooldown();

    int ping();

    IInventory inventory();

    /** Active status effects of the local player. */
    java.util.List<IPotionEffect> potionEffects();

    // ------------------------------------------------------------------ actions

    void setSprinting(boolean value);

    void setSneaking(boolean value);

    void jump();

    void swingHand();

    void attack(IEntity target);

    void startUsingItem();

    void stopUsingItem();

    void dropSelectedItem(boolean all);

    void respawn();

    void sendChat(String message);

    /** Sends a command (with or without leading slash). */
    void sendCommand(String command);

    /** Directly moves the player, clamped by the server to legit values. */
    void setVelocity(Vec3d velocity);

    /** Local-only position nudge used by Freecam and similar spectator tools. */
    void setPosition(Vec3d position);

    double horizontalSpeed();

    /** Blocks per second of horizontal movement. */
    double speedBps();

    int blockX();

    int blockY();

    int blockZ();

    /** Direction name shown by the HUD compass element. */
    String facing();

    /** Remaining cooldown in ticks of an item, 0 when not cooling down. */
    float itemCooldown(String itemId);

    /** Current experience or scoreboard score, used by stats elements. */
    int score();
}
