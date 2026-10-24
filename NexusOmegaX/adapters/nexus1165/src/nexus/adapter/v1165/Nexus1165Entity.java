package nexus.adapter.v1165;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import nexus.api.IEntity;
import nexus.api.IItemStack;
import nexus.api.math.Box3;

/** {@link IEntity} implementation for Minecraft 1.16.5. */
public final class Nexus1165Entity implements IEntity {

    private final Entity entity;

    public Nexus1165Entity(Entity entity) {
        this.entity = entity;
    }

    public Entity handle() {
        return entity;
    }

    @Override
    public int id() {
        return entity.getEntityId();
    }

    @Override
    public String name() {
        return entity.getName().getString();
    }

    @Override
    public String typeId() {
        return Registry.ENTITY_TYPE.getId(entity.getType()).toString();
    }

    @Override
    public nexus.api.math.Vec3d position() {
        return new nexus.api.math.Vec3d(entity.getX(), entity.getY(), entity.getZ());
    }

    @Override
    public nexus.api.math.Vec3d eyePosition() {
        return new nexus.api.math.Vec3d(entity.getX(), entity.getEyeY(), entity.getZ());
    }

    @Override
    public nexus.api.math.Vec3d velocity() {
        Vec3d velocity = entity.getVelocity();
        return new nexus.api.math.Vec3d(velocity.x, velocity.y, velocity.z);
    }

    @Override
    public Box3 boundingBox() {
        Box box = entity.getBoundingBox();
        return new Box3(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    @Override
    public float yaw() {
        return entity.yaw;
    }

    @Override
    public float pitch() {
        return entity.pitch;
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
        return entity.distanceTo(((Nexus1165Entity) other).entity);
    }

    @Override
    public double distanceToPlayer() {
        return entity.distanceTo(MinecraftClient.getInstance().player);
    }

    @Override
    public boolean isAlive() {
        return entity.isAlive();
    }

    @Override
    public boolean isPlayer() {
        return entity instanceof PlayerEntity;
    }

    @Override
    public boolean isLocalPlayer() {
        return entity == MinecraftClient.getInstance().player;
    }

    @Override
    public boolean isHostile() {
        return entity instanceof net.minecraft.entity.mob.HostileEntity;
    }

    @Override
    public boolean isPassive() {
        return entity instanceof net.minecraft.entity.passive.AnimalEntity;
    }

    @Override
    public boolean isNeutral() {
        return entity instanceof net.minecraft.entity.mob.PiglinEntity
                || entity instanceof net.minecraft.entity.mob.EndermanEntity;
    }

    @Override
    public boolean isItem() {
        return entity instanceof ItemEntity;
    }

    @Override
    public boolean isMob() {
        return entity instanceof net.minecraft.entity.mob.MobEntity;
    }

    @Override
    public boolean isVehicle() {
        return entity.hasVehicle();
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
        return entity.isSneaking();
    }

    @Override
    public boolean isOnGround() {
        return entity.isOnGround();
    }

    @Override
    public boolean isInWater() {
        return entity.isInWater();
    }

    @Override
    public boolean isGlowing() {
        return entity.isGlowing();
    }

    @Override
    public boolean isAttackable() {
        return entity instanceof LivingEntity && entity.isAttackable();
    }

    @Override
    public IItemStack heldItem() {
        return new Nexus1165ItemStack(entity instanceof LivingEntity
                ? ((LivingEntity) entity).getMainHandStack()
                : net.minecraft.item.ItemStack.EMPTY);
    }

    @Override
    public float[] limbAngles() {
        return new float[]{entity instanceof LivingEntity ? ((LivingEntity) entity).limbAngle : 0.0f,
                entity instanceof LivingEntity ? ((LivingEntity) entity).limbDistance : 0.0f,
                entity instanceof LivingEntity ? ((LivingEntity) entity).handSwingProgress : 0.0f,
                entity instanceof LivingEntity ? ((LivingEntity) entity).bodyYaw : entity.yaw,
                entity instanceof LivingEntity ? ((LivingEntity) entity).headYaw : entity.yaw, 0.0f};
    }

    @Override
    public float bodyYaw() {
        return entity instanceof LivingEntity ? ((LivingEntity) entity).bodyYaw : entity.yaw;
    }

    @Override
    public float height() {
        return entity.getHeight();
    }

    @Override
    public float width() {
        return entity.getWidth();
    }

    @Override
    public int airSupply() {
        return entity instanceof LivingEntity ? ((LivingEntity) entity).getAir() : 300;
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
        return net.minecraft.util.math.MathHelper.floor(entity.getX());
    }

    @Override
    public int blockY() {
        return net.minecraft.util.math.MathHelper.floor(entity.getY());
    }

    @Override
    public int blockZ() {
        return net.minecraft.util.math.MathHelper.floor(entity.getZ());
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
