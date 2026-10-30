package dev.axsesclient.mc12111.mixin;

import dev.axsesclient.mc12111.platform.Mc12111Adapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OptionInstance.class)
public abstract class OptionInstanceMixin<T> {
    @SuppressWarnings("unchecked")
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void axses$overrideGamma(CallbackInfoReturnable<T> cir) {
        double ov = Mc12111Adapter.getOverrideGamma();
        if (ov > 1.0) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.options != null && (Object) this == mc.options.gamma()) {
                cir.setReturnValue((T) Double.valueOf(ov));
            }
        }
    }
}
