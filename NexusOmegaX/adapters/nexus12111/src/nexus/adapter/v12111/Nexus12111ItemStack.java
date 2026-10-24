package nexus.adapter.v12111;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import nexus.api.IItemStack;

/** {@link IItemStack} implementation for Minecraft 1.21.11. */
public final class Nexus12111ItemStack implements IItemStack {

    private final ItemStack stack;

    public Nexus12111ItemStack(ItemStack stack) {
        this.stack = stack;
    }

    public ItemStack handle() {
        return stack;
    }

    @Override
    public boolean isEmpty() {
        return stack == null || stack.isEmpty();
    }

    @Override
    public String id() {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    @Override
    public String displayName() {
        return stack.getHoverName().getString();
    }

    @Override
    public int count() {
        return stack.getCount();
    }

    @Override
    public int maxStackSize() {
        return stack.getMaxStackSize();
    }

    @Override
    public int damage() {
        return stack.getDamageValue();
    }

    @Override
    public int maxDamage() {
        return stack.getMaxDamage();
    }

    @Override
    public double durabilityRatio() {
        int max = stack.getMaxDamage();
        return max <= 0 ? 1.0 : 1.0 - (stack.getDamageValue() / (double) max);
    }

    @Override
    public boolean isDamageable() {
        return stack.isDamageableItem();
    }

    @Override
    public boolean isFood() {
        return stack.getItem().isEdible();
    }

    @Override
    public int foodValue() {
        return stack.getItem().getFoodProperties(stack, null) == null ? 0
                : stack.getItem().getFoodProperties(stack, null).nutrition();
    }

    @Override
    public boolean isTool() {
        return stack.getItem() instanceof net.minecraft.world.item.DiggerItem
                || stack.getItem() instanceof net.minecraft.world.item.ShearsItem;
    }

    @Override
    public boolean isWeapon() {
        return stack.getItem() instanceof net.minecraft.world.item.SwordItem
                || stack.getItem() instanceof net.minecraft.world.item.AxeItem
                || stack.getItem() instanceof net.minecraft.world.item.TridentItem;
    }

    @Override
    public boolean isArmor() {
        return stack.getItem() instanceof net.minecraft.world.item.ArmorItem;
    }

    @Override
    public boolean isBlockItem() {
        return stack.getItem() instanceof net.minecraft.world.item.BlockItem;
    }

    @Override
    public boolean isEnchanted() {
        return stack.isEnchanted();
    }

    @Override
    public float attackDamage() {
        return 1.0f + net.minecraft.world.item.enchantment.EnchantmentHelper.getDamageBonus(
                stack, net.minecraft.world.entity.MobType.UNDEFINED);
    }

    @Override
    public boolean isInTag(String tag) {
        return stack.getTags().anyMatch(key -> key.location().toString().contains(tag));
    }

    @Override
    public boolean isTotem() {
        return stack.getItem() instanceof net.minecraft.world.item.TotemItem;
    }

    @Override
    public boolean isPotion() {
        return stack.getItem() instanceof net.minecraft.world.item.PotionItem;
    }

    @Override
    public boolean isArrow() {
        return stack.getItem() instanceof net.minecraft.world.item.ArrowItem;
    }

    @Override
    public boolean isBucket() {
        return stack.getItem() instanceof net.minecraft.world.item.BucketItem;
    }
}
