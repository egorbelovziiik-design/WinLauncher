package nexus.adapter.v1165.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import nexus.adapter.v1165.mixin.MinecraftClientMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Renders every 3D module (ESP, tracers, block overlays) inside the world pass. */
@Mixin(WorldRenderer.class)
public final class WorldRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void nexus$render3D(MatrixStack matrices, float tickDelta, long limitTime,
                                boolean renderBlockOutline, Camera camera, Object gameRenderer,
                                Object lightmapTextureManager, Object matrix4f, CallbackInfo info) {
        MinecraftClientMixin.Hooks.HOOKS.onRender3D(matrices, tickDelta);
    }
}
