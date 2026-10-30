package dev.axsesclient.common.platform;

public final class TargetEntityInfo {
    private final int entityId;
    private final String name;
    private final boolean player;
    private final boolean hostile;
    private final boolean passive;
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
        this.entityId = entityId;
        this.name = name == null || name.isEmpty() ? "Entity" : name;
        this.player = player;
        this.hostile = hostile;
        this.passive = passive;
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
    }

    public int getEntityId() { return entityId; }
    public String getName() { return name; }
    public boolean isPlayer() { return player; }
    public boolean isHostile() { return hostile; }
    public boolean isPassive() { return passive; }
    public float getHealth() { return health; }
    public float getMaxHealth() { return maxHealth; }
    public float getAbsorption() { return absorption; }
    public float getDistance() { return distance; }
    public int getHurtTime() { return hurtTime; }
    public int getArmorValue() { return armorValue; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public double getEyeY() { return eyeY; }

    public float getHealthFraction() {
        return Math.max(0.0f, Math.min(1.0f, health / Math.max(1.0f, maxHealth)));
    }
}
