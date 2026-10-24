package nexus.adapter.v1165;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import nexus.api.*;
import nexus.api.math.Box3;

import java.util.ArrayList;
import java.util.List;

/** {@link IPlayer} implementation for Minecraft 1.16.5. */
public final class Nexus1165Player implements IPlayer {

    private final Nexus1165Client client;

    Nexus1165Player(Nexus1165Client client) {
        this.client = client;
    }

    private ClientPlayerEntity self() {
        return client.localPlayer();
    }

    private boolean ready() {
        return self() != null;
    }

    private nexus.api.math.Vec3d convert(Vec3d value) {
        return new nexus.api.math.Vec3d(value.x, value.y, value.z);
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
    public nexus.api.math.Vec3d position() {
        return ready() ? convert(self().getPos()) : new nexus.api.math.Vec3d(0, 0, 0);
    }

    @Override
    public nexus.api.math.Vec3d eyePosition() {
        return ready() ? convert(self().getCameraPosVec(1.0f)) : new nexus.api.math.Vec3d(0, 0, 0);
    }

    @Override
    public nexus.api.math.Vec3d velocity() {
        return ready() ? convert(self().getVelocity()) : new nexus.api.math.Vec3d(0, 0, 0);
    }

    @Override
    public Box3 boundingBox() {
        if (!ready()) {
            return new Box3(0, 0, 0, 0, 0, 0);
        }
        Box box = self().getBoundingBox();
        return new Box3(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    @Override
    public float yaw() {
        return ready() ? self().yaw : 0.0f;
    }

    @Override
    public float pitch() {
        return ready() ? self().pitch : 0.0f;
    }

    @Override
    public void setRotation(float yaw, float pitch) {
        if (ready()) {
            self().yaw = yaw;
            self().pitch = pitch;
        }
    }

    @Override
    public void lookAt(nexus.api.math.Vec3d target) {
        if (!ready()) {
            return;
        }
        Vec3d eye = self().getCameraPosVec(1.0f);
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        setRotation((float) Math.toDegrees(Math.atan2(-dx, dz)),
                (float) -Math.toDegrees(Math.atan2(dy, horizontal)));
    }

    @Override
    public void rotateTowards(nexus.api.math.Vec3d target, double maxDegreesPerTick) {
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
        return ready() ? self().getHungerManager().getFoodLevel() : 0.0f;
    }

    @Override
    public float saturation() {
        return ready() ? self().getHungerManager().getSaturationLevel() : 0.0f;
    }

    @Override
    public int airSupply() {
        return ready() ? self().getAir() : 0;
    }

    @Override
    public int maxAirSupply() {
        return ready() ? self().getMaxAir() : 300;
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
        return ready() && self().isDead();
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
        return ready() && self().isSneaking();
    }

    @Override
    public boolean isOnGround() {
        return ready() && self().isOnGround();
    }

    @Override
    public boolean isInWater() {
        return ready() && self().isTouchingWater();
    }

    @Override
    public boolean isInLava() {
        return ready() && self().isInLava();
    }

    @Override
    public boolean isClimbing() {
        return ready() && self().isClimbing();
    }

    @Override
    public boolean isFallFlying() {
        return ready() && self().isFallFlying();
    }

    @Override
    public boolean isRiding() {
        return ready() && self().hasVehicle();
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
        return ready() ? self().getAttackCooldownProgress(0.0f) : 0.0f;
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
        final List<IPotionEffect> result = new ArrayList<IPotionEffect>();
        if (!ready()) {
            return result;
        }
        for (StatusEffectInstance instance : self().getStatusEffects()) {
            result.add(new IPotionEffect() {
                @Override
                public String name() {
                    return net.minecraft.text.TranslatableText(instance.getTranslationKey()).asString();
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
                    return instance.getEffectType().getColor();
                }

                @Override
                public boolean isAmbient() {
                    return instance.isAmbient();
                }

                @Override
                public boolean showParticles() {
                    return instance.shouldShowParticles();
                }

                @Override
                public boolean isBeneficial() {
                    return instance.getEffectType().isBeneficial();
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
            self().setSneaking(value);
        }
    }

    @Override
    public void jump() {
        if (ready() && self().isOnGround()) {
            self().jump();
        }
    }

    @Override
    public void swingHand() {
        if (ready()) {
            self().swingHand(net.minecraft.util.Hand.MAIN_HAND);
        }
    }

    @Override
    public void attack(IEntity target) {
        if (!ready() || target == null) {
            return;
        }
        client.handle().interactionManager.attackEntity(self(), ((Nexus1165Entity) target).handle());
        swingHand();
    }

    @Override
    public void startUsingItem() {
        if (ready()) {
            client.handle().interactionManager.interactItem(self(), self().world,
                    net.minecraft.util.Hand.MAIN_HAND);
        }
    }

    @Override
    public void stopUsingItem() {
        if (ready()) {
            self().clearActiveItem();
        }
    }

    @Override
    public void dropSelectedItem(boolean all) {
        if (ready()) {
            self().dropSelectedItem(all);
        }
    }

    @Override
    public void respawn() {
        if (ready()) {
            self().requestRespawn();
        }
    }

    @Override
    public void sendChat(String message) {
        if (ready()) {
            if (message.startsWith("/")) {
                self().sendChatMessage(message);
            } else {
                self().sendChatMessage(message);
            }
        }
    }

    @Override
    public void sendCommand(String command) {
        if (ready()) {
            self().sendChatMessage(command.startsWith("/") ? command : "/" + command);
        }
    }

    @Override
    public void setVelocity(nexus.api.math.Vec3d velocity) {
        if (ready()) {
            self().setVelocity(velocity.x, velocity.y, velocity.z);
        }
    }

    @Override
    public void setPosition(nexus.api.math.Vec3d position) {
        if (ready()) {
            self().setPos(position.x, position.y, position.z);
        }
    }

    @Override
    public double horizontalSpeed() {
        nexus.api.math.Vec3d velocity = velocity();
        return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    }

    @Override
    public double speedBps() {
        return horizontalSpeed() * 20.0;
    }

    @Override
    public int blockX() {
        return ready() ? net.minecraft.util.math.MathHelper.floor(self().getX()) : 0;
    }

    @Override
    public int blockY() {
        return ready() ? net.minecraft.util.math.MathHelper.floor(self().getY()) : 0;
    }

    @Override
    public int blockZ() {
        return ready() ? net.minecraft.util.math.MathHelper.floor(self().getZ()) : 0;
    }

    @Override
    public String facing() {
        return ready() ? self().getHorizontalFacing().getName() : "north";
    }

    @Override
    public float itemCooldown(String itemId) {
        return 0.0f;
    }

    @Override
    public int score() {
        return ready() ? self().getScore() : 0;
    }
}
