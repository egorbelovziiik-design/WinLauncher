package nexus.api;

/** Read-only view of a Minecraft item stack. */
public interface IItemStack {

    boolean isEmpty();

    String id();

    String displayName();

    int count();

    int maxStackSize();

    int damage();

    int maxDamage();

    /** 0..1 remaining durability. */
    double durabilityRatio();

    boolean isDamageable();

    boolean isFood();

    int foodValue();

    boolean isTool();

    boolean isWeapon();

    boolean isArmor();

    boolean isBlockItem();

    boolean isEnchanted();

    float attackDamage();

    /** Simple tag lookup, e.g. {@code tag("minecraft:swords")}. */
    boolean isInTag(String tag);

    boolean isTotem();

    boolean isPotion();

    boolean isArrow();

    boolean isBucket();
}
