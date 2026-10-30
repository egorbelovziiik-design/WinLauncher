package dev.survivortoolkit.mixin;

import dev.survivortoolkit.client.SurvivorToolkitClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    // Block the entire vanilla gameplay input handler while detached: attacks, use, picking,
    // dropping, hotbar changes, inventory and chat. No packets are created by this mixin.
    @Inject(method = "handleInputEvents", at = @At("HEAD"), cancellable = true)
    private void survivorToolkit$blockActions(CallbackInfo ci) {
        if (SurvivorToolkitClient.FREECAM.active()) ci.cancel();
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void survivorToolkit$stopOnScreen(Screen screen, CallbackInfo ci) {
        if (screen != null && SurvivorToolkitClient.FREECAM.active())
            SurvivorToolkitClient.FREECAM.stop((MinecraftClient) (Object) this);
    }
}
