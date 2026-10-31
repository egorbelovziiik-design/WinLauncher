package dev.axsesclient.common.platform;

/**
 * Immutable cross-version snapshot of a world entity (Player, Mob, or Item)
 * for Combat (KillAura / TriggerBot), Target HUD, and 2D/3D Projected ESP.
 */
public final class TargetEntityInfo {
    private final int entityId;
    private final String name;
    private final boolean player;
    private final boolean hostile;
    private final boolean passive;
    private final boolean itemEntity;
    private final float health;
    private final float maxHealth;
    private final float absorption;
    private final float distance;
    private final int hurtTime;
    private final int armorValue;
    private final double x;
    private final double y;
    private final double z;
    private final double eyeY;
    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;
    private final boolean lineOfSight;
    private final String heldItemName;

    public TargetEntityInfo(
            int entityId,
            String name,
            boolean player,
            boolean hostile,
            boolean passive,
            float health,
            float maxHealth,
            float absorption,
            float distance,
            int hurtTime,
            int armorValue,
            double x,
            double y,
            double z,
            double eyeY
    ) {
        this(entityId, name, player, hostile, passive, false,
                health, maxHealth, absorption, distance, hurtTime, armorValue,
                x, y, z, eyeY,
                x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3,
                true, "");
    }

    public TargetEntityInfo(
            int entityId,
            String name,
            boolean player,
            boolean hostile,
            boolean passive,
            boolean itemEntity,
            float health,
            float maxHealth,
            float absorption,
            float distance,
            int hurtTime,
            int armorValue,
            double x,
            double y,
            double z,
            double eyeY,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            boolean lineOfSight,
            String heldItemName
    ) {
        this.entityId = entityId;
        this.name = name != null ? name : "Entity";
        this.player = player;
        this.hostile = hostile;
        this.passive = passive;
        this.itemEntity = itemEntity;
        this.health = Math.max(0.0f, health);
        this.maxHealth = Math.max(1.0f, maxHealth);
        this.absorption = Math.max(0.0f, absorption);
        this.distance = Math.max(0.0f, distance);
        this.hurtTime = Math.max(0, hurtTime);
        this.armorValue = Math.max(0, armorValue);
        this.x = x;
        this.y = y;
        this.z = z;
        this.eyeY = eyeY;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.lineOfSight = lineOfSight;
        this.heldItemName = heldItemName != null ? heldItemName : "";
    }

    public int getEntityId() { return entityId; }
    public String getName() { return name; }
    public boolean isPlayer() { return player; }
    public boolean isHostile() { return hostile; }
    public boolean isPassive() { return passive; }
    public boolean isItemEntity() { return itemEntity; }
    public float getHealth() { return health; }
    public float getMaxHealth() { return maxHealth; }
    public float getAbsorption() { return absorption; }
    public float getHealthRatio() { return Math.max(0.0f, Math.min(1.0f, health / maxHealth)); }
    public float getDistance() { return distance; }
    public int getHurtTime() { return hurtTime; }
    public int getArmorValue() { return armorValue; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public double getEyeY() { return eyeY; }
    public double getMinX() { return minX; }
    public double getMinY() { return minY; }
    public double getMinZ() { return minZ; }
    public double getMaxX() { return maxX; }
    public double getMaxY() { return maxY; }
    public double getMaxZ() { return maxZ; }
    public boolean hasLineOfSight() { return lineOfSight; }
    public String getHeldItemName() { return heldItemName; }
}
