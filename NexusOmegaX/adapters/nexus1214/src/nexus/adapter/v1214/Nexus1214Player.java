package nexus.adapter.v1214;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import nexus.api.*;
import nexus.api.math.Box3;
import nexus.api.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** {@link IPlayer} implementation for Minecraft 1.21.4. */
public final class Nexus1214Player implements IPlayer {

    private final Nexus1214Client client;

    Nexus1214Player(Nexus1214Client client) {
        this.client = client;
    }

    private LocalPlayer self() {
        return client.localPlayer();
    }

    private boolean ready() {
        return self() != null;
    }

    @Override
    public boolean exists() {
        return ready();
    }

    @Override
    public String name() {
        return ready() ? self().getName().getString() : "";
    }

    @Override
    public Vec3d position() {
        if (!ready()) {
            return new Vec3d(0.0, 0.0, 0.0);
        }
        Vec3 position = self().position();
        return new Vec3d(position.x, position.y, position.z);
    }

    @Override
    public Vec3d eyePosition() {
        if (!ready()) {
            return new Vec3d(0.0, 0.0, 0.0);
        }
        Vec3 position = self().getEyePosition();
        return new Vec3d(position.x, position.y, position.z);
    }

    @Override
    public Vec3d velocity() {
        if (!ready()) {
            return new Vec3d(0.0, 0.0, 0.0);
        }
        Vec3 velocity = self().getDeltaMovement();
        return new Vec3d(velocity.x, velocity.y, velocity.z);
    }

    @Override
    public Box3 boundingBox() {
        if (!ready()) {
            return new Box3(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }
        net.minecraft.world.phys.AABB box = self().getBoundingBox();
        return new Box3(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    @Override
    public float yaw() {
        return ready() ? self().getYRot() : 0.0f;
    }

    @Override
    public float pitch() {
        return ready() ? self().getXRot() : 0.0f;
    }

    @Override
    public void setRotation(float yaw, float pitch) {
        if (ready()) {
            self().setYRot(yaw);
            self().setXRot(pitch);
        }
    }

    @Override
    public void lookAt(Vec3d target) {
        if (!ready()) {
            return;
        }
        Vec3 eye = self().getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        setRotation((float) Math.toDegrees(Math.atan2(-dx, dz)),
                (float) -Math.toDegrees(Math.atan2(dy, horizontal)));
    }

    @Override
    public void rotateTowards(Vec3d target, double maxDegreesPerTick) {
        lookAt(target);
    }

    @Override
    public float health() {
        return ready() ? self().getHealth() : 0.0f;
    }

    @Override
    public float maxHealth() {
        return ready() ? self().getMaxHealth() : 20.0f;
    }

    @Override
    public float absorption() {
        return ready() ? self().getAbsorptionAmount() : 0.0f;
    }

    @Override
    public float hunger() {
        return ready() ? self().getFoodData().getFoodLevel() : 0.0f;
    }

    @Override
    public float saturation() {
        return ready() ? self().getFoodData().getSaturationLevel() : 0.0f;
    }

    @Override
    public int airSupply() {
        return ready() ? self().getAirSupply() : 0;
    }

    @Override
    public int maxAirSupply() {
        return ready() ? self().getMaxAirSupply() : 300;
    }

    @Override
    public float experienceLevel() {
        return ready() ? self().experienceLevel : 0.0f;
    }

    @Override
    public float experienceProgress() {
        return ready() ? self().experienceProgress : 0.0f;
    }

    @Override
    public boolean isAlive() {
        return ready() && self().isAlive();
    }

    @Override
    public boolean isDead() {
        return ready() && self().isDeadOrDying();
    }

    @Override
    public boolean isCreative() {
        return ready() && self().isCreative();
    }

    @Override
    public boolean isSpectator() {
        return ready() && self().isSpectator();
    }

    @Override
    public boolean isSprinting() {
        return ready() && self().isSprinting();
    }

    @Override
    public boolean isSneaking() {
        return ready() && self().isShiftKeyDown();
    }

    @Override
    public boolean isOnGround() {
        return ready() && self().onGround();
    }

    @Override
    public boolean isInWater() {
        return ready() && self().isInWater();
    }

    @Override
    public boolean isInLava() {
        return ready() && self().isInLava();
    }

    @Override
    public boolean isClimbing() {
        return ready() && self().onClimbable();
    }

    @Override
    public boolean isFallFlying() {
        return ready() && self().isFallFlying();
    }

    @Override
    public boolean isRiding() {
        return ready() && self().isPassenger();
    }

    @Override
    public boolean isSleeping() {
        return ready() && self().isSleeping();
    }

    @Override
    public boolean isUsingItem() {
        return ready() && self().isUsingItem();
    }

    @Override
    public boolean isBlocking() {
        return ready() && self().isBlocking();
    }

    @Override
    public boolean isHurt() {
        return ready() && self().hurtTime > 0;
    }

    @Override
    public int hurtTime() {
        return ready() ? self().hurtTime : 0;
    }

    @Override
    public float fallDistance() {
        return ready() ? self().fallDistance : 0.0f;
    }

    @Override
    public float attackCooldown() {
        return ready() ? self().getAttackStrengthScale(0.0f) : 0.0f;
    }

    @Override
    public int ping() {
        return client.connection().ping();
    }

    @Override
    public IInventory inventory() {
        return client.inventory();
    }

    @Override
    public List<IPotionEffect> potionEffects() {
        List<IPotionEffect> result = new ArrayList<IPotionEffect>();
        if (!ready()) {
            return result;
        }
        for (MobEffectInstance instance : self().getActiveEffects()) {
            result.add(new IPotionEffect() {
                @Override
                public String name() {
                    return instance.getEffect().value().getDisplayName().getString();
                }

                @Override
                public int duration() {
                    return instance.getDuration();
                }

                @Override
                public int amplifier() {
                    return instance.getAmplifier();
                }

                @Override
                public int color() {
                    return instance.getEffect().value().getColor();
                }

                @Override
                public boolean isAmbient() {
                    return instance.isAmbient();
                }

                @Override
                public boolean showParticles() {
                    return instance.isVisible();
                }

                @Override
                public boolean isBeneficial() {
                    return instance.getEffect().value().isBeneficial();
                }
            });
        }
        return result;
    }

    @Override
    public void setSprinting(boolean value) {
        if (ready()) {
            self().setSprinting(value);
        }
    }

    @Override
    public void setSneaking(boolean value) {
        if (ready()) {
            self().setShiftKeyDown(value);
        }
    }

    @Override
    public void jump() {
        if (ready() && self().onGround()) {
            self().jumpFromGround();
        }
    }

    @Override
    public void swingHand() {
        if (ready()) {
            self().swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }

    @Override
    public void attack(IEntity target) {
        if (!ready() || target == null) {
            return;
        }
        net.minecraft.world.entity.Entity entity = ((Nexus1214Entity) target).handle();
        client.minecraft().gameMode.attack(self(), entity);
        swingHand();
    }

    @Override
    public void startUsingItem() {
        if (ready()) {
            client.minecraft().gameMode.useItem(self(), net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }

    @Override
    public void stopUsingItem() {
        if (ready()) {
            self().releaseUsingItem();
        }
    }

    @Override
    public void dropSelectedItem(boolean all) {
        if (ready()) {
            self().drop(all);
        }
    }

    @Override
    public void respawn() {
        if (ready()) {
            self().respawn();
        }
    }

    @Override
    public void sendChat(String message) {
        if (ready()) {
            if (message.startsWith("/")) {
                self().connection.sendCommand(message.substring(1));
            } else {
                self().connection.sendChat(message);
            }
        }
    }

    @Override
    public void sendCommand(String command) {
        if (ready()) {
            self().connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
        }
    }

    @Override
    public void setVelocity(Vec3d velocity) {
        if (ready()) {
            self().setDeltaMovement(velocity.x, velocity.y, velocity.z);
        }
    }

    @Override
    public void setPosition(Vec3d position) {
        if (ready()) {
            self().setPos(position.x, position.y, position.z);
        }
    }

    @Override
    public double horizontalSpeed() {
        Vec3d velocity = velocity();
        return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    }

    @Override
    public double speedBps() {
        return horizontalSpeed() * 20.0;
    }

    @Override
    public int blockX() {
        return ready() ? self().getBlockX() : 0;
    }

    @Override
    public int blockY() {
        return ready() ? self().getBlockY() : 0;
    }

    @Override
    public int blockZ() {
        return ready() ? self().getBlockZ() : 0;
    }

    @Override
    public String facing() {
        if (!ready()) {
            return "north";
        }
        return self().getDirection().getName();
    }

    @Override
    public float itemCooldown(String itemId) {
        return 0.0f;
    }

    @Override
    public int score() {
        return ready() ? self().getScore() : 0;
    }

    /** Exposed for the mixins that need the vanilla entity. */
    public EquipmentSlot[] armorSlots() {
        return new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    }
}
