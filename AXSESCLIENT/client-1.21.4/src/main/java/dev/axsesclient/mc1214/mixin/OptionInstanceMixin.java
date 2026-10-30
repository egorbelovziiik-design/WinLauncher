package dev.axsesclient.mc1214.mixin;

import dev.axsesclient.mc1214.platform.Mc1214Adapter;
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
        double ov = Mc1214Adapter.getOverrideGamma();
        if (ov > 1.0) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.options != null && (Object) this == mc.options.gamma()) {
                cir.setReturnValue((T) Double.valueOf(ov));
            }
        }
    }
}
