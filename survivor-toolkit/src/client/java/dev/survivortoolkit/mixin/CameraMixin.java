package dev.survivortoolkit.mixin;

import dev.survivortoolkit.client.SurvivorToolkitClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPos(Vec3d pos);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void survivorToolkit$camera(World world, Entity focused, boolean thirdPerson, boolean inverse, float tickProgress, CallbackInfo ci) {
        var camera = SurvivorToolkitClient.FREECAM;
        if (camera.active()) { setPos(camera.position()); setRotation(camera.yaw(), camera.pitch()); }
    }
}
