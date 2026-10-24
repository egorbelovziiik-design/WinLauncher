package nexus.adapter.v1165.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Matrix4f;
import nexus.adapter.v1165.mixin.MinecraftClientMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders every 3D module (ESP, tracers, block overlays) inside the world pass.
 * <p>
 * 1.16.5 signature: {@code render(MatrixStack, float, long, boolean, Camera,
 * GameRenderer, LightmapTextureManager, Matrix4f)}. Every parameter type has to
 * be spelled out exactly, otherwise Mixin refuses the injection at runtime.
 */
@Mixin(WorldRenderer.class)
public final class WorldRendererMixin {

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lnet/minecraft/util/math/Matrix4f;)V",
            at = @At("TAIL"))
    private void nexus$render3D(MatrixStack matrices, float tickDelta, long limitTime,
                                boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                LightmapTextureManager lightmapTextureManager, Matrix4f projection,
                                CallbackInfo info) {
        try {
            MinecraftClientMixin.Hooks.HOOKS.onRender3D(matrices, tickDelta);
        } catch (Throwable throwable) {
            nexus.util.Log.error("Nexus render hook failed", throwable);
        }
    }
}
