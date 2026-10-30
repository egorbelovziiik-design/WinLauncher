package dev.axsesclient.mc1214.mixin;

import dev.axsesclient.common.AxsesCore;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void axses$applySmoothZoom(Camera camera, float partialTicks, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null) {
            float zoom = core.getRuntimeState().getCurrentZoomFactor();
            if (zoom > 1.01f) {
                cir.setReturnValue(cir.getReturnValueF() / zoom);
            }
        }
    }
}
