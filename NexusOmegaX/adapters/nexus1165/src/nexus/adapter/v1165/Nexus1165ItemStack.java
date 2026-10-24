package nexus.adapter.v1165;

import net.minecraft.item.ItemStack;
import net.minecraft.util.registry.Registry;
import nexus.api.IItemStack;

/** {@link IItemStack} implementation for Minecraft 1.16.5. */
public final class Nexus1165ItemStack implements IItemStack {

    private final ItemStack stack;

    public Nexus1165ItemStack(ItemStack stack) {
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
        return Registry.ITEM.getId(stack.getItem()).toString();
    }

    @Override
    public String displayName() {
        return stack.getName().getString();
    }

    @Override
    public int count() {
        return stack.getCount();
    }

    @Override
    public int maxStackSize() {
        return stack.getMaxCount();
    }

    @Override
    public int damage() {
        return stack.getDamage();
    }

    @Override
    public int maxDamage() {
        return stack.getMaxDamage();
    }

    @Override
    public double durabilityRatio() {
        int max = stack.getMaxDamage();
        return max <= 0 ? 1.0 : 1.0 - (stack.getDamage() / (double) max);
    }

    @Override
    public boolean isDamageable() {
        return stack.isDamageable();
    }

    @Override
    public boolean isFood() {
        return stack.getItem().isFood();
    }

    @Override
    public int foodValue() {
        return stack.getItem().getFoodComponent() == null ? 0
                : stack.getItem().getFoodComponent().getHunger();
    }

    @Override
    public boolean isTool() {
        return stack.getItem() instanceof net.minecraft.item.ToolItem
                || stack.getItem() instanceof net.minecraft.item.ShearsItem;
    }

    @Override
    public boolean isWeapon() {
        return stack.getItem() instanceof net.minecraft.item.SwordItem
                || stack.getItem() instanceof net.minecraft.item.AxeItem
                || stack.getItem() instanceof net.minecraft.item.TridentItem;
    }

    @Override
    public boolean isArmor() {
        return stack.getItem() instanceof net.minecraft.item.ArmorItem;
    }

    @Override
    public boolean isBlockItem() {
        return stack.getItem() instanceof net.minecraft.item.BlockItem;
    }

    @Override
    public boolean isEnchanted() {
        return stack.hasEnchantments();
    }

    @Override
    public float attackDamage() {
        return 1.0f + net.minecraft.enchantment.EnchantmentHelper.getAttackDamage(stack,
                net.minecraft.entity.EntityGroup.DEFAULT);
    }

    @Override
    public boolean isInTag(String tag) {
        net.minecraft.client.network.ClientPlayNetworkHandler handler =
                net.minecraft.client.MinecraftClient.getInstance().getNetworkHandler();
        if (handler == null || handler.getTagManager() == null) {
            return false;
        }
        net.minecraft.tag.Tag<net.minecraft.item.Item> entry = handler
                .getTagManager().getItems()
                .getTag(new net.minecraft.util.Identifier(tag.indexOf(':') >= 0 ? tag : "minecraft:" + tag));
        return entry != null && stack.getItem().isIn(entry);
    }

    @Override
    public boolean isTotem() {
        return stack.getItem() == net.minecraft.item.Items.TOTEM_OF_UNDYING;
    }

    @Override
    public boolean isPotion() {
        return stack.getItem() instanceof net.minecraft.item.PotionItem;
    }

    @Override
    public boolean isArrow() {
        return stack.getItem() instanceof net.minecraft.item.ArrowItem;
    }

    @Override
    public boolean isBucket() {
        return stack.getItem() instanceof net.minecraft.item.BucketItem;
    }
}
