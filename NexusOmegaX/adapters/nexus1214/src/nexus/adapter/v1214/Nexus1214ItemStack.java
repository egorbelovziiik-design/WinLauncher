package nexus.adapter.v1214;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import nexus.api.IItemStack;

/** {@link IItemStack} implementation for Minecraft 1.21.4. */
public final class Nexus1214ItemStack implements IItemStack {

    private final ItemStack stack;

    public Nexus1214ItemStack(ItemStack stack) {
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
        return stack.get(net.minecraft.core.component.DataComponents.FOOD) != null;
    }

    @Override
    public int foodValue() {
        net.minecraft.world.food.FoodProperties food =
                stack.get(net.minecraft.core.component.DataComponents.FOOD);
        return food == null ? 0 : food.nutrition();
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
        double damage = 1.0;
        net.minecraft.world.item.component.ItemAttributeModifiers modifiers =
                stack.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                        net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
        for (net.minecraft.world.item.component.ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) {
                damage += entry.modifier().amount();
            }
        }
        return (float) damage;
    }

    @Override
    public boolean isInTag(String tag) {
        return stack.getTags().anyMatch(key -> key.location().toString().contains(tag));
    }

    @Override
    public boolean isTotem() {
        return stack.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
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
