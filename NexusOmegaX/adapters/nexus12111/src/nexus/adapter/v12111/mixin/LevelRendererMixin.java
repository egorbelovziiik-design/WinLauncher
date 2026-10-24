package nexus.adapter.v12111.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import nexus.adapter.v12111.mixin.MinecraftMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders every 3D module (ESP, tracers, block overlays) inside the world pass.
 * <p>
 * 1.21.11 signature: {@code renderLevel(GraphicsResourceAllocator, DeltaTracker,
 * boolean, Camera, Matrix4f, Matrix4f, Matrix4f, GpuBufferSlice, Vector4f, boolean)}.
 */
@Mixin(LevelRenderer.class)
public final class LevelRendererMixin {

    @Inject(method = "renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
            at = @At("TAIL"))
    private void nexus$render3D(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
                                boolean renderBlockOutline, Camera camera,
                                org.joml.Matrix4f frustum, org.joml.Matrix4f projection,
                                org.joml.Matrix4f modelView, GpuBufferSlice fog,
                                org.joml.Vector4f fogColor, boolean shouldRenderSky,
                                CallbackInfo info) {
        PoseStack poseStack = new PoseStack();
        poseStack.last().pose().set(modelView);
        try {
            MinecraftMixin.Hooks.HOOKS.onRender3D(poseStack,
                deltaTracker.getGameTimeDeltaPartialTick(true));
        } catch (Throwable throwable) {
            nexus.util.Log.error("Nexus render hook failed", throwable);
        }
    }
}
