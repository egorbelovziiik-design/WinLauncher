package dev.survivortoolkit.client.feature;

import dev.survivortoolkit.client.SurvivorToolkitClient;
import dev.survivortoolkit.client.WorldPolicy;
import dev.survivortoolkit.policy.ServerPolicy;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Local camera only. Player physics remain vanilla; no coordinates or game packets are forged. */
public final class Freecam {
    private static final double MAX_DISTANCE = 12;
    private boolean active;
    private Vec3d position = Vec3d.ZERO;
    private Vec3d anchor = Vec3d.ZERO;
    private float yaw, pitch;
    private ClientPlayerEntity originalPlayer;
    private Object originalWorld;

    public boolean active() { return active; }
    public Vec3d position() { return position; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }

    public void toggle(MinecraftClient client) {
        if (active) { stop(client); return; }
        if (!ServerPolicy.freecamPermitted(client.world != null, WorldPolicy.privateLocal(client), client.player != null && client.player.isAlive())) {
            message(client, "survivor_toolkit.freecam.local_only"); return;
        }
        ClientPlayerEntity player = client.player;
        if (player.hasVehicle() || player.isUsingItem() || !player.isOnGround() || player.getVelocity().horizontalLengthSquared() > 0.0025) {
            message(client, "survivor_toolkit.freecam.stand_still"); return;
        }
        originalPlayer = player;
        originalWorld = client.world;
        anchor = player.getEntityPos();
        position = player.getEyePos();
        yaw = player.getYaw();
        pitch = player.getPitch();
        active = true;
        message(client, "survivor_toolkit.freecam.started");
    }

    public void stop(MinecraftClient client) {
        if (!active) return;
        drainVanillaKeys(client);
        active = false;
        originalPlayer = null;
        originalWorld = null;
    }

    public void look(double dx, double dy) {
        if (!active) return;
        double sensitivity = SurvivorToolkitClient.config().freecamSensitivity;
        yaw += (float) (dx * 0.15 * sensitivity);
        pitch = MathHelper.clamp(pitch + (float) (dy * 0.15 * sensitivity), -90, 90);
    }

    public void tick(MinecraftClient client) {
        if (!active) return;
        if (!WorldPolicy.privateLocal(client) || client.player == null || client.world != originalWorld || client.player != originalPlayer
                || !client.player.isAlive() || client.currentScreen != null || client.player.getEntityPos().squaredDistanceTo(anchor) > 0.16) {
            stop(client); return;
        }
        double forward = (client.options.forwardKey.isPressed() ? 1 : 0) - (client.options.backKey.isPressed() ? 1 : 0);
        double side = (client.options.rightKey.isPressed() ? 1 : 0) - (client.options.leftKey.isPressed() ? 1 : 0);
        double up = (client.options.jumpKey.isPressed() ? 1 : 0) - (client.options.sneakKey.isPressed() ? 1 : 0);
        double radians = Math.toRadians(yaw);
        Vec3d delta = new Vec3d(-Math.sin(radians) * forward + Math.cos(radians) * side, up,
                Math.cos(radians) * forward + Math.sin(radians) * side);
        if (delta.lengthSquared() > 0) {
            Vec3d next = position.add(delta.normalize().multiply(SurvivorToolkitClient.config().freecamSpeed));
            BlockPos block = BlockPos.ofFloored(next);
            if (next.squaredDistanceTo(client.player.getEyePos()) <= MAX_DISTANCE * MAX_DISTANCE
                    && client.world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
                    && clearRay(client, position, next) && clearRay(client, client.player.getEyePos(), next)) position = next;
        }
        drainVanillaKeys(client);
    }

    private boolean clearRay(MinecraftClient client, Vec3d from, Vec3d to) {
        return client.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, client.player)).getType() == HitResult.Type.MISS;
    }

    private void drainVanillaKeys(MinecraftClient client) {
        if (client.options == null) return;
        for (KeyBinding key : client.options.allKeys) {
            if (key == SurvivorToolkitClient.MENU_KEY || key == SurvivorToolkitClient.FREECAM_KEY) continue;
            while (key.wasPressed()) { /* discard, never replay on exit */ }
        }
    }

    private void message(MinecraftClient client, String key) {
        if (client.player != null) client.player.sendMessage(Text.translatable(key), true);
    }
}
