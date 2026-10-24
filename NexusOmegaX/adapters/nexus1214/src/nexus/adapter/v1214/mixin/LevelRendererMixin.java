package nexus.adapter.v1214.mixin;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import nexus.adapter.v1214.mixin.MinecraftMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders every 3D module (ESP, tracers, block overlays) inside the world pass.
 * <p>
 * 1.21.4 signature:
 * {@code renderLevel(GraphicsResourceAllocator, DeltaTracker, boolean, Camera,
 * GameRenderer, Matrix4f, Matrix4f)} - every parameter has to be repeated here,
 * otherwise Mixin rejects the injection at runtime.
 */
@Mixin(LevelRenderer.class)
public final class LevelRendererMixin {

    @Inject(method = "renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            at = @At("TAIL"))
    private void nexus$render3D(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
                                boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                org.joml.Matrix4f frustum, org.joml.Matrix4f projection,
                                CallbackInfo info) {
        PoseStack poseStack = new PoseStack();
        poseStack.last().pose().set(frustum);
        try {
            MinecraftMixin.Hooks.HOOKS.onRender3D(poseStack,
                deltaTracker.getGameTimeDeltaPartialTick(true));
        } catch (Throwable throwable) {
            nexus.util.Log.error("Nexus render hook failed", throwable);
        }
    }
}
