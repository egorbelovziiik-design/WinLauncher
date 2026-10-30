package dev.axsesclient.common.platform;

public final class ItemInfo {
    public static final ItemInfo EMPTY = new ItemInfo("", "", 0, 0, 0);

    private final String itemId;
    private final String displayName;
    private final int count;
    private final int damage;
    private final int maxDamage;

    public ItemInfo(String itemId, String displayName, int count, int damage, int maxDamage) {
        this.itemId = itemId == null ? "" : itemId;
        this.displayName = displayName == null ? "" : displayName;
        this.count = Math.max(0, count);
        this.damage = Math.max(0, damage);
        this.maxDamage = Math.max(0, maxDamage);
    }

    public boolean isEmpty() {
        return count <= 0 || itemId.isEmpty() || "minecraft:air".equals(itemId);
    }

    public String getItemId() {
        return itemId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getCount() {
        return count;
    }

    public int getDamage() {
        return damage;
    }

    public int getMaxDamage() {
        return maxDamage;
    }

    public boolean isDamageable() {
        return maxDamage > 0;
    }

    public int getRemainingDurability() {
        return maxDamage > 0 ? Math.max(0, maxDamage - damage) : 0;
    }

    public float getDurabilityRatio() {
        if (maxDamage <= 0) {
            return 1.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, (float) getRemainingDurability() / (float) maxDamage));
    }
}
