package dev.survivortoolkit.client.feature;

import dev.survivortoolkit.client.SurvivorToolkitClient;
import dev.survivortoolkit.client.WorldPolicy;
import dev.survivortoolkit.config.ToolkitConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/** Conservative: alert only for a loaded, non-invisible Warden in a narrow forward cone and line of sight. */
public final class WardenAlert {
    private long visibleUntil;
    private long lastNotice;
    private int ticks;

    public boolean visible() { return System.currentTimeMillis() < visibleUntil; }

    public void tick(MinecraftClient client) {
        ToolkitConfig config = SurvivorToolkitClient.config();
        if (!config.wardenEnabled || client.world == null || client.player == null || client.currentScreen != null
                || SurvivorToolkitClient.FREECAM.active() || !WorldPolicy.allowed(client, config.wardenAllowedServers)) {
            visibleUntil = 0; return;
        }
        if (++ticks % 5 != 0) return;
        double radius = config.wardenRadius;
        Vec3d eye = client.player.getEyePos();
        Vec3d facing = client.player.getRotationVec(1.0f);
        boolean seen = client.world.getEntitiesByClass(WardenEntity.class, client.player.getBoundingBox().expand(radius), warden -> {
            Vec3d direction = warden.getEyePos().subtract(eye);
            return warden.isAlive() && !warden.isInvisibleTo(client.player) && direction.lengthSquared() <= radius * radius
                    && direction.lengthSquared() > 0.0001 && facing.dotProduct(direction.normalize()) >= Math.cos(Math.toRadians(10))
                    && client.player.canSee(warden);
        }).stream().findAny().isPresent();
        long now = System.currentTimeMillis();
        if (seen && now - lastNotice >= Math.max(6000, config.warningSeconds * 1000L)) {
            lastNotice = now;
            visibleUntil = now + config.warningSeconds * 1000L;
            if (config.warningSoundEnabled && !"none".equals(config.warningSound) && config.warningVolume > 0) {
                var sound = "bell".equals(config.warningSound) ? SoundEvents.BLOCK_BELL_USE : SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
                client.getSoundManager().play(PositionedSoundInstance.ui(sound, 1.0f, (float) config.warningVolume));
            }
        }
    }
}
