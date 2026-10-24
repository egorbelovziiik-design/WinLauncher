package nexus.adapter.v12111;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.registries.BuiltInRegistries;
import nexus.api.IEntity;
import nexus.api.IItemStack;
import nexus.api.math.Box3;
import nexus.api.math.Vec3d;

/** {@link IEntity} implementation for Minecraft 1.21.11. */
public final class Nexus12111Entity implements IEntity {

    private final Entity entity;

    public Nexus12111Entity(Entity entity) {
        this.entity = entity;
    }

    public Entity handle() {
        return entity;
    }

    @Override
    public int id() {
        return entity.getId();
    }

    @Override
    public String name() {
        return entity.getName().getString();
    }

    @Override
    public String typeId() {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    @Override
    public Vec3d position() {
        return new Vec3d(entity.getX(), entity.getY(), entity.getZ());
    }

    @Override
    public Vec3d eyePosition() {
        return new Vec3d(entity.getX(), entity.getEyeY(), entity.getZ());
    }

    @Override
    public Vec3d velocity() {
        net.minecraft.world.phys.Vec3 velocity = entity.getDeltaMovement();
        return new Vec3d(velocity.x, velocity.y, velocity.z);
    }

    @Override
    public Box3 boundingBox() {
        net.minecraft.world.phys.AABB box = entity.getBoundingBox();
        return new Box3(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    @Override
    public float yaw() {
        return entity.getYRot();
    }

    @Override
    public float pitch() {
        return entity.getXRot();
    }

    @Override
    public float health() {
        return entity instanceof LivingEntity ? ((LivingEntity) entity).getHealth() : 0.0f;
    }

    @Override
    public float maxHealth() {
        return entity instanceof LivingEntity ? ((LivingEntity) entity).getMaxHealth() : 0.0f;
    }

    @Override
    public float distanceTo(IEntity other) {
        return entity.distanceTo(((Nexus12111Entity) other).entity);
    }

    @Override
    public double distanceToPlayer() {
        return entity.distanceTo(net.minecraft.client.Minecraft.getInstance().player);
    }

    @Override
    public boolean isAlive() {
        return entity.isAlive();
    }

    @Override
    public boolean isPlayer() {
        return entity instanceof Player;
    }

    @Override
    public boolean isLocalPlayer() {
        return entity == net.minecraft.client.Minecraft.getInstance().player;
    }

    @Override
    public boolean isHostile() {
        return entity instanceof net.minecraft.world.entity.monster.Monster
                || (entity instanceof Mob && ((Mob) entity).getTarget() == net.minecraft.client.Minecraft.getInstance().player);
    }

    @Override
    public boolean isPassive() {
        return entity instanceof net.minecraft.world.entity.animal.Animal;
    }

    @Override
    public boolean isNeutral() {
        return entity instanceof net.minecraft.world.entity.monster.piglin.Piglin
                || entity instanceof net.minecraft.world.entity.monster.EnderMan
                || entity instanceof net.minecraft.world.entity.animal.Wolf;
    }

    @Override
    public boolean isItem() {
        return entity instanceof ItemEntity;
    }

    @Override
    public boolean isMob() {
        return entity instanceof Mob;
    }

    @Override
    public boolean isVehicle() {
        return entity.getType().toString().contains("boat") || entity.getType().toString().contains("minecart");
    }

    @Override
    public boolean isBaby() {
        return entity instanceof LivingEntity && ((LivingEntity) entity).isBaby();
    }

    @Override
    public boolean isInvisible() {
        return entity.isInvisible();
    }

    @Override
    public boolean isSneaking() {
        return entity.isShiftKeyDown();
    }

    @Override
    public boolean isOnGround() {
        return entity.onGround();
    }

    @Override
    public boolean isInWater() {
        return entity.isInWater();
    }

    @Override
    public boolean isGlowing() {
        return entity.isCurrentlyGlowing();
    }

    @Override
    public boolean isAttackable() {
        return entity instanceof LivingEntity && entity.isAttackable();
    }

    @Override
    public IItemStack heldItem() {
        if (entity instanceof LivingEntity) {
            return new Nexus12111ItemStack(((LivingEntity) entity).getMainHandItem());
        }
        return new Nexus12111ItemStack(net.minecraft.world.item.ItemStack.EMPTY);
    }

    @Override
    public float[] limbAngles() {
        if (entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) entity;
            return new float[]{living.animationPosition, living.animationSpeed, living.attackAnim,
                    living.yBodyRot, living.yHeadRot, living.walkAnimation.position()};
        }
        return new float[6];
    }

    @Override
    public float bodyYaw() {
        return entity instanceof LivingEntity ? ((LivingEntity) entity).yBodyRot : entity.getYRot();
    }

    @Override
    public float height() {
        return entity.getBbHeight();
    }

    @Override
    public float width() {
        return entity.getBbWidth();
    }

    @Override
    public int airSupply() {
        return entity instanceof LivingEntity ? ((LivingEntity) entity).getAirSupply() : 300;
    }

    @Override
    public boolean isSpectator() {
        return entity.isSpectator();
    }

    @Override
    public int ping() {
        return 0;
    }

    @Override
    public int blockX() {
        return entity.getBlockX();
    }

    @Override
    public int blockY() {
        return entity.getBlockY();
    }

    @Override
    public int blockZ() {
        return entity.getBlockZ();
    }

    @Override
    public boolean isBlocking() {
        return entity instanceof LivingEntity && ((LivingEntity) entity).isBlocking();
    }

    @Override
    public boolean isFallFlying() {
        return entity instanceof LivingEntity && ((LivingEntity) entity).isFallFlying();
    }
}
