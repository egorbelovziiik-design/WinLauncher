package nexus.adapter.v12111.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import nexus.adapter.v12111.mixin.MinecraftMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Renders every 3D module (ESP, tracers, block overlays) inside the world pass. */
@Mixin(LevelRenderer.class)
public final class LevelRendererMixin {

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void nexus$render3D(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera,
                                GameRenderer gameRenderer, Object frameGraphBuilder, Object gpuBufferSlice,
                                PoseStack poseStack, CallbackInfo info) {
        MinecraftMixin.Hooks.HOOKS.onRender3D(poseStack,
                deltaTracker.getGameTimeDeltaPartialTick(true));
    }
}
